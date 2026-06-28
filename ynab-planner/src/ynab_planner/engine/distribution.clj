(ns ynab-planner.engine.distribution)

(defn distribute
  "Sum monthly contributions per pillar and compare to ideal percentages.
   categories: seq of {:pillar kw-or-nil :monthly long}."
  [categories income pillar-targets]
  (let [by-pillar (group-by :pillar categories)
        amount-of (fn [p] (reduce + 0 (map :monthly (get by-pillar p []))))
        pct       (fn [amt] (if (pos? income) (double (/ (* 100 amt) income)) 0.0))]
    {:pillars  (for [[p ideal] pillar-targets
                     :let [amt (amount-of p)]]
                 {:pillar p :amount amt :actual-pct (pct amt) :ideal-pct ideal})
     :untagged (amount-of nil)}))
