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
                    n   (parse-long v)
                    cur (get before-by-id id)]
                (if (and (some? n) (contains? before-by-id id) (not= n cur))
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

(defn- drop-spec [plan id]
  (let [entry (dissoc (get-in plan [:categories id]) :spec)]
    (if (seq entry)
      (assoc-in plan [:categories id] entry)
      (update plan :categories dissoc id))))

(defn reset-targets
  "Discard target overrides, back to what YNAB says. Pillar tags survive — YNAB
   has no pillars, so there is nothing to reset them to. An \"id\" param resets
   that one category; no id resets all of them."
  [plan params]
  (if-let [id (get params "id")]
    (drop-spec plan id)
    (reduce drop-spec plan (keys (:categories plan)))))
