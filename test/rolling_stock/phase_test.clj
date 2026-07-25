(ns rolling-stock.phase-test
  (:require [clojure.test :refer [deftest is]]
            [rolling-stock.phase :as phase]))

(deftest ^{:doc "Phase table is properly defined."} phase-table-exists
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

(deftest ^{:doc "Start node is ADVISOR-NODE."} start-node-correct
  (is (= (phase/starting-node) phase/ADVISOR-NODE)
    "Starting node should be ADVISOR-NODE"))

(deftest ^{:doc "All expected nodes are defined in phase table."} nodes-defined
  (let [nodes (get phase/phase-table :nodes)]
    (is (contains? nodes phase/ADVISOR-NODE)
      "ADVISOR-NODE should be defined")
    (is (contains? nodes phase/GOVERNOR-NODE)
      "GOVERNOR-NODE should be defined")
    (is (contains? nodes phase/HOLD-NODE)
      "HOLD-NODE should be defined")
    (is (contains? nodes phase/COMPLETE-NODE)
      "COMPLETE-NODE should be defined")))

(deftest ^{:doc "Terminal nodes are correctly identified."} terminal-nodes
  (is (phase/is-terminal? phase/HOLD-NODE)
    "HOLD-NODE should be terminal")
  (is (phase/is-terminal? phase/COMPLETE-NODE)
    "COMPLETE-NODE should be terminal")
  (is (not (phase/is-terminal? phase/ADVISOR-NODE))
    "ADVISOR-NODE should not be terminal")
  (is (not (phase/is-terminal? phase/GOVERNOR-NODE))
    "GOVERNOR-NODE should not be terminal"))

(deftest ^{:doc "Phase edges define the workflow graph."} edges-defined
  (let [edges (get phase/phase-table :edges)]
    (is (seq edges)
      "Edges should not be empty")
    (is (some (fn [e] (= (first e) phase/ADVISOR-NODE))
             edges)
      "ADVISOR-NODE should have outgoing edges")
    (is (some (fn [e] (= (first e) phase/GOVERNOR-NODE))
             edges)
      "GOVERNOR-NODE should have outgoing edges")))

(deftest ^{:doc "Output node is COMPLETE-NODE."} output-node-complete
  (is (= (get phase/phase-table :output-node) phase/COMPLETE-NODE)
    "Output node should be COMPLETE-NODE"))
