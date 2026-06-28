(ns ynab-planner.sync
  (:require [ynab-planner.ynab.client :as client]
            [ynab-planner.store.files :as store]))

(defn sync-budget!
  "Fetch the budget (via :fetch-fn), parse to cache, persist. Returns the cache."
  [{:keys [fetch-fn now dir token budget-id]
    :or {fetch-fn client/fetch-budget}}]
  (let [body  (fetch-fn token budget-id)
        cache (client/parse-budget body now)]
    (store/write-cache dir cache)
    cache))
