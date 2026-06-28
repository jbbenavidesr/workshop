(ns ynab-planner.store.files
  (:require [clojure.edn :as edn]
            [clojure.pprint])
  (:import [java.io File]))

(def default-plan
  {:income 0
   :budget-id nil
   :pillar-targets {:donaciones 10 :ahorro 20 :fun 30 :necesario 40}
   :categories {}})

(defn read-edn [path default]
  (let [f (File. ^String path)]
    (if (.exists f) (edn/read-string (slurp f)) default)))

(defn write-edn [path data]
  (spit path (with-out-str (clojure.pprint/pprint data)))
  data)

(defn- p [dir name] (str (File. (File. ^String dir) ^String name)))

(defn read-plan  [dir] (read-edn (p dir "plan.edn") default-plan))
(defn write-plan [dir plan] (write-edn (p dir "plan.edn") plan))
(defn read-cache [dir] (read-edn (p dir "cache.edn") nil))
(defn write-cache [dir cache] (write-edn (p dir "cache.edn") cache))
