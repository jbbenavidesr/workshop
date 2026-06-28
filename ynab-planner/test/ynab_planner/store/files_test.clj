(ns ynab-planner.store.files-test
  (:require [clojure.test :refer [deftest is]]
            [ynab-planner.store.files :as store])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- tmp-dir [] (str (Files/createTempDirectory "yp" (make-array FileAttribute 0))))

(deftest plan-round-trips
  (let [dir (tmp-dir)]
    (is (= store/default-plan (store/read-plan dir)))      ; missing file -> default
    (let [p (assoc store/default-plan :income 24261765)]
      (store/write-plan dir p)
      (is (= p (store/read-plan dir))))))

(deftest cache-round-trips
  (let [dir (tmp-dir)
        c {:synced-at "t" :groups [] :categories []}]
    (is (nil? (store/read-cache dir)))
    (store/write-cache dir c)
    (is (= c (store/read-cache dir)))))
