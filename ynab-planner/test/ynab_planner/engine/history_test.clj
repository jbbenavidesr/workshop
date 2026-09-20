(ns ynab-planner.engine.history-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.engine.history :as hist])
  (:import [java.time YearMonth]))

(def sep (YearMonth/of 2026 9))

(defn- row [m spent] {:month m :spent spent :budgeted 0})

;; 2025-11 .. 2026-10: ten complete months, a partial current one, a future one.
(def series
  [(row "2025-11" 100) (row "2025-12" 900) (row "2026-01" 200) (row "2026-02" 200)
   (row "2026-03" 300) (row "2026-04" 400) (row "2026-05" 500) (row "2026-06" 600)
   (row "2026-07" 700) (row "2026-08" 800)
   (row "2026-09" 12)  ; current month, still filling up
   (row "2026-10" 0)]) ; future month YNAB always includes

(deftest complete-drops-current-and-future-months
  (is (= ["2025-11" "2025-12" "2026-01" "2026-02" "2026-03"
          "2026-04" "2026-05" "2026-06" "2026-07" "2026-08"]
         (mapv :month (hist/complete series sep)))))

(deftest stats-summarize-complete-months-only
  (let [s (hist/stats series sep)]
    (is (= 10 (:n s)) "a partial month must not count as history")
    ;; avg of the last six complete months: 300..800 -> 550
    (is (= 550 (:avg6 s)))
    ;; median of all ten: sorted 100 200 200 300 400 500 600 700 800 900 -> (400+500)/2
    (is (= 450 (:median s)))
    (is (= 900 (:max s)))
    (is (= [100 900 200 200 300 400 500 600 700 800] (:series s)) "sparkline data")))

(deftest stats-handle-short-and-empty-history
  (let [s (hist/stats [(row "2026-07" 10) (row "2026-08" 20)] sep)]
    (is (= 2 (:n s)))
    (is (= 15 (:avg6 s)) "fewer than six months averages what exists"))
  (let [s (hist/stats [] sep)]
    (is (= {:n 0 :avg6 0 :median 0 :max 0 :series []} s)
        "no history must not blow up or fake a number")))

(deftest slack-is-planned-minus-typical-spend
  (is (= 150 (hist/slack 700 {:n 10 :avg6 550 :max 900})))   ; padding available to harvest
  (is (= -100 (hist/slack 450 {:n 10 :avg6 550 :max 900})))  ; chronically underfunded
  (is (= 0 (hist/slack 500 {:n 0 :avg6 0 :max 0})) "no history -> no claim about slack")
  ;; A savings goal that has never spent is not "100% padding" — with no outflow
  ;; on record there is no evidence either way, so claim nothing.
  (is (= 0 (hist/slack 10000000 {:n 10 :avg6 0 :max 0}))
      "never spent -> unknown, not harvestable"))
