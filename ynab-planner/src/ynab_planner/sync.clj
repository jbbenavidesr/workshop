(ns ynab-planner.sync
  (:require [ynab-planner.ynab.client :as client]
            [ynab-planner.store.files :as store]))

(defn sync-budget!
  "Fetch the budget and its month history, parse to cache, persist. Returns the
   cache. History is a bonus: if the months endpoint fails the snapshot is still
   written, just without it."
  [{:keys [fetch-fn fetch-months-fn now dir token budget-id]
    :or {fetch-fn client/fetch-budget fetch-months-fn client/fetch-months}}]
  (let [body    (fetch-fn token budget-id)
        history (try (client/parse-months (fetch-months-fn token budget-id))
                     (catch Exception e
                       (println "sync: history unavailable —" (ex-message e))
                       {}))
        cache   (assoc (client/parse-budget body now) :history history)]
    (store/write-cache dir cache)
    cache))
