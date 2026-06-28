(ns ynab-planner.engine.distribution-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.engine.distribution :as d]))

(deftest distributes-by-pillar
  (let [cats [{:pillar :donaciones :monthly 1000}
              {:pillar :ahorro     :monthly 2000}
              {:pillar :ahorro     :monthly 1000}
              {:pillar nil         :monthly 500}]
        result (d/distribute cats 10000 {:donaciones 10 :ahorro 20})
        by (into {} (map (juxt :pillar identity) (:pillars result)))]
    (is (= 1000 (get-in by [:donaciones :amount])))
    (is (= 3000 (get-in by [:ahorro :amount])))
    (is (= 10.0 (get-in by [:donaciones :actual-pct])))
    (is (= 30.0 (get-in by [:ahorro :actual-pct])))
    (is (= 20 (get-in by [:ahorro :ideal-pct])))
    (is (= 500 (:untagged result)))))

(deftest zero-income-is-safe
  (is (= 0.0 (-> (d/distribute [{:pillar :fun :monthly 100}] 0 {:fun 30})
                 :pillars first :actual-pct))))
