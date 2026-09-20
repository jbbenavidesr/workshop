(ns ynab-planner.ynab.mapping)

(defn goal->spec
  "Map a YNAB category's (pesos-converted) goal fields to an internal target spec."
  [{:keys [goal-type goal-target goal-target-month goal-cadence goal-under-funded]}]
  (case goal-type
    nil   {:type :none}
    "MF"  {:type :monthly :amount goal-target}
    "TBD" {:type :balance-by-date :target goal-target :date goal-target-month}
    "TB"  {:type :balance-rolling :monthly (or goal-under-funded 0)}
    "NEED" (case goal-cadence
             ;; 0 is "have X by <month>" — a one-off fund, not a recurring cost.
             ;; Spread over the months left, which is what :balance-by-date does.
             0  (if goal-target-month
                  {:type :balance-by-date :target goal-target :date goal-target-month}
                  {:type :monthly :amount goal-target})
             1  {:type :monthly :amount goal-target}
             2  {:type :needed-cadence :amount goal-target :cadence :weekly}
             13 {:type :needed-cadence :amount goal-target :cadence :yearly}
             {:type :monthly :amount goal-target})
    "DEBT" {:type :monthly :amount (or goal-target 0)}
    {:type :none}))
