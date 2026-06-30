(ns ynab-planner.web.plan-edit
  "Pure transforms from parsed form params onto a plan map. No I/O."
  (:require [clojure.string :as str]))

(defn apply-income [plan params]
  (if-let [v (get params "income")]
    (assoc plan :income (or (parse-long v) (:income plan)))
    plan))

(defn apply-amounts
  "before-by-id: map of category id -> current monthly (long)."
  [plan before-by-id params]
  (reduce (fn [p [k v]]
            (if (str/starts-with? k "cat-")
              (let [id  (subs k 4)
                    n   (parse-long (str v))
                    cur (get before-by-id id)]
                (if (and n cur (not= n cur))
                  (assoc-in p [:categories id :spec] {:type :monthly :amount n})
                  p))
              p))
          plan params))

(defn apply-pillars [plan params]
  (reduce (fn [p [k v]]
            (if (str/starts-with? k "pillar-")
              (let [id (subs k 7)]
                (if (str/blank? v)
                  (cond-> p
                    (contains? (:categories p) id) (update-in [:categories id] dissoc :pillar))
                  (assoc-in p [:categories id :pillar] (keyword v))))
              p))
          plan params))
