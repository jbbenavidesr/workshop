(ns ynab-planner.web.views-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [ynab-planner.web.views :as views]))

(def view
  {:income 3600000 :synced-at "2026-06-27T12:00:00Z"
   :categories [{:id "c1" :name "🏠 Arriendo" :group-name "🏡 Hogar"
                 :pillar :necesario :spec {:type :monthly :amount 1600000}
                 :monthly 1600000 :ynab-current 1500000 :planned 1600000}]
   :balance {:total 1600000 :income 3600000 :difference 2000000 :status :under}
   :distribution {:pillars [{:pillar :necesario :amount 1600000 :actual-pct 44.4 :ideal-pct 40}]
                  :untagged 0}
   :diff [{:id "c1" :name "🏠 Arriendo" :current 1500000 :planned 1600000}]})

(deftest renders-key-content
  (let [html (views/render-page view)]
    (is (str/includes? html "🏠 Arriendo"))
    (is (str/includes? html "category-row"))
    (is (str/includes? html "distribution-bar"))
    (is (str/includes? html "3600000"))))           ; income present

(deftest progressive-enhancement-structure
  (let [html (views/render-page view)]
    ;; works without JS: a real form that POSTs, with named inputs
    (is (str/includes? html "id=\"plan-form\""))
    (is (str/includes? html "action=\"/plan/update\""))
    (is (str/includes? html "name=\"income\""))
    (is (str/includes? html "name=\"cat-c1\""))
    ;; semantic grouping + CUBE bracket class grouping
    (is (str/includes? html "<fieldset"))
    (is (str/includes? html "<legend"))
    (is (str/includes? html "[ stack ]"))))

(deftest update-response-is-json-friendly
  (let [resp (views/update-response view)]
    (is (= :under (get-in resp [:balance :status])))
    (is (= 1 (count (:diff resp))))))
