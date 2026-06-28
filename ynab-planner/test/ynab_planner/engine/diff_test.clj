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
    (is (= {:id "a" :name "Arriendo" :current 1500000 :planned 1600000}
           (first (filter #(= "a" (:id %)) result))))))
