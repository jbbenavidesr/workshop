(ns ynab-planner.engine.target-test
  (:require [clojure.test :refer [deftest is testing]]
            [ynab-planner.engine.target :as t])
  (:import [java.time YearMonth]))

(def jun (YearMonth/of 2026 6))

(deftest fixed-types
  (testing ":none is always zero"
    (is (= 0 (t/derive-monthly {:type :none} 0 jun))))
  (testing ":monthly returns the fixed amount"
    (is (= 1600000 (t/derive-monthly {:type :monthly :amount 1600000} 0 jun))))
  (testing ":balance-rolling returns the chosen monthly amount"
    (is (= 200000 (t/derive-monthly {:type :balance-rolling :monthly 200000} 9999 jun)))))

(deftest needed-cadence
  (testing "yearly amount divides by 12"
    (is (= 41667 (t/derive-monthly {:type :needed-cadence :amount 500000 :cadence :yearly} 0 jun))))
  (testing "weekly amount scales by 52/12"
    (is (= 433333 (t/derive-monthly {:type :needed-cadence :amount 100000 :cadence :weekly} 0 jun))))
  (testing "unknown cadence falls back to the raw amount"
    (is (= 100000 (t/derive-monthly {:type :needed-cadence :amount 100000 :cadence :daily} 0 jun)))))

(deftest balance-by-date
  (testing "spreads remaining over inclusive months left"
    ;; jun..sep inclusive = 4 months; (7800000-0)/4 = 1950000
    (is (= 1950000 (t/derive-monthly {:type :balance-by-date :target 7800000 :date "2026-09"} 0 jun))))
  (testing "subtracts current balance"
    ;; (7800000-3800000)/4 = 1000000
    (is (= 1000000 (t/derive-monthly {:type :balance-by-date :target 7800000 :date "2026-09"} 3800000 jun))))
  (testing "never negative when already funded past target"
    (is (= 0 (t/derive-monthly {:type :balance-by-date :target 1000000 :date "2026-09"} 5000000 jun))))
  (testing "date in the past collapses to one month"
    (is (= 1000000 (t/derive-monthly {:type :balance-by-date :target 1000000 :date "2026-01"} 0 jun)))))

(deftest needed-cadence-every-n-periods
  (testing ":every divides the monthly share — 300.000 every 2 months is 150.000"
    (is (= 150000 (t/derive-monthly {:type :needed-cadence :amount 300000
                                     :cadence :monthly :every 2} 0 jun))))
  (testing "yearly every 2 years spreads over 24 months"
    (is (= 20833 (t/derive-monthly {:type :needed-cadence :amount 500000
                                    :cadence :yearly :every 2} 0 jun))))
  (testing "weekly every 2 weeks halves the weekly rate"
    (is (= 216667 (t/derive-monthly {:type :needed-cadence :amount 100000
                                     :cadence :weekly :every 2} 0 jun))))
  (testing "a missing :every behaves exactly as before"
    (is (= 41667 (t/derive-monthly {:type :needed-cadence :amount 500000
                                    :cadence :yearly} 0 jun)))))
