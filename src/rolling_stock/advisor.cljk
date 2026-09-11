(ns rolling-stock.advisor
  "Rolling Stock Manufacturing Advisor -- the LLM-driven suggestion layer.
  Proposes rolling-stock manufacturing coordination operations to the
  Governor for censorship. Has NO direct authority: every proposal below
  carries `:effect :propose` and is always routed through
  `rolling-stock.governor/evaluate` before anything commits (wired as the
  `:advise` node of `rolling-stock.operation/build`'s compiled StateGraph).

  PRIOR BUG (fixed here): the four proposal-builders below already
  existed and were each individually correct, but nothing wired them
  into a real graph/governor/store round-trip -- `rolling-stock.sim`
  called `governor/evaluate` directly against a hand-built proposal
  literal, bypassing any real advisor dispatch AND any langgraph-clj
  StateGraph entirely (there was no `operation.cljc`, no `g/state-graph`
  call anywhere in this repo). Kept as PLAIN FUNCTIONS (not a
  `defprotocol`) -- wiring a real advisor into a real compiled graph node
  does not require a protocol here, each op already has its own builder.
  `propose` below is the new piece: a dispatcher (partial-applied over
  `advisor`) that IS the plain 1-arg fn of `request` -> proposal that
  `rolling-stock.operation/build`'s `:advisor` opt accepts -- swap it
  wholesale to point at a real LLM backend without touching the graph
  wiring, or inject a test double directly as `:advisor` in `build`."
  )

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

(defn production-record-proposal
  "Propose a production-record logging operation for a manufactured unit."
  [_advisor unit-id]
  {:op :log-production-record
   :subject unit-id
   :effect :propose
   :cites ["Railway Technical Standards (鉄道技術基準) §4.2"]
   :value {:evidence {:unit-verified true :inspection-pass true :documentation-complete true}
           :confidence 0.87
           :detail "Production record for unit completion and quality logging"}})

(defn maintenance-proposal
  "Propose an equipment maintenance scheduling operation."
  [_advisor equipment-id]
  {:op :schedule-maintenance
   :subject equipment-id
   :effect :propose
   :cites ["Railway Maintenance Safety Standard (鉄道保守安全基準) §5"]
   :value {:evidence {:maintenance-records-available true :compliance-check-pass true}
           :confidence 0.85
           :detail "Routine equipment maintenance scheduling"}})

(defn safety-defect-proposal
  "Propose flagging a safety-critical manufacturing defect.
  CRITICAL: This is always an escalation, never silently logged.
  safety-critical?: boolean indicating if defect affects safety-of-life."
  [_advisor defect-id safety-critical?]
  {:op :flag-safety-defect
   :subject defect-id
   :effect :propose
   :cites ["Railway Safety Regulation (鉄道安全規則) §1.3"]
   :value {:evidence {:defect-confirmed true :photographic-evidence true}
           :confidence 0.9
           :safety-critical? safety-critical?
           :detail (if safety-critical?
                    "SAFETY DEFECT: Weld integrity issue in structural frame, affects crashworthiness"
                    "Non-critical manufacturing defect detected during QA")}})

(defn release-review-proposal
  "Propose scheduling a qualified engineer's release-review for a completed vehicle.
  NOTE: This actor does NOT release the vehicle -- only proposes to schedule
  the qualified engineer's review."
  [_advisor vehicle-id]
  {:op :request-release-review
   :subject vehicle-id
   :effect :propose
   :cites ["Railway Operational Safety Directive (鉄道運用安全指令) §2.1"]
   :value {:evidence {:manufacturing-complete true :all-inspections-pass true}
           :confidence 0.88
           :detail "Request scheduling of qualified engineer release review for vehicle"}})

;; ----------------------------- dispatch seam -----------------------------

(defn propose
  "Dispatch a request map ({:op ... :subject ... ...}) to the matching
  domain-specific proposal builder above -- this IS the advisor's
  `:advise` node behavior once wired into
  `rolling-stock.operation/build`'s compiled StateGraph."
  [advisor request]
  (case (:op request)
    :log-production-record (production-record-proposal advisor (:subject request))
    :schedule-maintenance   (maintenance-proposal advisor (:subject request))
    :flag-safety-defect     (safety-defect-proposal advisor (:subject request)
                                                     (boolean (:safety-critical? request)))
    :request-release-review (release-review-proposal advisor (:subject request))
    ;; fallback -- unrecognized op. The Governor's independent checks
    ;; (spec-basis / release-forbidden / process-control / confidence
    ;; gate) censor this regardless of what the advisor proposes; an
    ;; unrecognized op simply carries no evidence and zero confidence.
    {:op (:op request)
     :subject (:subject request)
     :effect :propose
     :cites []
     :value {:evidence {}
             :confidence 0.0
             :detail (str "Operation not recognized: " (:op request))}}))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's later approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :subject (:subject request)
   :proposal-detail (get-in proposal [:value :detail])
   :confidence (get-in proposal [:value :confidence])})
