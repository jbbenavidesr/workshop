(ns ynab-planner.engine.diff)

(defn plan-diff
  "Categories whose planned monthly differs from their current YNAB-derived monthly."
  [categories]
  (->> categories
       (filter #(not= (:ynab-current %) (:planned %)))
       (mapv (fn [c] {:id (:id c) :name (:name c)
                      :current (:ynab-current c) :planned (:planned c)}))))
