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
    "Should have France jurisdiction")
  (is (contains? facts/catalog :DEU)
    "Should have Germany jurisdiction")
  (is (contains? facts/catalog :KOR)
    "Should have South Korea jurisdiction"))

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

(deftest deu-requirements
  "Germany has a real but honestly narrower requirement set than
  JPN/USA/GBR -- vehicle-authorization (initial acceptance) and
  periodic-inspection only, per EBO §§ 3 and 32."
  (let [reqs (facts/requirement-citations :DEU)]
    (is (pos? (count reqs))
      "Germany should have requirements")
    (is (contains? reqs :vehicle-authorization)
      "Should require EBA/Land-authority vehicle acceptance")
    (is (contains? reqs :periodic-inspection)
      "Should require periodic recurring inspection")
    (is (not (contains? reqs :structural-integrity))
      "Should NOT claim a structural-integrity requirement that was not verified")
    (is (not (contains? reqs :braking-system))
      "Should NOT claim a braking-system requirement that was not verified")
    (is (every? :spec-basis (vals reqs))
      "Every requirement should have an official spec-basis citation")))

(deftest deu-evidence-satisfaction
  "Evidence checklist validation works correctly for Germany."
  (let [good-checklist {:eba-or-land-authority-acceptance true
                        :vehicle-acceptance-cert true
                        :periodic-inspection-record true
                        :inspection-interval-compliance true}
        bad-checklist {:eba-or-land-authority-acceptance true}]
    (is (facts/required-evidence-satisfied? :DEU good-checklist)
      "Should accept complete evidence checklist")
    (is (not (facts/required-evidence-satisfied? :DEU bad-checklist))
      "Should reject incomplete evidence checklist")))

(deftest kor-requirements
  "South Korea has a real but honestly narrower requirement set than
  JPN/USA/GBR -- design type-approval and completion inspection only,
  per Railroad Safety Act (철도안전법) Articles 26 and 26-6."
  (let [reqs (facts/requirement-citations :KOR)]
    (is (pos? (count reqs))
      "South Korea should have requirements")
    (is (contains? reqs :vehicle-authorization)
      "Should require MOLIT/TS rolling-stock type approval")
    (is (contains? reqs :completion-inspection)
      "Should require post-manufacture completion inspection")
    (is (not (contains? reqs :structural-integrity))
      "Should NOT claim a structural-integrity requirement that was not verified")
    (is (not (contains? reqs :braking-system))
      "Should NOT claim a braking-system requirement that was not verified")
    (is (every? :spec-basis (vals reqs))
      "Every requirement should have an official spec-basis citation")))

(deftest kor-evidence-satisfaction
  "Evidence checklist validation works correctly for South Korea."
  (let [good-checklist {:molit-type-approval true
                        :ts-type-approval-inspection true
                        :technical-standards-compliance true
                        :completion-inspection-cert true
                        :type-approval-conformance true}
        bad-checklist {:molit-type-approval true}]
    (is (facts/required-evidence-satisfied? :KOR good-checklist)
      "Should accept complete evidence checklist")
    (is (not (facts/required-evidence-satisfied? :KOR bad-checklist))
      "Should reject incomplete evidence checklist")))

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
