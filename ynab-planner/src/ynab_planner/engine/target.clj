(ns ynab-planner.engine.target
  (:import [java.time YearMonth]
           [java.time.temporal ChronoUnit]))

(defn parse-year-month [s]
  (YearMonth/parse (subs s 0 7)))

(defn months-left
  "Inclusive count of months from plan-month to target (both ends). Minimum 1."
  [^YearMonth plan-month ^YearMonth target]
  (max 1 (inc (.until plan-month target ChronoUnit/MONTHS))))

(defn- round-div [n d]
  (long (Math/round (/ (double n) (double d)))))

(defmulti derive-monthly
  "Monthly contribution (long pesos) for a target spec."
  (fn [spec _balance _plan-month] (:type spec)))

(defmethod derive-monthly :default [_ _ _] 0)
(defmethod derive-monthly :none [_ _ _] 0)
(defmethod derive-monthly :monthly [spec _ _] (:amount spec))
(defmethod derive-monthly :balance-rolling [spec _ _] (:monthly spec))

(defmethod derive-monthly :needed-cadence [spec _ _]
  (case (:cadence spec)
    :yearly (round-div (:amount spec) 12)
    :weekly (round-div (* (:amount spec) 52) 12)
    (:amount spec)))

(defmethod derive-monthly :balance-by-date [spec balance plan-month]
  (let [target    (:target spec)
        date      (parse-year-month (:date spec))
        m         (months-left plan-month date)
        remaining (- target (or balance 0))]
    (max 0 (round-div remaining m))))
