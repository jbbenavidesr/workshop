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
                     ;; :internal is YNAB's own "Inflow: Ready to Assign" — income,
                     ;; not a category you plan a target for.
                     :when (and (not (:deleted c)) (not (:hidden c)) (not (:internal c)))]
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

(defn parse-months
  "Parse /months/{month} responses (ascending) into {category-id [row ...]} (pure).
   YNAB reports outflow as negative activity, so :spent is the sign-flipped value —
   the same boundary where milliunits become pesos."
  [bodies]
  (reduce
   (fn [acc body]
     (let [data  (if (string? body) (json/parse-string body true) body)
           month (subs (get-in data [:data :month :month]) 0 7)]
       (reduce (fn [m c]
                 (if (or (:deleted c) (:hidden c) (:internal c))
                   m
                   (update m (:id c) (fnil conj [])
                           {:month month
                            :spent (- (or (mu->pesos (:activity c)) 0))
                            :budgeted (or (mu->pesos (:budgeted c)) 0)})))
               acc
               (get-in data [:data :month :categories]))))
   {}
   bodies))

(defn fetch-budget
  "Network: fetch categories for a budget. Returns the raw JSON body string."
  [token budget-id]
  (let [url (str "https://api.ynab.com/v1/budgets/" budget-id "/categories")
        {:keys [status body error]}
        @(http/get url {:headers {"Authorization" (str "Bearer " token)}})]
    (when (or error (not= 200 status))
      (throw (ex-info "YNAB fetch failed" {:status status :error error})))
    body))

(defn- get-json [token url]
  (let [{:keys [status body error]}
        @(http/get url {:headers {"Authorization" (str "Bearer " token)}})]
    (when (or error (not= 200 status))
      (throw (ex-info "YNAB fetch failed" {:url url :status status :error error})))
    body))

(defn fetch-months
  "Network: every month YNAB holds for this budget, oldest first. One call lists
   the months, then one per month for its categories (the list call omits them).
   Issued in parallel — a young budget is a dozen requests, well under the limit."
  [token budget-id]
  (let [base   (str "https://api.ynab.com/v1/budgets/" budget-id "/months")
        months (->> (json/parse-string (get-json token base) true)
                    :data :months (map :month) sort)]
    (mapv deref (mapv #(future (get-json token (str base "/" %))) months))))
