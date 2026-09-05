(ns rolling-stock.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: there was previously no
  demo page and no generator at all. This namespace drives the REAL actor
  stack -- `rolling-stock.operation/build` (a genuinely compiled
  langgraph-clj StateGraph) -> `rolling-stock.governor/evaluate` ->
  `rolling-stock.store` -- and renders the resulting store and its
  append-only audit ledger. Nothing on the page is typed in by hand: every
  id, entity attribute, disposition, hold rule and ledger row below is read
  back out of the store after a real run.

  WHY THIS SCENARIO IS NOT `rolling-stock.sim`'s
  ----------------------------------------------
  This repo's own demo driver (`clojure -M:dev:run`) proposes against
  `defect-002`, `defect-003` and `defect-004`, and NONE of those three
  exist in `rolling-stock.store/mem-store` -- the seed's `:defect-log` has
  exactly one entry, `defect-001`. Those runs still execute real code, but
  they execute it against subject ids the store has never heard of, so the
  page they would produce would be about fabricated inventory. The scenario
  in `run-demo!` below was therefore authored from scratch and uses ONLY
  ids that `store/mem-store` actually seeds:

    unit-001          :units             verified, :locomotive-frame, JPN
    unit-002          :units             UNVERIFIED, :passenger-car-body
    brake-system-001  :components        :air-brake-assembly, inspection pass
    wheel-axle-001    :components        :wheel-axle-set, precision-grade-A
    defect-001        :defect-log        :weld-stress-crack, safety-critical
    record-001        :production-records (rendered; no op targets it)

  WHAT EACH SUBJECT EXERCISES (all verified by running the graph)
  --------------------------------------------------------------
    unit-001         :log-production-record  -> clean auto-commit. Verified
                     unit + cited spec-basis + confidence 0.87 -> the
                     governor returns `:clean? true` and the graph commits
                     with no human in the loop.
    brake-system-001 :schedule-maintenance   -> clean auto-commit (the
                     low-stakes path: no spec-basis gate, no verification
                     gate, confidence 0.85).
    defect-001       :flag-safety-defect (non-critical) -> SOFT `:escalate`
                     -> `interrupt-before :request-approval` -> inspector
                     APPROVES -> commits. A safety-defect flag always trips
                     the confidence gate regardless of severity, so this is
                     the full human-in-the-loop lifecycle.
    wheel-axle-001   :flag-safety-defect (non-critical) -> SOFT `:escalate`
                     -> inspector REJECTS -> `:approval-rejected` hold. The
                     same gate, resolved the other way.
    defect-001       :flag-safety-defect (safety-critical) -> HARD hold on
                     TWO rules at once: `:process-control-forbidden` (the
                     advisor's own critical-defect narrative says \"Weld
                     integrity issue\", and `weld` is a manufacturing-floor
                     keyword this actor may never decide on) and
                     `:safety-defect-escalation`.
    unit-002         :log-production-record  -> HARD hold
                     `:unit-not-verified` (the seed marks it `:verified?
                     false`).
    unit-001         :request-release-review -> HARD hold
                     `:release-forbidden`. Worth reading twice: the op is
                     *meant* to be the safe one (schedule a qualified
                     engineer's review rather than release the vehicle),
                     but `governor/release-forbidden-violations` tokenizes
                     the proposal with `#\"\\w+\"` and `release` is a bare
                     forbidden keyword, so the word inside \"release
                     review\" trips the gate. The page reports what the
                     governor actually did, not what the op was named to
                     do.

  Three distinct HARD rules therefore appear on the page
  (`:process-control-forbidden`, `:unit-not-verified`,
  `:release-forbidden`), none of them staged -- each is the real verdict
  for a real seeded subject.

  LEDGER FACT TYPES
  -----------------
  `store/append-ledger!` is called from exactly two node handlers in
  `rolling-stock.operation/build`: `:commit` and `:hold`. So the only fact
  types that ever reach the ledger are `:committed`, `:governor-hold` and
  `:approval-rejected`. `:approval-granted` and `:approval-requested` are
  written to the graph's in-memory `:audit` channel ONLY and never land in
  the store -- `status-cell` below therefore does not branch on them
  (branching on `:approval-granted` would be dead code).

  DETERMINISM
  -----------
  No timestamps and no generated ids appear in the output. Entity tables
  are `sort-by`-ed on their ids rather than relying on map iteration order,
  and the ledger is rendered in append order. Two consecutive runs are
  byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [rolling-stock.operation :as operation]
            [rolling-stock.store :as store]))

