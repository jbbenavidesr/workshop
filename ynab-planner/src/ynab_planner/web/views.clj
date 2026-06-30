(ns ynab-planner.web.views
  (:require [hiccup2.core :as h]
            [clojure.string :as str]
            [ynab-planner.engine.diff :as diff]))

;; Colombian peso formatting uses '.' as the thousands separator.
(defn- fmt [n] (str "$" (str/replace (str (long n)) #"\B(?=(\d{3})+(?!\d))" ".")))

(defn- layout [{:keys [title]} & body]
  (clojure.core/str
   "<!doctype html>"
   (h/html
    [:html {:lang "es" :class "theme-light"}
     [:head
      [:meta {:charset "utf-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:title (or title "YNAB Planner")]
      [:link {:rel "stylesheet" :href "/css/tokens.css"}]
      [:link {:rel "stylesheet" :href "/css/main.css"}]
      [:script {:type "module" :src "/js/app.js"}]]
     [:body [:main {:class "[ stack ]"} body]]])))

;; ---------- Plan page ----------

(defn- plan-header [{:keys [synced-at]}]
  [:header {:class "[ cluster ]"}
   [:h1 "Presupuesto"]
   [:div {:class "[ cluster ]"}
    [:a {:class "[ text-muted ]" :href "/settings"} "⟳ " (or synced-at "sin sincronizar")]
    [:a {:href "/settings"} "⚙ Ajustes"]]])

(defn- balance-summary [{:keys [income balance]}]
  [:section {:class "[ stack ] [ card ]" :aria-label "Balance"}
   [:p {:class "[ balance-assigned ]"}
    [:span#total (fmt (:total balance))] " de " (fmt income) " ingreso"]
   [:output#difference {:data-status (name (:status balance))} (fmt (:difference balance))]])

(defn- untagged-nudge [n]
  (when (pos? n)
    [:a {:class "[ card ] [ nudge ]" :data-status "under" :href "/settings"}
     "⚠️ " n " sin clasificar — clasifícalas →"]))

(defn- category-row [c]
  [:category-row {:data-id (:id c)}
   [:label
    [:span (:name c) " " [:span {:class "[ group-tag ] [ text-muted ]"} "· " (:group-name c)]]
    [:input {:type "number" :name (str "cat-" (:id c)) :value (:monthly c)
             :inputmode "numeric" :data-id (:id c)}]]
   [:span {:class "[ amount-echo ] [ text-muted ]"} (fmt (:monthly c))]])

(defn- pillar-card [i {:keys [pillar amount actual-pct ideal-pct categories]}]
  [:pillar-card {:data-index i :data-pillar (name pillar)}
   [:details {:open true}
    [:summary {:class "[ pillar-head ]"}
     [:span {:class "[ pillar-name ]"} (name pillar)]
     [:span {:class "[ pillar-bar ]"}
      [:span {:class "[ fill ]" :style (str "inline-size:" (min 100.0 (double actual-pct)) "%")}]
      [:span {:class "[ tick ]" :style (str "inset-inline-start:" (min 100.0 (double ideal-pct)) "%")}]]
     [:span {:class "[ pillar-amount ]"} (fmt amount)]
     [:span {:class "[ pillar-pct ] [ text-muted ]"} (format "%.1f%% / %d%%" (double actual-pct) ideal-pct)]]
    (map category-row categories)]])

(defn render-plan-page [view]
  (let [sections (:pillar-sections view)]
    (layout {:title "Presupuesto"}
     (plan-header view)
     [:form#plan-form {:method "post" :action "/plan/update" :class "[ stack ]"}
      (balance-summary view)
      (untagged-nudge (count (:untagged-categories sections)))
      [:div#pillars {:class "[ stack ]"}
       (map-indexed pillar-card (:pillars sections))]
      [:noscript [:button {:type "submit"} "Recalcular"]]]
     [:a {:class "[ cta ]" :href "/apply"}
      "Ver cambios para aplicar → " [:span#diff-count (count (:diff view))]])))

(defn update-response
  "JSON-friendly subset returned after a plan edit, for the JS controller."
  [view]
  {:balance (:balance view)
   :diffCount (count (:diff view))
   :pillars (mapv (fn [p] {:pillar (name (:pillar p))
                           :amount (:amount p)
                           :actualPct (:actual-pct p)
                           :idealPct (:ideal-pct p)})
                  (get-in view [:pillar-sections :pillars]))
   :untagged (get-in view [:pillar-sections :untagged])})

;; ---------- Apply page ----------

(defn- diff-line [{:keys [name current planned]}]
  (let [dir (cond (> planned current) "▲" (< planned current) "▼" :else "")]
    [:li {:class "[ diff-line ]"}
     [:label
      [:input {:type "checkbox"}]
      [:span {:class "[ diff-name ]"} name]
      [:span {:class "[ diff-from ] [ text-muted ]"} (fmt current)]
      [:span {:class "[ diff-arrow ]"} "→"]
      [:span {:class "[ diff-to ]"} (fmt planned) " " dir]]]))

(defn render-apply-page [view]
  (layout {:title "Aplicar en YNAB"}
   [:a {:href "/"} "← Volver al plan"]
   [:h1 "Cambios para aplicar en YNAB"]
   (if (empty? (:diff view))
     [:p "Sin cambios — el plan coincide con YNAB."]
     (for [{:keys [group-name items]} (diff/grouped-diff (:diff view))]
       [:section {:class "[ stack ] [ card ]"}
        [:h2 group-name]
        [:ul {:class "[ diff-list ]"} (map diff-line items)]]))))

;; ---------- Settings page ----------

(defn- pillar-select [c pillar-targets]
  (let [current (or (some-> (:pillar c) name) "")]
    [:select {:name (str "pillar-" (:id c)) :aria-label "Pilar"}
     [:option (cond-> {:value ""} (= "" current) (assoc :selected "selected")) "—"]
     (for [p (keys pillar-targets) :let [v (name p)]]
       [:option (cond-> {:value v} (= v current) (assoc :selected "selected")) v])]))

(defn- classify-fieldset [legend cats pillar-targets status]
  [:fieldset (cond-> {:class "[ stack ]"} status (assoc :data-status status))
   [:legend legend]
   (for [c cats]
     [:label [:span (:name c)] (pillar-select c pillar-targets)])])

(defn render-settings-page [view]
  (let [cats     (:categories view)
        targets  (:pillar-targets view)
        untagged (filter #(nil? (:pillar %)) cats)
        tagged   (filter #(some? (:pillar %)) cats)]
    (layout {:title "Ajustes"}
     [:a {:href "/"} "← Volver al plan"]
     [:h1 "Ajustes"]
     [:form {:method "post" :action "/income" :class "[ stack ] [ card ]"}
      [:h2 "Ingreso base"]
      [:label [:span "Ingreso mensual"]
       [:input {:type "number" :name "income" :value (:income view) :inputmode "numeric"}]]
      [:button {:type "submit"} "Guardar ingreso"]]
     [:form {:method "post" :action "/classify" :class "[ stack ] [ card ]"}
      [:h2 "Clasificación de pilares"]
      (when (seq untagged)
        (classify-fieldset "⚠️ Sin clasificar" untagged targets "under"))
      (for [group (distinct (map :group-name tagged))
            :let [gcats (filter #(= group (:group-name %)) tagged)]]
        (classify-fieldset group gcats targets nil))
      [:button {:type "submit"} "Guardar clasificación"]]
     [:section {:class "[ stack ] [ card ]"}
      [:h2 "Datos · YNAB"]
      [:p {:class "[ text-muted ]"} "Sincronizado: " (or (:synced-at view) "nunca")]
      [:form {:method "post" :action "/sync"} [:button {:type "submit"} "Sincronizar ahora"]]])))

;; TEMP compat shim — removed in the server-routing task.
(def render-page render-plan-page)
