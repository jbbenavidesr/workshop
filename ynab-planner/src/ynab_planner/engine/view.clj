(ns ynab-planner.engine.view
  (:require [ynab-planner.engine.target :as t]
            [ynab-planner.engine.distribution :as dist]
            [ynab-planner.engine.balance :as bal]
            [ynab-planner.engine.diff :as diff]))

(defn resolve-category
  "Merge a cached category with the user's plan overrides and derive its numbers."
  [cache-cat plan plan-month]
  (let [id           (:id cache-cat)
        override     (get-in plan [:categories id])
        planned-spec (or (:spec override) (:ynab-spec cache-cat))
        balance      (:balance cache-cat)]
    {:id id
     :name (:name cache-cat)
     :group-name (:group-name cache-cat)
     :pillar (:pillar override)
     :spec planned-spec
     ;; :monthly is the display alias consumed by distribute/balance-check; equals :planned
     :monthly      (t/derive-monthly planned-spec balance plan-month)
     :ynab-current (t/derive-monthly (:ynab-spec cache-cat) balance plan-month)
     :planned      (t/derive-monthly planned-spec balance plan-month)}))

(defn pillar-sections
  "Ordered pillar sections for the plan view: each distribution pillar plus its
   categories (sorted by :monthly desc). Untagged categories are returned separately."
  [categories distribution]
  (let [by-pillar (group-by :pillar categories)
        sort-desc (fn [cs] (vec (sort-by :monthly > cs)))]
    {:pillars (vec (for [p (:pillars distribution)]
                     (assoc p :categories (sort-desc (get by-pillar (:pillar p) [])))))
     :untagged-categories (sort-desc (get by-pillar nil []))
     :untagged (:untagged distribution)}))

(defn build-view [cache plan plan-month]
  (let [income (:income plan)
        cats   (mapv #(resolve-category % plan plan-month) (:categories cache))
        dist   (dist/distribute cats income (:pillar-targets plan))]
    {:income income
     :synced-at (:synced-at cache)
     :categories cats
     :pillar-targets (:pillar-targets plan)
     :balance       (bal/balance-check cats income)
     :distribution  dist
     :pillar-sections (pillar-sections cats dist)
     :diff          (diff/plan-diff cats)}))
