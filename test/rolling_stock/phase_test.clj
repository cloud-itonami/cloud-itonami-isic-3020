(ns rolling-stock.phase-test
  (:require [clojure.test :refer [deftest is]]
            [rolling-stock.phase :as phase]))

(deftest phase-table-exists
  "Phase table is properly defined."
  (is (map? phase/phase-table)
    "Phase table should be a map")
  (is (contains? phase/phase-table :start)
    "Phase table should have :start")
  (is (contains? phase/phase-table :nodes)
    "Phase table should have :nodes")
  (is (contains? phase/phase-table :edges)
    "Phase table should have :edges")
  (is (contains? phase/phase-table :output-node)
    "Phase table should have :output-node"))

(deftest start-node-correct
  "Start node is ADVISOR-NODE."
  (is (= (phase/starting-node) phase/ADVISOR-NODE)
    "Starting node should be ADVISOR-NODE"))

(deftest nodes-defined
  "All expected nodes are defined in phase table."
  (let [nodes (get phase/phase-table :nodes)]
    (is (contains? nodes phase/ADVISOR-NODE)
      "ADVISOR-NODE should be defined")
    (is (contains? nodes phase/GOVERNOR-NODE)
      "GOVERNOR-NODE should be defined")
    (is (contains? nodes phase/HOLD-NODE)
      "HOLD-NODE should be defined")
    (is (contains? nodes phase/COMPLETE-NODE)
      "COMPLETE-NODE should be defined")))

(deftest terminal-nodes
  "Terminal nodes are correctly identified."
  (is (phase/is-terminal? phase/HOLD-NODE)
    "HOLD-NODE should be terminal")
  (is (phase/is-terminal? phase/COMPLETE-NODE)
    "COMPLETE-NODE should be terminal")
  (is (not (phase/is-terminal? phase/ADVISOR-NODE))
    "ADVISOR-NODE should not be terminal")
  (is (not (phase/is-terminal? phase/GOVERNOR-NODE))
    "GOVERNOR-NODE should not be terminal"))

(deftest edges-defined
  "Phase edges define the workflow graph."
  (let [edges (get phase/phase-table :edges)]
    (is (seq edges)
      "Edges should not be empty")
    (is (some (fn [e] (= (first e) phase/ADVISOR-NODE))
             edges)
      "ADVISOR-NODE should have outgoing edges")
    (is (some (fn [e] (= (first e) phase/GOVERNOR-NODE))
             edges)
      "GOVERNOR-NODE should have outgoing edges")))

(deftest output-node-complete
  "Output node is COMPLETE-NODE."
  (is (= (get phase/phase-table :output-node) phase/COMPLETE-NODE)
    "Output node should be COMPLETE-NODE"))
