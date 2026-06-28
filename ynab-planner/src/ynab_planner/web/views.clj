(ns ynab-planner.web.views
  (:require [hiccup2.core :as h]
            [clojure.string :as str]))

;; Colombian peso formatting uses '.' as the thousands separator.
(defn- fmt [n] (str "$" (str/replace (str (long n)) #"\B(?=(\d{3})+(?!\d))" ".")))

(defn- header [{:keys [synced-at]}]
  [:header {:class "[ cluster ]"}
   [:h1 "Presupuesto"]
   [:div {:class "[ cluster ]"}
    [:p {:class "[ text-muted ]"} "Sincronizado: " (or synced-at "nunca")]
    [:form {:method "post" :action "/sync"}
     [:button {:type "submit"} "Sincronizar con YNAB"]]]])

(defn- balance-bar [{:keys [income balance]}]
  [:section {:class "[ cluster ] [ card ]" :aria-label "Balance"}
   [:label "Ingreso base "
    [:input#income {:type "number" :name "income" :value income :inputmode "numeric"}]]
   [:p "Asignado: " [:span#total (fmt (:total balance))]]
   [:p "Diferencia: "
    [:output#difference {:data-status (name (:status balance))} (fmt (:difference balance))]]])

(defn- distribution [{:keys [pillars untagged]}]
  [:section#distribution {:class "[ stack ] [ card ]" :aria-label "Distribución"}
   [:h2 "Distribución"]
   (for [{:keys [pillar amount actual-pct ideal-pct]} pillars]
     [:distribution-bar {:data-pillar (name pillar)}
      [:span (name pillar)]
      [:span.amount (fmt amount)]
      [:span.bar [:span {:style (str "inline-size:" (min 100.0 actual-pct) "%")}]]
      [:span.pct.text-muted (format "%.1f%% / %d%%" actual-pct ideal-pct)]])
   (when (pos? untagged)
     [:p {:data-status "under"} "Sin clasificar: " (fmt untagged)])])

(def ^:private pillar-options
  [["" "—"]
   ["donaciones" "Donaciones"]
   ["ahorro" "Ahorro"]
   ["fun" "Fun"]
   ["necesario" "Necesario"]])

(defn- pillar-select [c]
  (let [current (or (some-> (:pillar c) name) "")]
    [:select {:name (str "pillar-" (:id c)) :aria-label "Pilar"}
     (for [[v label] pillar-options]
       [:option (cond-> {:value v} (= v current) (assoc :selected "selected")) label])]))

(defn- category-row [c]
  [:category-row {:data-id (:id c) :data-pillar (some-> (:pillar c) name)}
   [:label (:name c)
    [:input {:type "number" :name (str "cat-" (:id c))
             :value (:monthly c) :inputmode "numeric" :data-id (:id c)}]]
   [:span {:class "[ amount-echo ] [ text-muted ]"} (fmt (:monthly c))]
   (pillar-select c)])

(defn- category-fieldsets [categories]
  ;; Preserve YNAB's group order (first appearance) — categories arrive in
  ;; group-then-category order from the cache. group-by would scramble it.
  (for [group (distinct (map :group-name categories))
        :let [cats (filter #(= group (:group-name %)) categories)]]
    [:fieldset {:class "[ stack ] [ card ]"}
     [:legend group]
     (map category-row cats)]))

(defn- diff-list [diff]
  [:section#diff {:class "[ stack ] [ card ]"}
   [:h2 "Cambios para aplicar en YNAB"]
   (if (empty? diff)
     [:p "Sin cambios — el plan coincide con YNAB."]
     [:ul (for [{:keys [name current planned]} diff]
            [:li name ": " (fmt current) " → " (fmt planned)])])])

(defn render-page [view]
  (str
   "<!doctype html>"
   (h/html
    [:html {:lang "es"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title "YNAB Planner"]
      [:link {:rel "stylesheet" :href "/css/tokens.css"}]
      [:link {:rel "stylesheet" :href "/css/main.css"}]
      [:script {:type "module" :src "/js/app.js"}]]
     [:body
      [:main {:class "[ stack ]"}
       (header view)
       ;; Whole plan lives in ONE form so it submits and recomputes without JS.
       [:form#plan-form {:method "post" :action "/plan/update" :class "[ stack ]"}
        (balance-bar view)
        (distribution (:distribution view))
        [:div {:class "[ stack ]"}
         [:p {:class "[ text-muted ]"} "Editar un monto fija ese valor como meta mensual para ese mes."]
         (category-fieldsets (:categories view))]
        [:button {:type "submit"} "Recalcular"]]
       (diff-list (:diff view))]]])))

(defn update-response
  "JSON-friendly subset returned after a plan edit, for the JS controller.
   Pillar percentages are emitted with clean camelCase keys."
  [view]
  {:balance (:balance view)
   :distribution {:pillars (mapv (fn [p] {:pillar (name (:pillar p))
                                          :amount (:amount p)
                                          :actualPct (:actual-pct p)
                                          :idealPct (:ideal-pct p)})
                                 (get-in view [:distribution :pillars]))
                  :untagged (get-in view [:distribution :untagged])}
   :diff (:diff view)
   :categories (mapv #(select-keys % [:id :monthly]) (:categories view))})
