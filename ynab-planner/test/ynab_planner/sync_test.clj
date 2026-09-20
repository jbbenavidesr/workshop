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

(deftest sync-writes-history-alongside-categories
  (let [dir   (tmp-dir)
        body  (slurp (io/resource "ynab_planner/fixtures/categories.json"))
        cache (sync/sync-budget!
               {:fetch-fn         (fn [_token _id] body)
                :fetch-months-fn  (fn [_token _id]
                                    [(slurp (io/resource "ynab_planner/fixtures/month-2026-07.json"))
                                     (slurp (io/resource "ynab_planner/fixtures/month-2026-08.json"))])
                :now "2026-09-01T12:00:00Z" :dir dir :token "tok" :budget-id "b1"})]
    (is (= 2 (count (get-in cache [:history "c1"]))))
    (is (= cache (store/read-cache dir)) "history round-trips through edn"))
  ;; history is additive: a sync that cannot reach the months endpoint still works
  (let [dir   (tmp-dir)
        body  (slurp (io/resource "ynab_planner/fixtures/categories.json"))
        cache (sync/sync-budget! {:fetch-fn (fn [_token _id] body)
                                  :fetch-months-fn (fn [_ _] (throw (ex-info "boom" {})))
                                  :now "2026-09-01T12:00:00Z"
                                  :dir dir :token "tok" :budget-id "b1"})]
    (is (= 2 (count (:categories cache))))
    (is (= {} (:history cache)))))
