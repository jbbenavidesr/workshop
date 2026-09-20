(ns ynab-planner.web.views
  (:require [hiccup2.core :as h]
            [clojure.string :as str]
            [ynab-planner.engine.diff :as diff]))

;; Colombian peso formatting uses '.' as the thousands separator.
(defn- fmt [n] (str "$" (str/replace (str (long n)) #"\B(?=(\d{3})+(?!\d))" ".")))

(defn fmt-short
  "Compact pesos for dense rows: 2049000 -> 2,0M, 551000 -> 551k, 0 -> a dot.
   No currency mark — the row is all money and the column is narrow."
  [n]
  (let [n (long (or n 0))
        a (abs n)
        sg (if (neg? n) "-" "")]
    (cond
      (zero? n)      "·"
      (>= a 1000000) (str sg (str/replace (format "%.1f" (/ a 1e6)) "." ",") "M")
      (>= a 1000)    (str sg (Math/round (/ a 1000.0)) "k")
      :else          (str sg a))))

(defn- fmt-signed [n] (if (zero? n) "—" (str (when (pos? n) "+") (fmt-short n))))

(defn- r2 [x] (double (/ (Math/round (* 100.0 (double x))) 100.0)))

(defn spark-geometry
  "Bars for a spend sparkline, in a 0..w by 0..h user space — CSS does the real
   sizing, so these are ratios, not pixels. The scale spans zero, the whole
   series and the planned amount, so the target line always lands inside the box
   and a refund reads below the baseline."
  [series planned]
  (when (seq series)
    (let [n    (count series)
          w    (* 4 n)
          h    20
          lo   (double (min 0 (reduce min series)))
          hi   (double (max 1 (reduce max (cons (or planned 0) series))))
          span (- hi lo)
          y    (fn [v] (r2 (* h (- 1.0 (/ (- (double v) lo) span)))))
          bw   (/ (double w) n)
          tgt? (and planned (pos? planned))]
      {:w w :h h
       :zero-y   (when (neg? lo) (y 0))
       :target-y (when tgt? (y planned))
       :bars (vec (map-indexed
                   (fn [i v]
                     (let [y0 (y (max v 0))
                           y1 (y (min v 0))
                           ;; a zero month still gets a visible sliver, nudged up
                           ;; so the minimum height cannot hang out of the box
                           bh (max 0.6 (- y1 y0))]
                       {:x (r2 (+ (* i bw) 0.35))
                        :w (r2 (- bw 0.7))
                        :y (r2 (min y0 (- h bh)))
                        :h (r2 bh)
                        :over? (boolean (and tgt? (> v planned)))}))
                   series))})))

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
    [:a {:class "[ card ] [ nudge ]" :data-status "under" :href "/classify"}
     "⚠️ " n " sin clasificar — clasifícalas →"]))

(defn- sparkline
  "Twelve months of spend as bars, with a dashed line at the planned amount.
   Decorative: the same numbers follow it in text, so it is hidden from AT."
  [c]
  (when-let [{:keys [w h bars zero-y target-y]}
             (spark-geometry (get-in c [:history :series]) (:planned c))]
    [:svg {:class "[ spark ]" :viewBox (str "0 0 " w " " h)
           :preserveAspectRatio "none" :aria-hidden "true" :focusable "false"}
     (when zero-y
       [:line {:class "[ zero ]" :x1 0 :x2 w :y1 zero-y :y2 zero-y
               :vector-effect "non-scaling-stroke"}])
     (for [b bars]
       [:rect (cond-> {:x (:x b) :y (:y b) :width (:w b) :height (:h b)}
                (:over? b) (assoc :data-over "true"))])
     (when target-y
       [:line {:class "[ target ]" :x1 0 :x2 w :y1 target-y :y2 target-y
               :vector-effect "non-scaling-stroke"}])]))

(defn- history-stats [c]
  (let [{:keys [n avg6 median] mx :max} (:history c)
        months (when (pos? (or n 0))
                 ;; an average over two months is not a fact; say so, do not imply it
                 [:span {:data-confidence (when (< n 6) "low")} n "m"])]
    [:span {:class "[ stats ]"}
     (cond
       (not (pos? (or n 0))) [:span "sin historial"]
       ;; a savings goal that has never paid out has nothing to average — four
       ;; dots in a row say less than one honest phrase
       (not (pos? (or mx 0))) (list [:span "sin gasto"] months)
       :else (list [:span "prom " [:b (fmt-short avg6)]]
                   [:span "med " (fmt-short median)]
                   [:span "m\u00e1x " (fmt-short mx)]
                   months))]))

(defn- slack-kind [s] (cond (pos? s) "pos" (neg? s) "neg" :else "none"))

(defn- slack-pill [c]
  (let [s (or (:slack c) 0)]
    [:span {:class "[ pill ]"
            :data-slack (slack-kind s)
            :title (if (zero? s)
                     "Sin gasto registrado \u2014 no hay evidencia de holgura"
                     "Holgura: plan menos gasto t\u00edpico")}
     (fmt-signed s)]))

(defn- category-row [c]
  (let [field (str "cat-" (:id c))]
    [:category-row {:data-id (:id c)}
     [:div {:class "[ row-main ]"}
      [:label {:for field}
       (:name c) [:span {:class "[ group-tag ] [ text-muted ]"} " \u00b7 " (:group-name c)]]
      (slack-pill c)
      [:input {:type "number" :id field :name field :value (:monthly c)
               :inputmode "numeric" :data-id (:id c)}]
      ;; Forms cannot nest, so the revert buttons sit inside #plan-form but submit
      ;; #reset-form via the native form= attribute. Always rendered, hidden until
      ;; the category is actually overridden, so JS can toggle them after an edit.
      [:button {:type "submit" :form "reset-form" :name "id" :value (:id c)
                :data-reset "" :hidden (when-not (:overridden? c) "hidden")
                :class "[ ghost-btn ]"
                :aria-label (str "Restaurar " (:name c) " a YNAB")}
       "\u21ba"]]
     [:div {:class "[ row-history ]"}
      (sparkline c)
      (history-stats c)]]))

(defn- bar-geometry
  "Geometry for a target-relative pillar bar. actual/target are percentages of
   income. Full bar = the target's share; the fill grows as actual/target. When
   over target the bar rescales so full width = actual, with the target mark at
   the solid/over boundary. Returns percentages as doubles."
  [actual target]
  (let [a (double actual)
        t (double target)]
    (cond
      (<= a t) {:solid-pct (if (pos? t) (* 100.0 (/ a t)) 0.0)
                :over-pct 0.0 :over? false :mark-pct 0.0}
      :else    (let [solid (if (pos? a) (* 100.0 (/ t a)) 0.0)]
                 {:solid-pct solid :over-pct (- 100.0 solid)
                  :over? true :mark-pct solid}))))

(defn- pillar-card [i {:keys [pillar amount actual-pct ideal-pct categories]}]
  (let [{:keys [solid-pct over-pct over? mark-pct]} (bar-geometry actual-pct ideal-pct)]
    [:pillar-card {:data-index i :data-pillar (name pillar)}
     [:details {:open true}
      [:summary {:class "[ pillar-head ]"}
       [:span {:class "[ pillar-name ]"} (name pillar)]
       [:span {:class "[ pillar-bar ]" :data-over (when over? "true")}
        [:span {:class "[ fill ]" :style (str "inline-size:" solid-pct "%")}]
        [:span {:class "[ over ]" :style (str "inline-size:" over-pct "%")}]
        [:span {:class "[ tick ]" :style (str "inset-inline-start:" mark-pct "%")}]]
       [:span {:class "[ pillar-amount ]"} (fmt amount)]
       [:span {:class "[ pillar-pct ] [ text-muted ]"}
        (format "%.1f%% / %d%%" (double actual-pct) ideal-pct)
        [:span {:class "[ pillar-toggle ]"}]]]
      (map category-row categories)]]))

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
     [:form#reset-form {:method "post" :action "/plan/reset"}]
     [:a {:class "[ cta ]" :href "/apply"}
      "Ver cambios para aplicar → " [:span#diff-count (count (:diff view))]]
     [:button#reset-all {:type "submit" :form "reset-form" :class "[ ghost-btn ]"
                         :hidden (when-not (some :overridden? (:categories view)) "hidden")}
      "\u21ba Restaurar todo a YNAB"])))

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
   :untagged (get-in view [:pillar-sections :untagged])
   :overridden (mapv :id (filter :overridden? (:categories view)))
   ;; Rendered server-side so the JS never has to know how a peso is formatted
   ;; or what counts as slack — it only assigns text and a data attribute.
   :slack (into {} (for [c (:categories view)
                         :let [s (or (:slack c) 0)]]
                     [(:id c) {:text (fmt-signed s) :kind (slack-kind s)}]))})

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
  (layout {:title "Ajustes"}
   [:a {:href "/"} "← Volver al plan"]
   [:h1 "Ajustes"]
   [:form {:method "post" :action "/income" :class "[ stack ] [ card ]"}
    [:h2 "Ingreso base"]
    [:label [:span "Ingreso mensual"]
     [:input {:type "number" :name "income" :value (:income view) :inputmode "numeric"}]]
    [:button {:type "submit"} "Guardar ingreso"]]
   [:a {:class "[ card ] [ settings-link ]" :href "/classify"} "Clasificar categorías →"]
   [:section {:class "[ stack ] [ card ]"}
    [:h2 "Datos · YNAB"]
    [:p {:class "[ text-muted ]"} "Sincronizado: " (or (:synced-at view) "nunca")]
    [:form {:method "post" :action "/sync"} [:button {:type "submit"} "Sincronizar ahora"]]]))

(defn render-classify-page [view]
  (let [cats     (:categories view)
        targets  (:pillar-targets view)
        untagged (filter #(nil? (:pillar %)) cats)
        tagged   (filter #(some? (:pillar %)) cats)]
    (layout {:title "Clasificar categorías"}
     [:a {:href "/settings"} "← Volver a ajustes"]
     [:h1 "Clasificar categorías"]
     [:form {:method "post" :action "/classify" :class "[ stack ] [ card ]"}
      (when (seq untagged)
        (classify-fieldset "⚠️ Sin clasificar" untagged targets "under"))
      (for [group (distinct (map :group-name tagged))
            :let [gcats (filter #(= group (:group-name %)) tagged)]]
        (classify-fieldset group gcats targets nil))
      [:button {:type "submit"} "Guardar clasificación"]])))
