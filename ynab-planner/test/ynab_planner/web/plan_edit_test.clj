(ns ynab-planner.web.plan-edit-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.web.plan-edit :as pe]))

(deftest apply-income-sets-when-parseable
  (is (= 5000 (:income (pe/apply-income {:income 1} {"income" "5000"}))))
  (is (= 1 (:income (pe/apply-income {:income 1} {"income" "abc"}))))   ; keeps old
  (is (= 1 (:income (pe/apply-income {:income 1} {})))))               ; absent -> unchanged

(deftest apply-amounts-overrides-only-changed
  (let [plan {:categories {}}
        before {"c1" 100 "c2" 200}
        out (pe/apply-amounts plan before {"cat-c1" "150" "cat-c2" "200"})]
    (is (= {:type :monthly :amount 150} (get-in out [:categories "c1" :spec])))  ; changed
    (is (nil? (get-in out [:categories "c2"])))))                                 ; unchanged -> no override

(deftest apply-pillars-sets-and-clears
  (let [plan {:categories {"c1" {:pillar :fun} "c2" {}}}
        out (pe/apply-pillars plan {"pillar-c1" "" "pillar-c2" "ahorro"})]
    (is (nil? (get-in out [:categories "c1" :pillar])))            ; blank -> cleared
    (is (= :ahorro (get-in out [:categories "c2" :pillar])))))     ; set
