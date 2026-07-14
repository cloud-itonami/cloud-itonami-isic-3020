(ns rolling-stock.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [rolling-stock.store :as store]
            [rolling-stock.advisor :as advisor]
            [rolling-stock.governor :as governor]
            [rolling-stock.registry :as registry]))

(deftest spec-basis-hard-gate
  "Spec-basis is a HARD gate: never allow proposals without official citations."
  (let [st (store/mem-store)
        proposal {:op :log-production-record
                  :subject "unit-001"
                  :effect :propose
                  :value {:evidence {:unit-verified true}
                          :confidence 0.9}
                  :cites []}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with empty cites should hold")
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :no-spec-basis) (:hard-violations eval))))))

(deftest release-forbidden-block
  "HARD BLOCK: Proposals claiming to release or certify vehicles are immediately rejected.
  Release and safety certification are engineer exclusive authority."
  (let [st (store/mem-store)
        proposal {:op :log-production-record
                  :subject "unit-001"
                  :effect :propose
                  :cites ["Railway Technical Standards §4.2"]
                  :value {:evidence {:unit-verified true}
                          :confidence 0.95
                          :detail "Release this locomotive for service tomorrow"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Release claim should hold")
      (is (some #(= (:rule %) :release-forbidden) (:hard-violations eval))
        "Should have release-forbidden violation"))))

(deftest process-control-block
  "HARD BLOCK: Proposals mentioning welding, assembly, or engineering decisions
  are immediately rejected. Those remain specialist/engineer exclusive authority."
  (let [st (store/mem-store)
        proposal {:op :log-production-record
                  :subject "unit-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:unit-verified true}
                          :confidence 0.9
                          :detail "Set welding parameters to 900 amps and assemble brake system"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Process-control proposal should hold")
      (is (some #(= (:rule %) :process-control-forbidden) (:hard-violations eval))
        "Should have process-control-forbidden violation"))))

(deftest safety-defect-escalation
  "Safety-critical manufacturing defects ALWAYS escalate to human.
  Never silently log a safety-critical defect."
  (let [st (store/mem-store)
        proposal {:op :flag-safety-defect
                  :subject "defect-001"
                  :effect :propose
                  :cites ["Railway Safety Regulation §1.3"]
                  :value {:evidence {:defect-confirmed true}
                          :confidence 0.95
                          :safety-critical? true
                          :detail "Weld integrity issue in structural frame"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Safety defect should hold")
      (is (some #(= (:rule %) :safety-defect-escalation) (:hard-violations eval))
        "Should have safety-defect-escalation violation"))))

(deftest flag-defect-without-safety-critical
  "Non-critical defects may escalate on confidence, but not hard-blocked."
  (let [st (store/mem-store)
        proposal {:op :flag-safety-defect
                  :subject "defect-002"
                  :effect :propose
                  :cites ["Railway Technical Standards §4.2"]
                  :value {:evidence {:defect-confirmed true}
                          :confidence 0.85
                          :safety-critical? false
                          :detail "Minor cosmetic defect on body panel"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (not (:holds? eval)) "Non-critical defect should not hard-block")
      (is (seq (:soft-violations eval)) "Should have soft violations on confidence gate"))))

(deftest unit-not-verified-blocks-record
  "Production-record logging with unverified unit is blocked."
  (let [st (store/mem-store)
        proposal (registry/production-record-draft "unit-002"
                   ["Railway Technical Standards §4.2"]
                   {:unit-verified true}
                   0.88
                   "Log production completion")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :unit-not-verified) (:hard-violations eval))
        "Should block unverified unit"))))

(deftest safety-defect-always-escalates
  "Safety defect flagging always escalates to human, regardless of confidence."
  (let [st (store/mem-store)
        proposal {:op :flag-safety-defect
                  :subject "defect-001"
                  :effect :propose
                  :cites ["Railway Safety Regulation §1.3"]
                  :value {:evidence {:defect-confirmed true}
                          :confidence 0.99
                          :safety-critical? false
                          :detail "Minor defect"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should always escalate safety defect"))))

(deftest clean-production-record
  "A proposal with all evidence, valid spec-basis, high confidence,
  and no safety defects is clean."
  (let [st (store/mem-store)
        proposal {:op :log-production-record
                  :subject "unit-001"
                  :effect :propose
                  :cites ["Railway Technical Standards §4.2"]
                  :value {:evidence {:unit-verified true :inspection-pass true}
                          :confidence 0.92
                          :detail "Production record for unit completion"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations"))))

(deftest maintenance-proposal-clean
  "Routine maintenance proposal with verified equipment and high confidence is clean."
  (let [st (store/mem-store)
        proposal {:op :schedule-maintenance
                  :subject "equipment-001"
                  :effect :propose
                  :cites ["Railway Maintenance Safety Standard §5"]
                  :value {:evidence {:maintenance-records-available true :compliance-check-pass true}
                          :confidence 0.90
                          :detail "Routine equipment maintenance scheduling"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (empty? (:hard-violations eval)) "Should have no hard violations"))))
