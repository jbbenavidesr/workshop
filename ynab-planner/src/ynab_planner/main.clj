(ns ynab-planner.main
  (:require [ynab-planner.web.server :as server]))

(defn -main [& _]
  (server/start! 3000)
  @(promise)) ; block forever
