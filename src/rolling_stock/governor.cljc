(ns rolling-stock.governor
  "Rolling Stock Manufacturing Governor -- the independent safety layer that earns
  the Rolling Stock Operations Advisor the right to propose and log actions.
  The LLM has no notion of railway safety standards, crashworthiness,
  braking-system compliance, or when a manufacturing proposal represents
  a safety-critical decision, so this MUST be a separate system able to *reject*
  a proposal and fall back to HOLD.

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates MANUFACTURING OPERATIONS and SAFETY LOGGING around
  rolling-stock production. It does NOT:
    - Make welding/structural decisions (welder/engineer exclusive)
    - Approve component sub-assembly quality ratings
    - Release any locomotive or rail vehicle for service (qualified engineer only)
    - Certify safety compliance (safety certification is engineer exclusive)
    - Make braking-system assembly decisions (brake specialist exclusive)

  Those remain the exclusive authority of qualified engineers and rail-safety specialists.

  HARD violations (a human approver CANNOT override):
    1. Spec-basis       -- no official railway safety standard citation
    2. Safety defect flag -- ALWAYS escalates, never silently logged
    3. Vehicle release claim -- this actor NEVER releases or certifies
    4. Process-control operations -- NO welding/assembly/engineering decisions
                                     (those remain manufacturing-floor exclusive authority)

  SOFT violation (can be approved by human):
    5. Confidence floor / actuation gate -- low confidence

  Safety-critical manufacturing defects (weld integrity, braking systems, structural
  compliance) are hard escalations regardless of confidence."
  (:require [rolling-stock.store :as store]))

(def confidence-floor 0.6)

(def release-forbidden-keywords
  "Keywords that indicate vehicle release authority (FORBIDDEN for this actor).
  This actor proposes scheduling a qualified engineer's release review,
  but NEVER releases or certifies the vehicle itself."
  #{"release-for-service" "release" "certify-safety" "approved-for-service"
    "safety-certification" "pass-inspection" "cleared-for-operation"})

(def process-control-keywords
  "Keywords that indicate manufacturing-floor engineering authority (FORBIDDEN).
  If a proposal mentions welding parameters, assembly sequences, or engineering
  decisions, it's a hard block -- those are specialist/engineer exclusive."
  #{"welding" "weld" "solder" "braking-system-assembly" "brake-assembly"
    "structural-decision" "component-rating" "quality-certification"
    "assembly-parameters" "torque-spec" "fit-tolerance"})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal affecting manufacturing or safety must cite official railway
  safety standard. HARD violation -- never invent requirements."
  [proposal _st]
  (let [op (:op proposal)]
    (when (contains? #{:log-production-record :flag-safety-defect} op)
      (when (or (empty? (:cites proposal))
                (and (contains? (:value proposal) :spec-basis)
                     (nil? (:spec-basis (:value proposal)))))
        [{:rule :no-spec-basis
          :detail "鉄道安全基準の公式引用が無い提案は処理できない"}]))))

(defn- release-forbidden-violations
  "HARD BLOCK: This actor NEVER releases or certifies a vehicle for service.
  Release and safety certification are qualified engineer exclusive authority.
  If a proposal claims to release or certify, reject it immediately."
  [proposal _st]
  (let [detail (str (:detail (:value proposal)) " " (:op proposal))
        words (re-seq #"\w+" (.toLowerCase detail))
        forbidden (some #(contains? release-forbidden-keywords %) words)]
    (when forbidden
      [{:rule :release-forbidden
        :detail (str "車両の運行認可と安全認証は認可エンジニアの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- process-control-block-violations
  "HARD BLOCK: This actor does NOT make manufacturing-floor engineering decisions.
  Welding parameters, assembly sequences, braking-system assembly, structural
  decisions, and component ratings are specialist/engineer exclusive.
  If a proposal mentions these, reject it immediately."
  [proposal _st]
  (let [detail (str (:detail (:value proposal)) " " (:op proposal))
        words (re-seq #"\w+" (.toLowerCase detail))
        forbidden (some #(contains? process-control-keywords %) words)]
    (when forbidden
      [{:rule :process-control-forbidden
        :detail (str "製造現場の工学的決定は認可エンジニアの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- safety-defect-escalation-violations
  "Safety-critical manufacturing defects (weld integrity, braking systems,
  structural compliance) MUST escalate to human. Never silently log a defect."
  [{:keys [op]} {:keys [safety-critical?]}]
  (when (and (= op :flag-safety-defect) safety-critical?)
    [{:rule :safety-defect-escalation
      :detail "安全関連の製造欠陥は必ず人間にエスカレートされる"}]))

(defn- unit-record-verification-violations
  "Production-record logging requires verified unit/component record."
  [{:keys [op subject]} st]
  (when (= op :log-production-record)
    (when-not (store/unit-verified? st subject)
      [{:rule :unit-not-verified
        :detail "ユニット/部品の記録が未検証"}])))

(defn- confidence-gate-violations
  "Low confidence or safety defect flag -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (= op :flag-safety-defect))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "安全欠陥は人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [spec-basis-violations
                           release-forbidden-violations
                           process-control-block-violations
                           unit-record-verification-violations]
        hard-checks-value [safety-defect-escalation-violations]
        soft-checks [confidence-gate-violations]
        hard-violations-store (mapcat #(% proposal st) hard-checks-store)
        hard-violations-value (mapcat #(% proposal (:value proposal)) hard-checks-value)
        hard-violations (concat hard-violations-store hard-violations-value)
        soft-violations (mapcat #(% proposal (:value proposal)) soft-checks)]
    {:holds? (seq hard-violations)
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
