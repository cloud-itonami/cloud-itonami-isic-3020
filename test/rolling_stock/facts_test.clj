(ns rolling-stock.facts-test
  (:require [clojure.test :refer [deftest is]]
            [rolling-stock.facts :as facts]))

(deftest catalog-exists
  "Catalog has entry for each implemented jurisdiction."
  (is (pos? (count facts/catalog))
    "Catalog should not be empty")
  (is (contains? facts/catalog :JPN)
    "Should have Japan jurisdiction")
  (is (contains? facts/catalog :USA)
    "Should have USA jurisdiction")
  (is (contains? facts/catalog :GBR)
    "Should have UK jurisdiction")
  (is (contains? facts/catalog :FRA)
    "Should have France jurisdiction"))

(deftest fra-requirements
  "France has a real but honestly narrower requirement set than
  JPN/USA/GBR -- vehicle-authorization only."
  (let [reqs (facts/requirement-citations :FRA)]
    (is (pos? (count reqs))
      "France should have requirements")
    (is (contains? reqs :vehicle-authorization)
      "Should require EPSF/ERA vehicle authorization")
    (is (not (contains? reqs :structural-integrity))
      "Should NOT claim a structural-integrity requirement that was not verified")
    (is (every? :spec-basis (vals reqs))
      "Every requirement should have an official spec-basis citation")))

(deftest japan-requirements
  "Japan rolling-stock manufacturing requirements are properly specified."
  (let [reqs (facts/requirement-citations :JPN)]
    (is (pos? (count reqs))
      "Japan should have requirements")
    (is (contains? reqs :structural-integrity)
      "Should require structural integrity")
    (is (contains? reqs :braking-system)
      "Should require braking system")
    (is (contains? reqs :electrical-safety)
      "Should require electrical safety")
    (is (contains? reqs :wheel-and-axle)
      "Should require wheel and axle standards")))

(deftest evidence-satisfaction
  "Evidence checklist validation works correctly."
  (let [good-checklist {:weld-inspection-cert true
                        :frame-stress-analysis true
                        :material-cert true
                        :braking-component-cert true
                        :integration-test-pass true
                        :failsafe-verification true
                        :electrical-inspection true
                        :grounding-verification true
                        :short-circuit-test true
                        :wheel-inspection-cert true
                        :axle-material-cert true
                        :rolling-quality-test true}
        bad-checklist {:weld-inspection-cert false}]
    (is (facts/required-evidence-satisfied? :JPN good-checklist)
      "Should accept complete evidence checklist")
    (is (not (facts/required-evidence-satisfied? :JPN bad-checklist))
      "Should reject incomplete evidence checklist")))

(deftest coverage-reporting
  "Coverage report provides honest jurisdictional scope."
  (let [cov (facts/coverage)]
    (is (pos? (:implemented cov))
      "Should report implementation count")
    (is (> (:worldwide-jurisdictions cov) (:implemented cov))
      "Worldwide count should exceed implemented")
    (is (pos? (:coverage-pct cov))
      "Coverage percentage should be positive")
    (is (< (:coverage-pct cov) 100.0)
      "Coverage should be honest, not 100%")
    (is (string? (:note cov))
      "Should include scope note")))

(deftest requirement-spec-basis
  "Each jurisdiction's requirements cite official spec-basis."
  (doseq [[jurisdiction reqs] (facts/requirement-citations :JPN)]
    (is (:spec-basis reqs)
      (str "Requirement " jurisdiction " should have spec-basis"))))

(deftest usa-requirements
  "USA rolling-stock manufacturing requirements are properly specified."
  (let [reqs (facts/requirement-citations :USA)]
    (is (pos? (count reqs))
      "USA should have requirements")
    (is (string? (get-in reqs [:structural-integrity :spec-basis]))
      "Should cite FRA standards for structure")
    (is (string? (get-in reqs [:braking-system :spec-basis]))
      "Should cite FRA standards for braking")))

(deftest gbr-requirements
  "UK rolling-stock manufacturing requirements are properly specified."
  (let [reqs (facts/requirement-citations :GBR)]
    (is (pos? (count reqs))
      "UK should have requirements")
    (is (string? (get-in reqs [:driver-cab :spec-basis]))
      "Should cite RSSB standards for cab design")))
