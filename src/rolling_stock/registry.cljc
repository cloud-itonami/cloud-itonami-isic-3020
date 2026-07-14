(ns rolling-stock.registry
  "Proposal registry and drafting helpers for rolling-stock manufacturing operations.
  Every proposal carries its spec-basis and evidence checklist.")

;; ----------------------------- hard invariants -----------------------------

(defn hard-invariant-violations
  "Hard invariants that CANNOT be overridden:
  - If operation affects manufacturing or safety, it must carry spec-basis.
  - This actor can never release or certify vehicles for service."
  [op-type value]
  (when (contains? #{:log-production-record :flag-safety-defect} op-type)
    (when (or (empty? (:cites value))
              (and (contains? value :spec-basis) (nil? (:spec-basis value))))
      [{:rule :no-spec-basis
        :detail "鉄道安全基準の公式引用が無い提案は処理できない"}])))

(defn protected-operation-violations
  "Operations that require human sign-off and can never be autonomous:
  - Safety defect flagging (always escalates)
  - Vehicle release review scheduling (only proposes, engineer decides)"
  [op-type]
  (when (contains? #{:flag-safety-defect :request-release-review} op-type)
    [{:rule :requires-human-approval
      :detail "この操作には人間の承認が必須"}]))

;; ----------------------------- proposal drafts -----------------------------

(defn production-record-draft
  "Draft a production-record logging proposal.
  subject: unit/component ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items"
  [subject cites evidence-checklist confidence detail]
  {:op :log-production-record
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn maintenance-draft
  "Draft an equipment maintenance scheduling proposal.
  subject: equipment ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items"
  [subject cites evidence-checklist confidence detail]
  {:op :schedule-maintenance
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn safety-defect-draft
  "Draft a safety-critical manufacturing defect flag proposal.
  subject: defect ID
  cites: spec-basis citations
  safety-critical?: boolean -- if true, ALWAYS escalates
  evidence-checklist: map of verified evidence items
  detail: narrative defect description"
  [subject cites safety-critical? evidence-checklist confidence detail]
  {:op :flag-safety-defect
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :safety-critical? safety-critical?
           :detail detail}})

(defn release-review-draft
  "Draft a release-review scheduling proposal.
  NOTE: This proposes scheduling the qualified engineer's review.
  The actor does NOT release the vehicle itself.
  subject: vehicle ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items"
  [subject cites evidence-checklist confidence detail]
  {:op :request-release-review
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})
