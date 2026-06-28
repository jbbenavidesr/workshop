(ns ynab-planner.smoke-test
  (:require [clojure.test :refer [deftest is]]))

(deftest toolchain-works
  (is (= 4 (+ 2 2))))
