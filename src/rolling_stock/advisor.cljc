(ns rolling-stock.advisor
  "Rolling Stock Manufacturing Advisor -- the LLM-driven suggestion layer.
  Proposes operations to the Governor for approval.")

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
