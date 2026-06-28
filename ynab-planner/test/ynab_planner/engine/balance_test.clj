(ns ynab-planner.engine.balance-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.engine.balance :as b]))

(deftest balance-status
  (let [cats [{:monthly 4000} {:monthly 6000}]]
    (is (= {:total 10000 :income 10000 :difference 0 :status :balanced}
           (b/balance-check cats 10000)))
    (is (= :over  (:status (b/balance-check cats 9000))))
    (is (= -1000  (:difference (b/balance-check cats 9000))))
    (is (= :under (:status (b/balance-check cats 12000))))))
