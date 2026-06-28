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
