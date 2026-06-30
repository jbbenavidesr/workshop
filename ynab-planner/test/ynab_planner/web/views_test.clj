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
