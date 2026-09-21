(ns ynab-planner.ynab.mapping)

(defn- every-n
  "A NEED spec that recurs once every n periods. n of 1 is the common case, and
   leaving :every off there keeps the spec (and its tests) at their simplest."
  [amount cadence n]
  (cond-> {:type :needed-cadence :amount amount :cadence cadence}
    (> n 1) (assoc :every n)))

(defn goal->spec
  "Map a YNAB category's (pesos-converted) goal fields to an internal target spec."
  [{:keys [goal-type goal-target goal-target-month goal-cadence
           goal-cadence-frequency goal-under-funded]}]
  ;; goal_cadence splits into two families. For 0/1/2/13 the period length lives
  ;; in goal_cadence_frequency ("every 2 months"); for 3-12 and 14 YNAB ignores
  ;; the frequency and encodes the period in the cadence itself. Reading only the
  ;; cadence priced every non-every-period goal at full rate — 🚰 Agua's "300.000
  ;; cada 2 meses" came out as 300.000 a month.
  (let [freq (or goal-cadence-frequency 1)]
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
               1  (if (> freq 1)
                    (every-n goal-target :monthly freq)
                    {:type :monthly :amount goal-target})
               2  (every-n goal-target :weekly freq)
               13 (every-n goal-target :yearly freq)
               14 (every-n goal-target :yearly 2)
               (if (<= 3 goal-cadence 12)
                 ;; 3 = every 2 months … 12 = every 11 months
                 (every-n goal-target :monthly (dec goal-cadence))
                 {:type :monthly :amount goal-target}))
      "DEBT" {:type :monthly :amount (or goal-target 0)}
      {:type :none})))
