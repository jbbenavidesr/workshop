(ns ynab-planner.engine.view-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.engine.view :as v])
  (:import [java.time YearMonth]))

(def jun (YearMonth/of 2026 6))

(def cache
  {:synced-at "2026-06-27T12:00:00Z"
   :groups [{:id "g1" :name "🏡 Hogar"}]
   :categories [{:id "c1" :name "🏠 Arriendo" :group-id "g1" :group-name "🏡 Hogar"
                 :balance 0 :ynab-spec {:type :monthly :amount 1500000}}
                {:id "c2" :name "💰 FPV" :group-id "g1" :group-name "🏡 Hogar"
                 :balance 0 :ynab-spec {:type :monthly :amount 2000000}}]})

(def plan
  {:income 3600000
   :pillar-targets {:necesario 40 :ahorro 20}
   :categories {"c1" {:pillar :necesario :spec {:type :monthly :amount 1600000}}
                "c2" {:pillar :ahorro}}}) ; no spec override -> uses ynab-spec

(deftest resolves-overrides-and-defaults
  (let [c1 (v/resolve-category (first (:categories cache)) plan jun)]
    (is (= 1600000 (:planned c1)))       ; plan override
    (is (= 1500000 (:ynab-current c1)))  ; from cache ynab-spec
    (is (= :necesario (:pillar c1))))
  (let [c2 (v/resolve-category (second (:categories cache)) plan jun)]
    (is (= 2000000 (:planned c2)))       ; falls back to ynab-spec
    (is (= 2000000 (:ynab-current c2)))))

(deftest build-view-composes-everything
  (let [view (v/build-view cache plan jun)]
    (is (= 3600000 (:income view)))
    (is (= :balanced (get-in view [:balance :status])))         ; 1.6M + 2.0M = 3.6M
    (is (= 2 (count (:categories view))))
    (is (= 1 (count (:diff view))))                              ; only c1 changed
    (is (= "2026-06-27T12:00:00Z" (:synced-at view)))))

(deftest pillar-sections-groups-and-sorts
  (let [cats [{:id "a" :pillar :necesario :monthly 100 :group-name "G"}
              {:id "b" :pillar :necesario :monthly 900 :group-name "G"}
              {:id "c" :pillar :fun       :monthly 200 :group-name "G"}
              {:id "d" :pillar nil        :monthly 50  :group-name "G"}]
        dist {:pillars [{:pillar :necesario :amount 1000 :actual-pct 50.0 :ideal-pct 40}
                        {:pillar :fun       :amount 200  :actual-pct 10.0 :ideal-pct 30}]
              :untagged 50}
        sec  (v/pillar-sections cats dist)]
    ;; pillars stay in distribution order
    (is (= [:necesario :fun] (mapv :pillar (:pillars sec))))
    ;; categories sorted by :monthly descending within a pillar
    (is (= ["b" "a"] (mapv :id (:categories (first (:pillars sec))))))
    ;; untagged carried separately
    (is (= ["d"] (mapv :id (:untagged-categories sec))))
    (is (= 50 (:untagged sec)))))

(deftest build-view-emits-sections-and-targets
  (let [view (v/build-view cache plan jun)]
    (is (contains? view :pillar-sections))
    (is (= {:necesario 40 :ahorro 20} (:pillar-targets view)))
    (is (= 2 (count (:pillars (:pillar-sections view)))))))
