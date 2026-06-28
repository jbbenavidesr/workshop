(ns ynab-planner.sync-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [ynab-planner.sync :as sync]
            [ynab-planner.store.files :as store])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- tmp-dir [] (str (Files/createTempDirectory "yp" (make-array FileAttribute 0))))

(deftest sync-writes-cache
  (let [dir  (tmp-dir)
        body (slurp (io/resource "ynab_planner/fixtures/categories.json"))
        cache (sync/sync-budget! {:fetch-fn (fn [_token _id] body)
                                  :now "2026-06-27T12:00:00Z"
                                  :dir dir :token "tok" :budget-id "b1"})]
    (is (= 2 (count (:categories cache))))
    (is (= cache (store/read-cache dir)))))
