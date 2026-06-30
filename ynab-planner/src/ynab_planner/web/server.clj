(ns ynab-planner.web.server
  (:require [org.httpkit.server :as hk]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ynab-planner.config :as config]
            [ynab-planner.store.files :as store]
            [ynab-planner.sync :as sync]
            [ynab-planner.engine.view :as view]
            [ynab-planner.web.plan-edit :as edit]
            [ynab-planner.web.views :as views])
  (:import [java.time Instant]
           [java.net URLDecoder]))

(defn- parse-form [body]
  (if (str/blank? body)
    {}
    (into {} (for [pair (str/split body #"&")
                   :let [[k v] (str/split pair #"=" 2)]]
               [(URLDecoder/decode k "UTF-8")
                (URLDecoder/decode (or v "") "UTF-8")]))))

(defn- current-view [dir]
  (let [cache (or (store/read-cache dir) {:synced-at nil :groups [] :categories []})
        plan  (store/read-plan dir)]
    (view/build-view cache plan (config/plan-month))))

(defn- before-monthly [dir]
  (into {} (map (juxt :id :monthly) (:categories (current-view dir)))))

(defn- wants-json? [req]
  (some-> (get-in req [:headers "accept"]) (str/includes? "application/json")))

(def ^:private content-types
  {"css" "text/css" "js" "text/javascript" "json" "application/json"})

(defn- static [uri]
  (when-let [res (io/resource (str "public" uri))]
    {:status 200
     :headers {"Content-Type" (get content-types (last (str/split uri #"\.")) "application/octet-stream")}
     :body (slurp res)}))

(defn- html [s] {:status 200 :headers {"Content-Type" "text/html; charset=utf-8"} :body s})
(defn- redirect [loc] {:status 303 :headers {"Location" loc}})

(defn app [req]
  (let [dir (config/data-dir)]
    (case [(:request-method req) (:uri req)]
      [:get "/"]         (html (views/render-plan-page (current-view dir)))
      [:get "/apply"]    (html (views/render-apply-page (current-view dir)))
      [:get "/settings"] (html (views/render-settings-page (current-view dir)))
      [:get "/classify"] (html (views/render-classify-page (current-view dir)))

      [:post "/plan/update"]
      (let [params (parse-form (slurp (:body req)))
            before (before-monthly dir)
            plan'  (edit/apply-amounts (store/read-plan dir) before params)]
        (store/write-plan dir plan')
        (let [v (current-view dir)]
          (if (wants-json? req)
            {:status 200 :headers {"Content-Type" "application/json"}
             :body (json/generate-string (views/update-response v))}
            (redirect "/"))))

      [:post "/income"]
      (let [params (parse-form (slurp (:body req)))]
        (store/write-plan dir (edit/apply-income (store/read-plan dir) params))
        (redirect "/settings"))

      [:post "/classify"]
      (let [params (parse-form (slurp (:body req)))]
        (store/write-plan dir (edit/apply-pillars (store/read-plan dir) params))
        (redirect "/classify"))

      [:post "/sync"]
      (do (sync/sync-budget! {:now (str (Instant/now))
                              :dir dir :token (config/token)
                              :budget-id (:budget-id (store/read-plan dir))})
          (redirect "/settings"))

      (or (static (:uri req))
          {:status 404 :body "not found"}))))

(defonce server (atom nil))

(defn start! [port]
  (reset! server (hk/run-server #'app {:port port}))
  (println (str "YNAB Planner on http://localhost:" port)))

(defn stop! []
  (when-let [s @server] (s) (reset! server nil)))
