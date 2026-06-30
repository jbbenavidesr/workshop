(ns ynab-planner.web.views-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [ynab-planner.web.views :as views]))

(def view
  {:income 3600000 :synced-at "2026-06-27T12:00:00Z"
   :pillar-targets {:necesario 40}
   :categories [{:id "c1" :name "🏠 Arriendo" :group-name "🏡 Hogar"
                 :pillar :necesario :monthly 1600000 :ynab-current 1500000 :planned 1600000}]
   :balance {:total 1600000 :income 3600000 :difference 2000000 :status :under}
   :distribution {:pillars [{:pillar :necesario :amount 1600000 :actual-pct 44.4 :ideal-pct 40}]
                  :untagged 0}
   :pillar-sections {:pillars [{:pillar :necesario :amount 1600000 :actual-pct 44.4 :ideal-pct 40
                                :categories [{:id "c1" :name "🏠 Arriendo" :group-name "🏡 Hogar"
                                              :monthly 1600000}]}]
                     :untagged-categories [] :untagged 0}
   :diff [{:id "c1" :name "🏠 Arriendo" :group-name "🏡 Hogar" :current 1500000 :planned 1600000}]})

(deftest plan-page-renders-pillars-and-category-inputs
  (let [html (views/render-plan-page view)]
    (is (str/includes? html "pillar-card"))
    (is (str/includes? html "category-row"))
    (is (str/includes? html "🏠 Arriendo"))
    (is (str/includes? html "name=\"cat-c1\""))
    ;; no-JS base experience: the plan form POSTs to the engine
    (is (str/includes? html "action=\"/plan/update\""))
    ;; income is NOT editable on the plan page (moved to settings)
    (is (not (str/includes? html "name=\"income\"")))))

(deftest plan-page-links-out-to-apply-and-settings
  (let [html (views/render-plan-page view)]
    (is (str/includes? html "href=\"/apply\""))
    (is (str/includes? html "href=\"/settings\""))))

(deftest update-response-is-trimmed-and-json-friendly
  (let [resp (views/update-response view)]
    (is (= :under (get-in resp [:balance :status])))
    (is (= 1 (:diffCount resp)))
    (is (= "necesario" (:pillar (first (:pillars resp)))))
    (is (not (contains? resp :diff)))))

(deftest apply-page-groups-by-ynab-group-with-checkboxes
  (let [html (views/render-apply-page view)]
    (is (str/includes? html "🏡 Hogar"))           ; group heading
    (is (str/includes? html "🏠 Arriendo"))         ; changed category
    (is (str/includes? html "type=\"checkbox\""))   ; tick-off
    (is (str/includes? html "href=\"/\""))          ; back link
    (is (str/includes? html "$1.500.000"))          ; current
    (is (str/includes? html "$1.600.000"))))        ; planned

(deftest apply-page-empty-when-no-changes
  (let [html (views/render-apply-page (assoc view :diff []))]
    (is (str/includes? html "Sin cambios"))))

(deftest settings-page-has-income-classify-and-sync
  (let [html (views/render-settings-page view)]
    (is (str/includes? html "action=\"/income\""))
    (is (str/includes? html "name=\"income\""))
    (is (str/includes? html "action=\"/classify\""))
    (is (str/includes? html "name=\"pillar-c1\""))
    ;; option built from :pillar-targets, current pillar preselected
    (is (or (re-find #"(?s)<option[^>]*value=\"necesario\"[^>]*selected" html)
            (re-find #"(?s)<option[^>]*selected[^>]*value=\"necesario\"" html)))
    (is (str/includes? html "action=\"/sync\""))))

(deftest settings-page-each-category-appears-exactly-once
  ;; Regression: an untagged category that has a :group-name was being rendered
  ;; in the "Sin clasificar" fieldset AND again under its group, producing two
  ;; <select name="pillar-<id>"> elements — ambiguous form params on submit.
  (let [local-view {:income 3600000
                    :synced-at "2026-06-29T10:00:00Z"
                    :pillar-targets {:necesario 40}
                    :categories [{:id "u1" :name "Sin pilar" :group-name "🏡 Hogar"
                                  :pillar nil :monthly 0}
                                 {:id "t1" :name "Con pilar" :group-name "🏡 Hogar"
                                  :pillar :necesario :monthly 500000}]}
        html (views/render-settings-page local-view)]
    ;; untagged category appears EXACTLY once (not duplicated under its group)
    (is (= 1 (count (re-seq #"name=\"pillar-u1\"" html)))
        "pillar-u1 select should appear exactly once")
    ;; tagged category also appears exactly once
    (is (= 1 (count (re-seq #"name=\"pillar-t1\"" html)))
        "pillar-t1 select should appear exactly once")
    ;; untagged still shows in the Sin clasificar section
    (is (str/includes? html "Sin clasificar")
        "Sin clasificar fieldset should be present")))

(def view-with-over
  (assoc view :pillar-sections
         {:pillars [{:pillar :necesario :amount 1600000 :actual-pct 44.4 :ideal-pct 40
                     :categories [{:id "c1" :name "🏠 Arriendo" :group-name "🏡 Hogar" :monthly 1600000}]}
                    {:pillar :ahorro :amount 200000 :actual-pct 5.0 :ideal-pct 20
                     :categories [{:id "c9" :name "💰 FPV" :group-name "🏡 Hogar" :monthly 200000}]}]
          :untagged-categories [] :untagged 0}))

(deftest pillar-bar-marks-over-only
  (let [html (views/render-plan-page view-with-over)]
    ;; the over pillar (necesario 44.4 > 40) carries data-over="true"
    (is (str/includes? html "data-over=\"true\""))
    ;; the under pillar (ahorro 5 < 20) has a bar WITHOUT data-over
    (is (str/includes? html "[ pillar-bar ]"))
    ;; both pillars still render a fill segment
    (is (str/includes? html "[ fill ]"))
    (is (str/includes? html "[ over ]"))))

(deftest bar-geometry-under-at-over
  (let [bg #(deref (resolve 'ynab-planner.web.views/bar-geometry))]
    ;; under target: fill = actual/target, no over, no mark
    (let [g ((bg) 30 40)]
      (is (false? (:over? g)))
      (is (< 74.9 (:solid-pct g) 75.1))   ; 30/40 = 75%
      (is (= 0.0 (:over-pct g))))
    ;; at target: full, not over
    (let [g ((bg) 40 40)]
      (is (false? (:over? g)))
      (is (< 99.9 (:solid-pct g) 100.1)))
    ;; over target: rescale to actual; solid = target/actual, over = remainder, mark at solid
    (let [g ((bg) 50 40)]
      (is (true? (:over? g)))
      (is (< 79.9 (:solid-pct g) 80.1))   ; 40/50 = 80%
      (is (< 19.9 (:over-pct g) 20.1))    ; remainder
      (is (< 79.9 (:mark-pct g) 80.1)))   ; mark at the target boundary
    ;; edge: target 0, actual > 0 -> fully over
    (let [g ((bg) 10 0)]
      (is (true? (:over? g)))
      (is (= 0.0 (:solid-pct g)))
      (is (= 100.0 (:over-pct g))))
    ;; edge: both zero -> empty, not over
    (let [g ((bg) 0 0)]
      (is (false? (:over? g)))
      (is (= 0.0 (:solid-pct g))))))
