(ns rolling-stock.sim
  "Simulation harness for Rolling Stock Manufacturing Coordinator actor.
  Run with: clojure -M:dev:run"
  (:require [rolling-stock.advisor :as advisor]
            [rolling-stock.governor :as governor]
            [rolling-stock.store :as store]))

(defn -main
  "Drive a simple rolling-stock manufacturing workflow through the governor."
  [& _args]
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)

        ;; Scenario 1: Production record logging (clean proposal)
        record-proposal (advisor/production-record-proposal adv "unit-001")
        record-eval (governor/evaluate record-proposal st)

        ;; Scenario 2: Maintenance scheduling (clean proposal)
        maint-proposal (advisor/maintenance-proposal adv "brake-system-001")
        maint-eval (governor/evaluate maint-proposal st)

        ;; Scenario 3: Safety defect flagging (critical defect, hard escalation)
        defect-proposal (advisor/safety-defect-proposal adv "defect-001" true)
        defect-eval (governor/evaluate defect-proposal st)

        ;; Scenario 4: Release review request (proposes engineer review only)
        release-proposal (advisor/release-review-proposal adv "unit-001")
        release-eval (governor/evaluate release-proposal st)]

    (println "=== ROLLING STOCK MANUFACTURING COORDINATOR SIMULATION ===\n")

    (println "--- Scenario 1: Production Record Logging ---")
    (println "Proposal:" record-proposal)
    (println "Evaluation:" record-eval)
    (println "Result:" (if (:clean? record-eval) "APPROVED" "ESCALATE TO HUMAN"))
    (println)

    (println "--- Scenario 2: Maintenance Scheduling ---")
    (println "Proposal:" maint-proposal)
    (println "Evaluation:" maint-eval)
    (println "Result:" (if (:clean? maint-eval) "APPROVED" "ESCALATE TO HUMAN"))
    (println)

    (println "--- Scenario 3: Safety Defect Flagging (Critical) ---")
    (println "Proposal:" defect-proposal)
    (println "Evaluation:" defect-eval)
    (println "Hard Violations:" (:hard-violations defect-eval))
    (println "Result:" (if (:holds? defect-eval) "HARD BLOCK - Escalate immediately" "ERROR"))
    (println)

    (println "--- Scenario 4: Release Review Scheduling ---")
    (println "Proposal:" release-proposal)
    (println "Evaluation:" release-eval)
    (println "Result:" (if (seq (:soft-violations release-eval)) "ESCALATE - Engineer review required" "APPROVED"))
    (println)))
