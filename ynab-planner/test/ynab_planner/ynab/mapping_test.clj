(ns ynab-planner.ynab.mapping-test
  (:require [clojure.test :refer [deftest is testing]]
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

(deftest cadence-frequency-is-how-many-periods-between-occurrences
  ;; YNAB says "300.000 cada 2 meses" with cadence 1 + frequency 2. Reading only
  ;; the cadence made 🚰 Agua cost 300.000 a month instead of 150.000 — the goal
  ;; came out at double for every category that is not funded every period.
  (is (= {:type :needed-cadence :amount 300000 :cadence :monthly :every 2}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 1 :goal-cadence-frequency 2
                        :goal-target 300000})))
  (is (= {:type :needed-cadence :amount 100000 :cadence :weekly :every 2}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 2 :goal-cadence-frequency 2
                        :goal-target 100000})))
  (is (= {:type :needed-cadence :amount 500000 :cadence :yearly :every 2}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 13 :goal-cadence-frequency 2
                        :goal-target 500000})))
  (testing "frequency 1 and a missing frequency stay on the simple shape"
    (is (= {:type :monthly :amount 950000}
           (m/goal->spec {:goal-type "NEED" :goal-cadence 1 :goal-cadence-frequency 1
                          :goal-target 950000})))
    (is (= {:type :needed-cadence :amount 500000 :cadence :yearly}
           (m/goal->spec {:goal-type "NEED" :goal-cadence 13 :goal-target 500000})))))

(deftest cadences-three-to-twelve-carry-the-period-themselves
  ;; For these YNAB ignores goal_cadence_frequency: the cadence IS the month
  ;; count, offset by one (3 = every 2 months … 12 = every 11 months).
  ;; They used to fall through to :monthly, which read them at full price.
  (is (= {:type :needed-cadence :amount 300000 :cadence :monthly :every 2}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 3 :goal-target 300000})))
  (is (= {:type :needed-cadence :amount 600000 :cadence :monthly :every 11}
         (m/goal->spec {:goal-type "NEED" :goal-cadence 12 :goal-target 600000})))
  (testing "14 is every two years"
    (is (= {:type :needed-cadence :amount 800000 :cadence :yearly :every 2}
           (m/goal->spec {:goal-type "NEED" :goal-cadence 14 :goal-target 800000})))))
