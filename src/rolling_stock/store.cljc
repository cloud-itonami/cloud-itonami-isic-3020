(ns rolling-stock.store
  "In-memory store for rolling-stock manufacturing operations state.
  This is a reference implementation; production systems would use Datomic
  or similar persistent event store for audit and replay.")

;; ----------------------------- store initialization -----------------------------

(defn mem-store
  "Create an in-memory store with reference rolling-stock manufacturing data."
  []
  {:data (atom {
           :units {
             "unit-001" {:type :locomotive-frame
                        :line-id "mainline-2026-07"
                        :verified? true
                        :jurisdiction :JPN}
             "unit-002" {:type :passenger-car-body
                        :line-id "express-2026-07"
                        :verified? false
                        :jurisdiction :JPN}}
           :components {
             "brake-system-001" {:type :air-brake-assembly
                                :unit-id "unit-001"
                                :inspection-pass? true}
             "wheel-axle-001" {:type :wheel-axle-set
                              :unit-id "unit-001"
                              :quality-grade "precision-grade-A"
                              :verified? true}}
           :production-records {
             "record-001" {:unit-id "unit-001"
                          :date "2026-07-14"
                          :status :completed
                          :inspections-pass? true}}
           :defect-log {
             "defect-001" {:unit-id "unit-002"
                          :date "2026-07-14"
                          :type :weld-stress-crack
                          :severity :critical
                          :safety-critical? true}}})})

;; ----------------------------- accessors -----------------------------

(defn unit
  "Get unit/component record by ID."
  [st unit-id]
  (get-in @(:data st) [:units unit-id]))

(defn component
  "Get component record by ID."
  [st component-id]
  (get-in @(:data st) [:components component-id]))

(defn production-record
  "Get production record by ID."
  [st record-id]
  (get-in @(:data st) [:production-records record-id]))

(defn defect-entry
  "Get defect log entry by ID."
  [st defect-id]
  (get-in @(:data st) [:defect-log defect-id]))

;; ----------------------------- guards -----------------------------

(defn unit-verified?
  "Check if unit record is verified and registered."
  [st unit-id]
  (let [u (unit st unit-id)]
    (:verified? u false)))

(defn component-verified?
  "Check if component inspection passed."
  [st component-id]
  (let [c (component st component-id)]
    (:inspection-pass? c false)))

(defn defect-is-safety-critical?
  "Check if a defect entry is marked safety-critical."
  [st defect-id]
  (let [d (defect-entry st defect-id)]
    (:safety-critical? d false)))
