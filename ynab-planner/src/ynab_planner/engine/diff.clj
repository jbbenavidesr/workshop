(ns ynab-planner.engine.diff)

(defn plan-diff
  "Categories whose planned monthly differs from their current YNAB-derived monthly."
  [categories]
  (->> categories
       (filter #(not= (:ynab-current %) (:planned %)))
       (mapv (fn [c] {:id (:id c) :name (:name c) :group-name (:group-name c)
                      :current (:ynab-current c) :planned (:planned c)}))))

(defn grouped-diff
  "Group changed-category diff items by :group-name, preserving first-appearance order."
  [diff]
  (vec (for [g (distinct (map :group-name diff))]
         {:group-name g :items (filterv #(= g (:group-name %)) diff)})))
