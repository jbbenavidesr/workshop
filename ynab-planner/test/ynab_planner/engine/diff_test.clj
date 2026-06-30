(ns ynab-planner.engine.diff-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.engine.diff :as diff]))

(deftest only-changed-categories
  (let [cats [{:id "a" :name "Arriendo" :ynab-current 1500000 :planned 1600000}
              {:id "b" :name "Gas"      :ynab-current 5000    :planned 5000}
              {:id "c" :name "Mercado"  :ynab-current 1000000 :planned 900000}]
        result (diff/plan-diff cats)]
    (is (= 2 (count result)))
    (is (= #{"a" "c"} (set (map :id result))))
    (is (= {:id "a" :name "Arriendo" :group-name nil :current 1500000 :planned 1600000}
           (first (filter #(= "a" (:id %)) result))))))

(deftest plan-diff-carries-group-name
  (let [cats [{:id "c1" :name "A" :group-name "🏡 Hogar" :ynab-current 100 :planned 200}]
        out  (diff/plan-diff cats)]
    (is (= "🏡 Hogar" (:group-name (first out))))))

(deftest grouped-diff-preserves-first-appearance-order
  (let [diff [{:id "a" :name "A" :group-name "🏡 Hogar"  :current 1 :planned 2}
              {:id "b" :name "B" :group-name "🎉 Diversión" :current 1 :planned 2}
              {:id "c" :name "C" :group-name "🏡 Hogar"  :current 1 :planned 2}]
        out  (diff/grouped-diff diff)]
    (is (= ["🏡 Hogar" "🎉 Diversión"] (mapv :group-name out)))
    (is (= ["a" "c"] (mapv :id (:items (first out)))))))
