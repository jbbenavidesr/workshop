(ns ynab-planner.engine.balance)

(defn balance-check
  "Compare the sum of monthly contributions to income."
  [categories income]
  (let [total (reduce + 0 (map :monthly categories))
        diff  (- income total)]
    {:total total
     :income income
     :difference diff
     :status (cond (zero? diff) :balanced
                   (neg? diff)  :over
                   :else        :under)}))
