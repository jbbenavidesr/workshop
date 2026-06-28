(ns ynab-planner.config-test
  (:require [clojure.test :refer [deftest is testing]]
            [ynab-planner.config :as config])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [java.io File]))

;;; --- parse-dotenv tests ---

(deftest parse-dotenv-simple-key-value
  (testing "parses a simple KEY=value line"
    (is (= {"KEY" "value"}
           (config/parse-dotenv "KEY=value")))))

(deftest parse-dotenv-blank-lines-ignored
  (testing "blank lines are ignored"
    (is (= {"A" "1" "B" "2"}
           (config/parse-dotenv "A=1\n\nB=2")))))

(deftest parse-dotenv-comment-lines-ignored
  (testing "lines whose first non-whitespace char is # are ignored"
    (is (= {"A" "1"}
           (config/parse-dotenv "# this is a comment\nA=1\n  # indented comment"))))
  (testing "hash-only input returns empty map"
    (is (= {}
           (config/parse-dotenv "# only comments")))))

(deftest parse-dotenv-whitespace-trimmed
  (testing "whitespace around key and value is trimmed"
    (is (= {"KEY" "value"}
           (config/parse-dotenv "  KEY  =  value  ")))))

(deftest parse-dotenv-value-contains-equals
  (testing "splits on first = only; value may contain ="
    (is (= {"A" "b=c"}
           (config/parse-dotenv "A=b=c")))))

(deftest parse-dotenv-double-quoted-value
  (testing "strips surrounding double quotes from value"
    (is (= {"TOKEN" "abc123"}
           (config/parse-dotenv "TOKEN=\"abc123\"")))))

(deftest parse-dotenv-single-quoted-value
  (testing "strips surrounding single quotes from value"
    (is (= {"TOKEN" "abc123"}
           (config/parse-dotenv "TOKEN='abc123'")))))

(deftest parse-dotenv-empty-input
  (testing "empty string returns empty map"
    (is (= {}
           (config/parse-dotenv ""))))
  (testing "whitespace-only string returns empty map"
    (is (= {}
           (config/parse-dotenv "   \n  ")))))

(deftest parse-dotenv-no-equals-sign-ignored
  (testing "lines without = are ignored"
    (is (= {}
           (config/parse-dotenv "NOEQUALS")))))

(deftest parse-dotenv-empty-key-ignored
  (testing "lines with empty key are ignored"
    (is (= {}
           (config/parse-dotenv "=value")))))

(deftest parse-dotenv-multiple-entries
  (testing "parses multiple valid entries"
    (is (= {"YP_DATA_DIR" "/data"
            "YNAB_TOKEN" "secret"
            "YP_PLAN_MONTH" "2026-07"}
           (config/parse-dotenv
            "YP_DATA_DIR=/data\nYNAB_TOKEN=secret\nYP_PLAN_MONTH=2026-07")))))

;;; --- read-dotenv tests ---

(defn- make-temp-dir []
  (str (Files/createTempDirectory "config-test" (make-array FileAttribute 0))))

(deftest read-dotenv-returns-empty-map-when-no-file
  (testing "returns {} when no .env file exists in the given dir"
    (let [tmp (make-temp-dir)]
      (is (= {} (config/read-dotenv tmp))))))

(deftest read-dotenv-parses-dotenv-file
  (testing "parses a .env file when it exists"
    (let [tmp (make-temp-dir)
          dotenv-file (File. (str tmp "/.env"))]
      (spit dotenv-file "YNAB_TOKEN=mytoken\nYP_DATA_DIR=/tmp/data")
      (is (= {"YNAB_TOKEN" "mytoken" "YP_DATA_DIR" "/tmp/data"}
             (config/read-dotenv tmp))))))
