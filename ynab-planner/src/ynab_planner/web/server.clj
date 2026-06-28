(ns ynab-planner.web.server
  (:require [org.httpkit.server :as hk]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ynab-planner.config :as config]
            [ynab-planner.store.files :as store]
            [ynab-planner.sync :as sync]
            [ynab-planner.engine.view :as view]
            [ynab-planner.web.views :as views])
  (:import [java.time Instant]
           [java.net URLDecoder]))

(defn- parse-form
  "Parse an application/x-www-form-urlencoded body into a string->string map."
  [body]
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

(defn- apply-form
  "Update plan.edn from submitted form params: income + any changed cat-<id>
   monthly values (a hand-typed monthly becomes a {:type :monthly} override).
   Returns the freshly recomputed view."
  [dir params]
  (let [before (current-view dir)
        by-id  (into {} (map (juxt :id identity) (:categories before)))
        plan'  (reduce
                (fn [p [k v]]
                  (cond
                    (= k "income")
                    (assoc p :income (or (parse-long v) (:income p)))

                    (str/starts-with? k "cat-")
                    (let [id  (subs k 4)
                          n   (parse-long v)
                          cur (get-in by-id [id :monthly])]
                      (if (and n cur (not= n cur))
                        (assoc-in p [:categories id :spec] {:type :monthly :amount n})
                        p))

                    (str/starts-with? k "pillar-")
                    (let [id (subs k 7)]
                      (if (str/blank? v)
                        (cond-> p
                          (contains? (:categories p) id)
                          (update-in [:categories id] dissoc :pillar))
                        (assoc-in p [:categories id :pillar] (keyword v))))

                    :else p))
                (store/read-plan dir)
                params)]
    (store/write-plan dir plan')
    (current-view dir)))

(defn- wants-json? [req]
  (some-> (get-in req [:headers "accept"]) (str/includes? "application/json")))

(def ^:private content-types
  {"css" "text/css" "js" "text/javascript" "json" "application/json"})

(defn- static [uri]
  (when-let [res (io/resource (str "public" uri))]
    {:status 200
     :headers {"Content-Type" (get content-types (last (str/split uri #"\.")) "application/octet-stream")}
     :body (slurp res)}))

(defn app [req]
  (let [dir (config/data-dir)]
    (case [(:request-method req) (:uri req)]
      [:get "/"]
      {:status 200 :headers {"Content-Type" "text/html; charset=utf-8"}
       :body (views/render-page (current-view dir))}

      [:post "/sync"]
      (do (sync/sync-budget! {:now (str (Instant/now))
                              :dir dir :token (config/token)
                              :budget-id (:budget-id (store/read-plan dir))})
          {:status 303 :headers {"Location" "/"}})

      [:post "/plan/update"]
      (let [params (parse-form (slurp (:body req)))
            v (apply-form dir params)]
        (if (wants-json? req)
          {:status 200 :headers {"Content-Type" "application/json"}
           :body (json/generate-string (views/update-response v))}
          {:status 303 :headers {"Location" "/"}}))   ; no-JS fallback: reload

      (or (static (:uri req))
          {:status 404 :body "not found"}))))

(defonce server (atom nil))

(defn start! [port]
  (reset! server (hk/run-server #'app {:port port}))
  (println (str "YNAB Planner on http://localhost:" port)))

(defn stop! []
  (when-let [s @server] (s) (reset! server nil)))
