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

(deftest reset-targets-drops-specs-keeps-pillars
  (let [plan {:categories {"c1" {:pillar :ahorro :spec {:type :monthly :amount 5}}
                           "c2" {:spec {:type :monthly :amount 9}}
                           "c3" {:pillar :fun}}}]
    ;; one category: spec goes, pillar stays (YNAB has no pillars to reset to)
    (let [out (pe/reset-targets plan {"id" "c1"})]
      (is (= {:pillar :ahorro} (get-in out [:categories "c1"])))
      (is (some? (get-in out [:categories "c2" :spec])) "others untouched"))
    ;; nothing left in the entry -> drop the entry
    (is (nil? (get-in (pe/reset-targets plan {"id" "c2"}) [:categories "c2"])))
    ;; no id -> reset every target
    (let [out (pe/reset-targets plan {})]
      (is (= {:pillar :ahorro} (get-in out [:categories "c1"])))
      (is (nil? (get-in out [:categories "c2"])))
      (is (= {:pillar :fun} (get-in out [:categories "c3"]))))
    ;; unknown / blank id -> no-op (fail-safe: never a surprise wipe)
    (is (= plan (pe/reset-targets plan {"id" "nope"})))
    (is (= plan (pe/reset-targets plan {"id" ""})))))

(deftest apply-skips-marks-and-clears
  ;; The pillar selects are the roster of ids: a <select> always submits, an
  ;; unchecked checkbox never does.
  (let [plan {:categories {"c1" {:pillar :fun} "c2" {:pillar :ahorro :skip true}}}
        out  (pe/apply-skips plan {"pillar-c1" "fun" "skip-c1" "on"
                                   "pillar-c2" "ahorro"})]
    (is (true? (get-in out [:categories "c1" :skip])) "checked -> skipped")
    (is (nil? (get-in out [:categories "c2" :skip])) "unchecked -> no longer skipped")
    (is (= :ahorro (get-in out [:categories "c2" :pillar])) "pillar survives"))
  ;; a skip-only entry is still an entry; an emptied one goes away
  (let [out (pe/apply-skips {:categories {"c1" {:skip true}}} {"pillar-c1" ""})]
    (is (nil? (get-in out [:categories "c1"])))))
