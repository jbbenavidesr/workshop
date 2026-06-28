(ns ynab-planner.config
  (:import [java.time YearMonth]))

(defn data-dir [] (or (System/getenv "YP_DATA_DIR") "."))
(defn token [] (System/getenv "YNAB_TOKEN"))

(defn plan-month []
  (if-let [s (System/getenv "YP_PLAN_MONTH")]
    (YearMonth/parse s)
    (YearMonth/now)))