;; ----------------------------- the real run -----------------------------

(def ^:private qc-inspector
  {:actor-id "qc-inspector-01" :role :quality-inspector})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context qc-inspector} {:thread-id tid}))

(defn- resolve! [actor tid status]
  (g/run* actor {:approval {:status status :by "qc-inspector-01"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh `store/mem-store` through the compiled StateGraph over the
  seeded-subject scenario documented in this namespace's docstring, and
  returns the store. Every field `render` reads afterwards is real
  governor/store output."
  []
  (let [st (store/mem-store)
        actor (operation/build st)]

    ;; clean auto-commits (governor :clean? true, no human involved)
    (exec! actor "r1" {:op :log-production-record :subject "unit-001"})
    (exec! actor "r2" {:op :schedule-maintenance :subject "brake-system-001"})

    ;; soft escalation -> human approves -> commits
    (exec! actor "r3" {:op :flag-safety-defect :subject "defect-001"
                       :safety-critical? false})
    (resolve! actor "r3" :approved)

    ;; soft escalation -> human rejects -> :approval-rejected hold
    (exec! actor "r4" {:op :flag-safety-defect :subject "wheel-axle-001"
                       :safety-critical? false})
    (resolve! actor "r4" :rejected)

    ;; HARD holds -- these never reach a human at all
    (exec! actor "r5" {:op :flag-safety-defect :subject "defect-001"
                       :safety-critical? true})
    (exec! actor "r6" {:op :log-production-record :subject "unit-002"})
    (exec! actor "r7" {:op :request-release-review :subject "unit-001"})
    st))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw
  "Render a keyword/string domain value without its leading colon."
  [v]
  (if (nil? v) "" (esc (if (keyword? v) (name v) v))))

(defn- yes-no
  "Boolean domain flags, rendered with the governor's own colour vocabulary."
  [v yes-class yes-label no-class no-label]
  (if v
    (str "<span class=\"" yes-class "\">" yes-label "</span>")
    (str "<span class=\"" no-class "\">" no-label "</span>")))

(defn- entities
  "Read one seeded collection out of the store's data atom, sorted by id so
  the output cannot depend on map iteration order. `store/mem-store`'s
  documented shape is `{:ledger atom :data atom}`; the per-id accessors
  (`store/unit` etc.) can only answer for an id you already know, and the
  point here is to render whatever the seed actually holds."
  [st k]
  (sort-by key (get @(:data st) k)))

(defn- facts-for
  "Every ledger fact recorded against `subject-id`, in append order."
  [ledger subject-id]
  (filter #(= subject-id (:subject %)) ledger))

(defn- status-cell
  "The subject's most recent ledger fact, rendered. Branches ONLY on the
  three fact types `operation/build` actually appends (see ns docstring)."
  [ledger subject-id]
  (let [f (last (facts-for ledger subject-id))]
    (case (:t f)
      nil "<span class=\"muted\">no activity this run</span>"
      :committed "<span class=\"ok\">committed</span>"
      :governor-hold
      (str "<span class=\"critical\">HARD hold &middot; "
           (str/join ", " (map kw (:basis f))) "</span>")
      :approval-rejected
      (str "<span class=\"warn\">approval rejected &middot; "
           (str/join ", " (map kw (:basis f))) "</span>")
      "<span class=\"muted\">in progress</span>")))

(defn- ops-cell
  "Which ops this run actually put through the governor for this subject."
  [ledger subject-id]
  (let [ops (distinct (map :op (facts-for ledger subject-id)))]
    (if (seq ops)
      (str/join " " (map #(str "<code>" (kw %) "</code>") ops))
      "<span class=\"muted\">&mdash;</span>")))

(defn- unit-row [ledger [id {:keys [type line-id jurisdiction verified?]}]]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (kw type) (esc line-id) (kw jurisdiction)
          (yes-no verified? "ok" "verified" "critical" "UNVERIFIED")
          (ops-cell ledger id) (status-cell ledger id)))

(defn- component-row [ledger [id {:keys [type unit-id inspection-pass? quality-grade verified?]}]]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (kw type) (esc unit-id)
          (if (nil? inspection-pass?)
            "<span class=\"muted\">n/a</span>"
            (yes-no inspection-pass? "ok" "inspection pass" "critical" "inspection FAIL"))
          (if quality-grade (esc quality-grade) "<span class=\"muted\">&mdash;</span>")
          (ops-cell ledger id) (status-cell ledger id)))

(defn- production-record-row [ledger [id {:keys [unit-id date status inspections-pass?]}]]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc unit-id) (esc date) (kw status)
          (yes-no inspections-pass? "ok" "inspections pass" "critical" "inspections FAIL")
          (status-cell ledger id)))

(defn- defect-row [ledger [id {:keys [unit-id date type severity safety-critical?]}]]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc unit-id) (esc date) (kw type)
          (if (= :critical severity)
            (str "<span class=\"critical\">" (kw severity) "</span>")
            (kw severity))
          (yes-no safety-critical? "critical" "safety-critical" "muted" "not safety-critical")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis summary violations]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (case t
            :committed "<span class=\"ok\">committed</span>"
            :governor-hold "<span class=\"critical\">governor-hold</span>"
            :approval-rejected "<span class=\"warn\">approval-rejected</span>"
            (kw t))
          (kw op) (esc subject)
          (str/join ", " (map kw basis))
          ;; committed facts carry a human summary; holds carry the rules
          ;; the governor fired. Both come straight off the ledger fact.
          (cond
            summary (esc summary)
            (seq violations) (str/join "; " (map #(str (kw (:rule %))) violations))
            :else (kw disposition))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own FIXED op/gate contract, read off
  ;; `rolling-stock.governor` (`spec-basis-violations`,
  ;; `release-forbidden-violations`, `process-control-block-violations`,
  ;; `unit-record-verification-violations`, `confidence-gate-violations`)
  ;; and `rolling-stock.advisor`'s four proposal builders. This is
  ;; documentation-of-code -- the actor's contract does not vary per run --
  ;; and is the ONLY hand-written content on the page. Everything else is
  ;; derived from the live run.
  ["        <tr><td><code>:log-production-record</code></td><td><span class=\"ok\">auto-commit when the unit record is verified and a spec-basis is cited</span></td><td>HARD <code>:no-spec-basis</code>, HARD <code>:unit-not-verified</code></td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"ok\">auto-commit &middot; low-stakes, no verification gate</span></td><td>SOFT <code>:escalate</code> below confidence 0.6</td></tr>"
   "        <tr><td><code>:flag-safety-defect</code></td><td><span class=\"warn\">ALWAYS escalates &middot; a defect flag is never silently logged at any confidence</span></td><td>SOFT <code>:escalate</code> always; HARD <code>:safety-defect-escalation</code> when safety-critical</td></tr>"
   "        <tr><td><code>:request-release-review</code></td><td><span class=\"critical\">this actor never releases or certifies a vehicle</span></td><td>HARD <code>:release-forbidden</code></td></tr>"
   "        <tr><td><em>any op</em></td><td><span class=\"critical\">manufacturing-floor engineering decisions are engineer-exclusive</span></td><td>HARD <code>:process-control-forbidden</code> on welding/assembly wording</td></tr>"])

(defn- section [title lede headers rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" % "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

(defn render
  "Renders the operator console from a store `db` that has already been
  driven by `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        holds (filter #(= :governor-hold (:t %)) ledger)
        hard-rules (distinct (mapcat :basis holds))]
    (str
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-3020 &middot; railway rolling-stock manufacturing</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Railway locomotive &amp; rolling-stock manufacturing (ISIC 3020) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · release &amp; certification are engineer-exclusive</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"banner\">\n"
     "    <p>Build-time generated from the real actor stack by <code>rolling-stock.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>): every row below was produced by running "
     "<code>rolling-stock.operation</code>'s compiled langgraph-clj StateGraph over "
     "<code>rolling-stock.store/mem-store</code>'s seeded inventory, through the independent "
     "Rolling Stock Manufacturing Governor. Nothing here is hand-written except the fixed op/gate "
     "contract table.</p>\n"
     "    <p>This run produced <strong>" (count ledger) "</strong> audit-ledger facts, of which "
     "<strong>" (count holds) "</strong> are HARD governor holds spanning "
     (count hard-rules) " distinct rules ("
     (str/join ", " (map #(str "<code>" (kw %) "</code>") hard-rules))
     ").</p>\n"
     "  </section>\n"

     (section "Units under manufacture"
              (str "Seeded rolling-stock units. <code>UNVERIFIED</code> is the store's own "
                   "<code>:verified?</code> flag &mdash; it is what makes a production-record "
                   "proposal a HARD hold rather than a commit.")
              ["Unit" "Type" "Production line" "Jurisdiction" "Record" "Ops this run" "Last governor outcome"]
              (map (partial unit-row ledger) (entities db :units)))

     (section "Components"
              "Seeded sub-assemblies, each bound to the unit it belongs to."
              ["Component" "Type" "Unit" "Inspection" "Quality grade" "Ops this run" "Last governor outcome"]
              (map (partial component-row ledger) (entities db :components)))

     (section "Production records"
              "Completed production records held in the store."
              ["Record" "Unit" "Date" "Status" "Inspections" "Last governor outcome"]
              (map (partial production-record-row ledger) (entities db :production-records)))

     (section "Defect log"
              (str "Manufacturing defects. A safety-critical defect can never be "
                   "silently logged: the governor holds it hard.")
              ["Defect" "Unit" "Date" "Type" "Severity" "Safety" "Last governor outcome"]
              (map (partial defect-row ledger) (entities db :defect-log)))

     (section "Action gate (Rolling Stock Manufacturing Governor)"
              (str "The actor's fixed contract, described from "
                   "<code>rolling-stock.governor</code>. HARD violations cannot be overridden by "
                   "any human approver; SOFT violations open a checkpointed approval interrupt. "
                   "This actor coordinates and logs &mdash; it never welds, rates a component, "
                   "certifies safety, or releases a vehicle for service.")
              ["Op" "Posture" "Gates"]
              action-gate-rows)

     (section "Audit ledger (this run)"
              (str "Append-only decision facts written by the compiled graph's "
                   "<code>:commit</code> and <code>:hold</code> node handlers &mdash; the only two "
                   "callers of <code>store/append-ledger!</code>. Approval <em>grants</em> are "
                   "deliberately absent: they are recorded on the graph's in-memory audit channel, "
                   "never in the durable ledger, so a granted approval appears here as the "
                   "<code>:committed</code> fact it produced.")
              ["Fact" "Op" "Subject" "Basis" "Detail"]
              (map ledger-row ledger))

     "</main>\n"
     "<footer>\n"
     "  <p>cloud-itonami-isic-3020 · Open Business Blueprint for ISIC Rev.5 3020 · AGPL-3.0-or-later. "
     "Regenerate with <code>clojure -M:dev:render-html</code>.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        ledger (store/ledger db)]
    (spit out (render db))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count (filter #(= :committed (:t %)) ledger)) " committed, "
                  (count (filter #(= :governor-hold (:t %)) ledger)) " hard holds, "
                  (count (filter #(= :approval-rejected (:t %)) ledger)) " approval-rejected)"))))
