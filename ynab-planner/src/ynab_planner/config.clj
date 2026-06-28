(ns ynab-planner.config
  (:import [java.time YearMonth]
           [java.io File]))

(defn parse-dotenv
  "Pure function. Takes .env file contents string, returns {string string} map.
  Ignores blank lines and comment lines (first non-whitespace char is #).
  Splits on the first = only, trims whitespace around key and value.
  Strips a single pair of matching surrounding quotes (double or single).
  Skips lines with no = or with an empty key."
  [contents]
  (reduce
   (fn [acc line]
     (let [trimmed (clojure.string/trim line)]
       (cond
         ;; blank line
         (empty? trimmed) acc
         ;; comment line
         (= \# (first trimmed)) acc
         ;; no = sign
         (not (clojure.string/includes? trimmed "=")) acc
         :else
         (let [idx (.indexOf trimmed "=")
               k   (clojure.string/trim (subs trimmed 0 idx))
               v   (clojure.string/trim (subs trimmed (inc idx)))]
           (if (empty? k)
             acc
             (let [v (cond
                       (and (clojure.string/starts-with? v "\"")
                            (clojure.string/ends-with? v "\"")
                            (> (count v) 1))
                       (subs v 1 (dec (count v)))

                       (and (clojure.string/starts-with? v "'")
                            (clojure.string/ends-with? v "'")
                            (> (count v) 1))
                       (subs v 1 (dec (count v)))

                       :else v)]
               (assoc acc k v)))))))
   {}
   (clojure.string/split-lines contents)))

(defn read-dotenv
  "Reads <dir>/.env and parses it. Returns {} if file does not exist."
  ([] (read-dotenv "."))
  ([dir]
   (let [f (File. (str dir "/.env"))]
     (if (.exists f)
       (parse-dotenv (slurp f))
       {}))))

(def ^:private dotenv (delay (read-dotenv)))

(defn getenv
  "Returns the value of env var k. Real env var takes precedence over .env."
  [k]
  (or (System/getenv k) (get @dotenv k)))

(defn data-dir [] (or (getenv "YP_DATA_DIR") "."))
(defn token [] (getenv "YNAB_TOKEN"))

(defn plan-month []
  (if-let [s (getenv "YP_PLAN_MONTH")]
    (YearMonth/parse s)
    (YearMonth/now)))
