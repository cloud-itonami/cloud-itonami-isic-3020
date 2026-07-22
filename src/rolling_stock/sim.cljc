(ns rolling-stock.sim
  "Simulation harness for the Rolling Stock Manufacturing Coordinator
  actor. Run with: clojure -M:dev:run

  PRIOR BUG (fixed here): this demo used to hand-wire
  `rolling-stock.advisor`'s proposal functions straight into
  `rolling-stock.governor/evaluate` and print the returned verdict --
  no `rolling-stock.operation/build`, no compiled StateGraph, no ledger.
  It now drives the SAME kind of scenarios through the real compiled
  graph (`operation/build`), including a full escalate -> human-approve
  / human-reject round trip via checkpoint-based resume, and prints the
  resulting append-only audit ledger at the end. Mirrors `pastaops.sim`
  (cloud-itonami-isic-1074) / `transportops.sim` (cloud-itonami-isic-869)."
  (:require [langgraph.graph :as g]
            [rolling-stock.operation :as operation]
            [rolling-stock.store :as store]))

(def qc-inspector {:actor-id "qc-inspector-01" :role :quality-inspector})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "qc-inspector-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "qc-inspector-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path
  (:schedule-maintenance, clean/low-stakes), a clean-evidence commit
  path (:log-production-record on a verified unit), an escalate ->
  reject path (:flag-safety-defect on a non-critical defect -- ALWAYS
  escalates per the governor's confidence gate, regardless of severity),
  and two hard-hold paths (:flag-safety-defect on a CRITICAL defect,
  and :log-production-record against an UNVERIFIED unit); print each
  result and the final audit ledger."
  []
  (let [st (store/mem-store)
        actor (operation/build st)]

    (println "=== Rolling Stock Manufacturing Coordinator Demo ===")

    (println "\n== schedule-maintenance brake-system-001 (governor-clean, low-stakes -> commit) ==")
    (println (exec-op actor "t1"
                      {:op :schedule-maintenance :subject "brake-system-001"}
                      qc-inspector))

    (println "\n== log-production-record unit-001 (verified unit, clean -> commit) ==")
    (println (exec-op actor "t2"
                      {:op :log-production-record :subject "unit-001"}
                      qc-inspector))

    (println "\n== flag-safety-defect defect-002 (non-critical -- ALWAYS escalates -- inspector approves) ==")
    (let [r (exec-op actor "t3"
                     {:op :flag-safety-defect :subject "defect-002" :safety-critical? false}
                     qc-inspector)]
      (println r)
      (println "-- QC inspector approves (confirmed genuine but non-critical) --")
      (println (approve! actor "t3")))

    (println "\n== flag-safety-defect defect-004 (non-critical -- ALWAYS escalates -- inspector rejects) ==")
    (let [r (exec-op actor "t3b"
                     {:op :flag-safety-defect :subject "defect-004" :safety-critical? false}
                     qc-inspector)]
      (println r)
      (println "-- QC inspector rejects (insufficient evidence to confirm/dismiss) --")
      (println (reject! actor "t3b")))

    (println "\n== flag-safety-defect defect-003 (critical -- HARD hold, no interrupt) ==")
    (println (exec-op actor "t4"
                      {:op :flag-safety-defect :subject "defect-003" :safety-critical? true}
                      qc-inspector))

    (println "\n== log-production-record unit-002 (UNVERIFIED unit -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t5"
                      {:op :log-production-record :subject "unit-002"}
                      qc-inspector))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo))
