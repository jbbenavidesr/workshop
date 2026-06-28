(ns ynab-planner.ynab.client
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]
            [ynab-planner.ynab.mapping :as mapping]))

(defn- mu->pesos [mu] (when (some? mu) (long (quot mu 1000))))

(defn parse-budget
  "Parse YNAB /categories response into the internal cache shape (pure)."
  [body synced-at]
  (let [data   (if (string? body) (json/parse-string body true) body)
        groups (get-in data [:data :category_groups])
        cats   (for [g groups
                     c (:categories g)
                     :when (and (not (:deleted c)) (not (:hidden c)))]
                 {:id (:id c)
                  :name (:name c)
                  :group-id (:id g)
                  :group-name (:name g)
                  :balance (or (mu->pesos (:balance c)) 0)
                  :ynab-spec (mapping/goal->spec
                              {:goal-type (:goal_type c)
                               :goal-target (mu->pesos (:goal_target c))
                               :goal-target-month (:goal_target_month c)
                               :goal-cadence (:goal_cadence c)
                               :goal-under-funded (mu->pesos (:goal_under_funded c))})})]
    {:synced-at synced-at
     :groups (mapv (fn [g] {:id (:id g) :name (:name g)}) groups)
     :categories (vec cats)}))

(defn fetch-budget
  "Network: fetch categories for a budget. Returns the raw JSON body string."
  [token budget-id]
  (let [url (str "https://api.ynab.com/v1/budgets/" budget-id "/categories")
        {:keys [status body error]}
        @(http/get url {:headers {"Authorization" (str "Bearer " token)}})]
    (when (or error (not= 200 status))
      (throw (ex-info "YNAB fetch failed" {:status status :error error})))
    body))
