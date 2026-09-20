(ns ynab-planner.engine.view
  (:require [ynab-planner.engine.target :as t]
            [ynab-planner.engine.distribution :as dist]
            [ynab-planner.engine.balance :as bal]
            [ynab-planner.engine.diff :as diff]
            [ynab-planner.engine.history :as hist]))

(defn resolve-category
  "Merge a cached category with the user's plan overrides and derive its numbers."
  [cache-cat plan plan-month]
  (let [id           (:id cache-cat)
        override     (get-in plan [:categories id])
        planned-spec (or (:spec override) (:ynab-spec cache-cat))
        balance      (:balance cache-cat)
        stats        (hist/stats (:history cache-cat []) plan-month)
        planned      (t/derive-monthly planned-spec balance plan-month)]
    {:id id
     :name (:name cache-cat)
     :group-name (:group-name cache-cat)
     :pillar (:pillar override)
     :skip? (boolean (:skip override))
     :spec planned-spec
     :overridden? (some? (:spec override))
     ;; :monthly is the display alias consumed by distribute/balance-check; equals :planned
     :monthly      planned
     :ynab-current (t/derive-monthly (:ynab-spec cache-cat) balance plan-month)
     :planned      planned
     :history      stats
     :slack        (hist/slack planned stats)}))

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
        hist-by-id (:history cache {})
        all    (mapv #(resolve-category (assoc % :history (get hist-by-id (:id %) []))
                                        plan plan-month)
                     (:categories cache))
        ;; skipped categories are not part of this plan: they leave the totals,
        ;; the distribution and the checklist, but the classify page still lists them
        cats   (filterv (complement :skip?) all)
        dist   (dist/distribute cats income (:pillar-targets plan))]
    {:income income
     :synced-at (:synced-at cache)
     :categories cats
     :all-categories all
     :pillar-targets (:pillar-targets plan)
     :balance       (bal/balance-check cats income)
     :distribution  dist
     :pillar-sections (pillar-sections cats dist)
     :diff          (diff/plan-diff cats)}))
