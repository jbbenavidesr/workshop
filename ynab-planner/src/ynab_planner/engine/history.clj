(ns ynab-planner.engine.history
  "Spend history per category, summarized. Pure: no I/O, no network.")

(defn complete
  "Rows strictly before the plan month. The plan month itself is still filling up
   and YNAB always returns a future month; averaging either in would understate
   every category."
  [rows plan-month]
  (let [cutoff (str plan-month)]
    (filterv #(neg? (compare (:month %) cutoff)) rows)))

(defn- mean [xs]
  (if (seq xs)
    (long (Math/round (/ (double (reduce + xs)) (count xs))))
    0))

(defn- median [xs]
  (let [v (vec (sort xs))
        n (count v)]
    (cond
      (zero? n)   0
      (odd? n)    (nth v (quot n 2))
      :else       (mean [(nth v (dec (quot n 2))) (nth v (quot n 2))]))))

(defn stats
  "Summary of what a category actually cost, over the complete months on record.
   :n says how many months that is — with a young budget it is well under 12,
   and an average is only as trustworthy as its n."
  [rows plan-month]
  (let [xs (mapv :spent (complete rows plan-month))]
    {:n      (count xs)
     :avg6   (mean (take-last 6 xs))
     :median (median xs)
     :max    (reduce max 0 xs)
     :series xs}))

(defn slack
  "Planned minus typical spend. Positive is padding you could harvest; negative
   means the target is one you quietly top up every month.

   Claimed only where there is evidence of spending. A category that has never
   spent — a savings goal, a fund still accumulating — would otherwise read as
   pure padding and top every harvest list, which is exactly backwards."
  [planned {:keys [n avg6 max]}]
  (if (and (pos? (or n 0)) (pos? (or max 0)))
    (- planned avg6)
    0))
