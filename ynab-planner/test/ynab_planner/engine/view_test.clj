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
