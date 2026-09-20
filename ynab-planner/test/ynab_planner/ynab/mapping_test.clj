(ns ynab-planner.ynab.mapping-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.ynab.mapping :as m]))

(deftest goal-mapping
  (is (= {:type :none} (m/goal->spec {:goal-type nil})))
  (is (= {:type :monthly :amount 1600000}
         (m/goal->spec {:goal-type "MF" :goal-target 1600000})))
  (is (= {:type :balance-by-date :target 7800000 :date "2026-09-01"}
         (m/goal->spec {:goal-type "TBD" :goal-target 7800000 :goal-target-month "2026-09-01"})))
  (is (= {:type :balance-rolling :monthly 200000}
         (m/goal->spec {:goal-type "TB" :goal-under-funded 200000})))
  (is (= {:type :monthly :amount 950000}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 1 :goal-target 950000})))
  (is (= {:type :needed-cadence :amount 500000 :cadence :yearly}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 13 :goal-target 500000}))))

(deftest need-cadence-zero-is-a-one-off-target-by-a-date
  ;; YNAB's "have X by <month>" goal. Falling through to :monthly made a
  ;; 10.000.000 January fund read as 10.000.000 every single month.
  (is (= {:type :balance-by-date :target 10000000 :date "2027-01-01"}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 0 :goal-target 10000000
                        :goal-target-month "2027-01-01"})))
  ;; no date to spread it over -> the old reading is the only one left
  (is (= {:type :monthly :amount 500000}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 0 :goal-target 500000
                        :goal-target-month nil}))))
