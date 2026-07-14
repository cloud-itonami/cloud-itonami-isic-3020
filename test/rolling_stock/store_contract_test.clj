(ns rolling-stock.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [rolling-stock.store :as store]))

(deftest mem-store-initialization
  "In-memory store initializes with reference data."
  (let [st (store/mem-store)]
    (is (contains? st :data)
      "Store should have :data key")
    (is (map? @(:data st))
      "Store data should be a map")))

(deftest unit-lookup
  "Unit records can be retrieved by ID."
  (let [st (store/mem-store)
        unit (store/unit st "unit-001")]
    (is (map? unit)
      "Should return unit record")
    (is (= (:type unit) :locomotive-frame)
      "Should have correct unit type")
    (is (:verified? unit)
      "unit-001 should be verified")))

(deftest unit-verification-check
  "Unit verification status can be queried."
  (let [st (store/mem-store)]
    (is (store/unit-verified? st "unit-001")
      "unit-001 should be verified")
    (is (not (store/unit-verified? st "unit-002"))
      "unit-002 should not be verified")
    (is (not (store/unit-verified? st "unit-unknown"))
      "unknown unit should not be verified")))

(deftest component-lookup
  "Component records can be retrieved by ID."
  (let [st (store/mem-store)
        component (store/component st "brake-system-001")]
    (is (map? component)
      "Should return component record")
    (is (= (:type component) :air-brake-assembly)
      "Should have correct component type")))

(deftest component-verification-check
  "Component inspection status can be queried."
  (let [st (store/mem-store)]
    (is (store/component-verified? st "brake-system-001")
      "brake-system-001 should pass inspection")
    (is (not (store/component-verified? st "unknown-component"))
      "unknown component should not be verified")))

(deftest production-record-lookup
  "Production records can be retrieved by ID."
  (let [st (store/mem-store)
        record (store/production-record st "record-001")]
    (is (map? record)
      "Should return production record")
    (is (= (:unit-id record) "unit-001")
      "Should reference correct unit")))

(deftest defect-entry-lookup
  "Defect log entries can be retrieved by ID."
  (let [st (store/mem-store)
        defect (store/defect-entry st "defect-001")]
    (is (map? defect)
      "Should return defect entry")
    (is (= (:type defect) :weld-stress-crack)
      "Should have correct defect type")
    (is (:safety-critical? defect)
      "Should be marked safety-critical")))

(deftest defect-safety-critical-check
  "Defect safety-critical status can be queried."
  (let [st (store/mem-store)]
    (is (store/defect-is-safety-critical? st "defect-001")
      "defect-001 should be safety-critical")
    (is (not (store/defect-is-safety-critical? st "unknown-defect"))
      "unknown defect should not be safety-critical")))

(deftest missing-records
  "Missing records return nil-friendly values."
  (let [st (store/mem-store)]
    (is (nil? (store/unit st "missing-unit"))
      "Missing unit should return nil")
    (is (nil? (store/component st "missing-component"))
      "Missing component should return nil")
    (is (nil? (store/production-record st "missing-record"))
      "Missing record should return nil")
    (is (nil? (store/defect-entry st "missing-defect"))
      "Missing defect should return nil")))
