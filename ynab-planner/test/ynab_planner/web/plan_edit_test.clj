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

(deftest apply-amounts-honors-zero-current
  ;; Falsy-zero bug: when current monthly is 0, (and n cur ...) short-circuits because 0 is falsy.
  ;; The fix uses (some? n) + (contains? before-by-id id) instead.
  (let [plan   {:categories {}}
        before {"c3" 0 "c4" 50}
        out    (pe/apply-amounts plan before {"cat-c3" "50"   ; cur=0, new=50  -> MUST apply
                                              "cat-c4" "50"   ; cur=50, new=50 -> unchanged, no override
                                              "cat-c5" "99"})] ; id absent from before -> no override
    (is (= {:type :monthly :amount 50} (get-in out [:categories "c3" :spec])) ; cur was 0 -> override applied
        "override must be applied when current is 0 and new value differs")
    (is (nil? (get-in out [:categories "c4"]))
        "no override when new equals current (50=50)")
    (is (nil? (get-in out [:categories "c5"]))
        "no override when id is absent from before-by-id")))

(deftest apply-pillars-sets-and-clears
  (let [plan {:categories {"c1" {:pillar :fun} "c2" {}}}
        out (pe/apply-pillars plan {"pillar-c1" "" "pillar-c2" "ahorro"})]
    (is (nil? (get-in out [:categories "c1" :pillar])))            ; blank -> cleared
    (is (= :ahorro (get-in out [:categories "c2" :pillar])))))     ; set
