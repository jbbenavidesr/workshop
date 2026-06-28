(ns ynab-planner.ynab.client-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [ynab-planner.ynab.client :as client]))

(deftest parses-fixture-into-cache
  (let [body  (slurp (io/resource "ynab_planner/fixtures/categories.json"))
        cache (client/parse-budget body "2026-06-27T12:00:00Z")
        by-id (into {} (map (juxt :id identity) (:categories cache)))]
    (is (= "2026-06-27T12:00:00Z" (:synced-at cache)))
    (is (= [{:id "g1" :name "🏡 Hogar"}] (:groups cache)))
    (is (= 2 (count (:categories cache))))                 ; deleted c3 dropped
    (is (= 0 (get-in by-id ["c1" :balance])))              ; balance 0 -> 0 pesos
    (is (= {:type :monthly :amount 1500000} (get-in by-id ["c1" :ynab-spec])))
    (is (= 3800000 (get-in by-id ["c2" :balance])))        ; 3800000000 mu -> 3800000 pesos
    (is (= {:type :balance-by-date :target 7800000 :date "2026-09-01"}
           (get-in by-id ["c2" :ynab-spec])))))
