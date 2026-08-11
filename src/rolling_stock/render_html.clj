(ns rolling-stock.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL actor stack -- `rolling-stock.store/mem-store` (seed) ->
  `rolling-stock.operation/build` (a genuinely compiled langgraph-clj
  StateGraph) -> `rolling-stock.advisor/propose` (the graph's `:advise`
  node) -> `rolling-stock.governor/evaluate` (the graph's `:govern`
  node) -> `rolling-stock.store/append-ledger!` (the graph's
  `:commit`/`:hold` nodes) -- and renders whatever that run actually
  produced. Nothing on the page is typed by hand: every subject id,
  field name, disposition, governor rule and ledger fact below is read
  back out of the store and the `langgraph.graph/run*` results.

  INPUT PROVENANCE. The scenario is DERIVED from the seed rather than
  written as literals -- `scenario` enumerates the ids actually present
  in `store/mem-store`'s `:units` / `:components` / `:defect-log` maps,
  so no id can appear here that the store does not hold. In particular
  `:flag-safety-defect`'s `:safety-critical?` flag is read from the
  store (`store/defect-is-safety-critical?`) instead of being asserted
  by this namespace. NOTE that `rolling-stock.sim` and
  `test/rolling_stock/operation_graph_test.clj` both drive `defect-002`
  / `defect-003` / `defect-004`, none of which exist in the seed -- the
  governor's safety-defect check reads the flag off the PROPOSAL rather
  than the store, so those runs work while describing records that are
  not there. This renderer deliberately does not copy that pattern.

  WHAT THIS DEMO DOES NOT SHOW, stated plainly. The graph's
  human-in-the-loop gate (`interrupt-before #{:request-approval}`) is
  NOT exercised, and no `:approval-rejected` fact appears below. That
  is not an omission of convenience: with the seeded data and the real
  `advisor/propose` dispatcher it is unreachable. A soft-only
  escalation needs `confidence < governor/confidence-floor` (every
  advisor proposal is hardcoded >= 0.85) or a `:flag-safety-defect`
  whose hard checks all pass -- and the seed's only defect record,
  `defect-001`, is `:safety-critical? true`, which is itself a HARD
  violation that pre-empts the soft branch. Reaching the approval gate
  honestly would require either a non-safety-critical defect in the
  seed or a non-default `:advisor`; inventing one to make a prettier
  page would defeat the point of the page.

  Also note: `:approval-granted` is written to the graph's in-memory
  `:audit` channel ONLY -- `operation/build`'s `:commit` node appends
  `commit-fact` (`:t :committed`), never the grant -- so the ledger's
  reachable fact types are exactly `:committed`, `:governor-hold` and
  `:approval-rejected`. This renderer branches on nothing else.

  Deterministic: no timestamps, no randomness, ids sorted, so two runs
  against the same seed are byte-identical (`clojure -M:dev:render-html
  /tmp/a.html && clojure -M:dev:render-html /tmp/b.html && cmp /tmp/a.html
  /tmp/b.html`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [langgraph.graph :as g]
            [rolling-stock.governor :as governor]
            [rolling-stock.operation :as operation]
            [rolling-stock.store :as store]))

;; ----------------------------- driving the real actor -----------------------------

(def ^:private qc-inspector
  "The operator context threaded into every run, same shape
  `rolling-stock.sim` uses."
  {:actor-id "qc-inspector-01" :role :quality-inspector})

(defn- seed-data
  "The seeded reference data behind the store's `:data` atom. Same access
  the repo's own `store_contract_test` uses."
  [st]
  @(:data st))

(defn scenario
  "The request list, DERIVED from `st`'s seed -- never literal ids.
  One `:schedule-maintenance` per seeded component, one
  `:log-production-record` per seeded unit, one `:flag-safety-defect`
  per seeded defect (its `:safety-critical?` flag read from the store,
  not asserted here), and one `:request-release-review` per seeded unit
  the store already reports verified."
  [st]
  (let [d (seed-data st)]
    (vec
     (concat
      (for [id (sort (keys (:components d)))]
        {:op :schedule-maintenance :subject id})
      (for [id (sort (keys (:units d)))]
        {:op :log-production-record :subject id})
      (for [id (sort (keys (:defect-log d)))]
        {:op :flag-safety-defect
         :subject id
         :safety-critical? (store/defect-is-safety-critical? st id)})
      (for [id (sort (keys (:units d)))
            :when (store/unit-verified? st id)]
        {:op :request-release-review :subject id})))))

(defn- thread-id [{:keys [op subject]}]
  (str "th-" (name op) "--" subject))

(defn run-demo!
  "Runs every request from `scenario` through ONE compiled actor bound to
  a fresh seeded store, each on its own checkpointed thread. Returns
  `{:store st :runs [{:thread .. :request .. :result ..}]}` where
  `:result` is `langgraph.graph/run*`'s own return value."
  []
  (let [st (store/mem-store)
        actor (operation/build st)
        runs (mapv (fn [request]
                     (let [tid (thread-id request)]
                       {:thread tid
                        :request request
                        :result (g/run* actor
                                        {:request request :context qc-inspector}
                                        {:thread-id tid})}))
                   (scenario st))]
    {:store st :runs runs}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- fmt
  "Render a domain value for a table cell. Never invents a value: a key
  a record does not carry renders as an em dash."
  [v]
  (cond
    (nil? v)        "—"
    (boolean? v)    (if v "yes" "no")
    (keyword? v)    (name v)
    (map? v)        (if (seq v)
                      (str/join ", " (map (fn [[k x]] (str (fmt k) "=" (fmt x)))
                                          (sort-by (comp str key) v)))
                      "—")
    (sequential? v) (if (seq v) (str/join ", " (map fmt v)) "—")
    :else           (str v)))

(defn- cell [v] (str "<td>" (esc (fmt v)) "</td>"))
(defn- code-cell [v] (str "<td><code>" (esc (fmt v)) "</code></td>"))
(defn- raw-cell [html] (str "<td>" html "</td>"))

(defn- table [headers rows]
  (str "<table class=\"oc-table\"><thead><tr>"
       (str/join (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead><tbody>\n"
       (str/join "\n" (map #(str "          <tr>" % "</tr>") rows))
       "\n        </tbody></table>"))

(defn- card [title lead & body]
  (str "      <section class=\"dds-ext-card\">\n"
       "        <h2 class=\"dads-heading\" data-size=\"28\">" (esc title) "</h2>\n"
       (when lead (str "        <p class=\"oc-lead\">" lead "</p>\n"))
       "        " (str/join "\n        " body) "\n"
       "      </section>\n"))

;; ----------------------------- ledger-derived status -----------------------------

(defn- last-fact
  "The most recent ledger fact naming `subject`, or nil when the subject
  was never driven."
  [ledger subject]
  (last (filter #(= subject (:subject %)) ledger)))

(defn- status-html
  "Status of one subject, read only from ledger fact types this store
  actually appends. `:committed` comes from `operation/build`'s
  `:commit` node; every hold fact is stamped `:disposition :hold` by its
  `:hold` node, and its `:t` (`:governor-hold` or `:approval-rejected`)
  is printed verbatim rather than being re-labelled here -- so there is
  no branch for a fact type that never reaches the ledger."
  [ledger subject]
  (let [f (last-fact ledger subject)]
    (cond
      (nil? f)
      "<span class=\"oc-muted\">not driven</span>"

      (= :committed (:t f))
      "<span class=\"oc-ok\">committed</span>"

      (= :hold (:disposition f))
      (str "<span class=\"oc-hard\">" (esc (name (:t f))) ": "
           (esc (str/join " + " (map name (:basis f)))) "</span>")

      :else
      (throw (ex-info "unrecognised ledger fact shape -- refusing to render a guess"
                      {:fact f})))))

;; ----------------------------- sections -----------------------------

(defn- entity-table
  "One table per seeded entity kind. Columns are the union of the keys
  the seeded records ACTUALLY carry (sorted), so no column can exist
  that the domain model does not."
  [records ledger id-header]
  (let [ids  (sort (keys records))
        cols (->> (vals records) (mapcat keys) distinct sort vec)
        row  (fn [id]
               (let [r (get records id)]
                 (str "<td><code>" (esc id) "</code></td>"
                      (str/join (map #(cell (get r %)) cols))
                      (raw-cell (status-html ledger id)))))]
    (table (concat [id-header] (map name cols) ["last ledger fact"])
           (map row ids))))

(defn- seed-section [st ledger]
  (let [d (seed-data st)]
    (card "Seeded subjects"
          (str "Read back out of <code>rolling-stock.store/mem-store</code>. "
               "Every request this page drives is derived from these ids "
               "(see <code>rolling-stock.render-html/scenario</code>); no id "
               "is written as a literal.")
          "<h3 class=\"oc-h3\">Units</h3>"
          (entity-table (:units d) ledger "unit")
          "<h3 class=\"oc-h3\">Components</h3>"
          (entity-table (:components d) ledger "component")
          "<h3 class=\"oc-h3\">Production records</h3>"
          (entity-table (:production-records d) ledger "record")
          "<h3 class=\"oc-h3\">Defect log</h3>"
          (entity-table (:defect-log d) ledger "defect"))))

(def ^:private gate-catalog
  "The governor's rule catalog. Rule names and severities mirror
  `rolling-stock.governor/evaluate`'s `hard-checks-store` /
  `hard-checks-value` / `soft-checks` vectors; the keyword lists and the
  confidence floor are read from that namespace's real vars rather than
  restated here."
  [{:rule :no-spec-basis
    :class "HARD"
    :scope ":log-production-record, :flag-safety-defect"
    :basis "proposal must carry a :cites entry (official railway safety standard)"}
   {:rule :release-forbidden
    :class "HARD"
    :scope "every op"
    :basis (str "proposal :detail / :op must not contain: "
                (str/join ", " (sort governor/release-forbidden-keywords)))}
   {:rule :process-control-forbidden
    :class "HARD"
    :scope "every op"
    :basis (str "proposal :detail / :op must not contain: "
                (str/join ", " (sort governor/process-control-keywords)))}
   {:rule :unit-not-verified
    :class "HARD"
    :scope ":log-production-record"
    :basis "store/unit-verified? -- the store's own ground truth, not the advisor's claim"}
   {:rule :safety-defect-escalation
    :class "HARD"
    :scope ":flag-safety-defect"
    :basis "proposal :value :safety-critical? -- a safety defect is never silently logged"}
   {:rule :escalate
    :class "SOFT"
    :scope "every op"
    :basis (str "confidence < " governor/confidence-floor
                " (governor/confidence-floor), or op = :flag-safety-defect")}])

(defn- fired-counts
  "How many runs each rule actually fired on, counted from the verdicts
  the `:govern` node produced."
  [runs]
  (frequencies
   (mapcat (fn [{:keys [result]}]
             (let [v (get-in result [:state :verdict])]
               (map :rule (concat (:hard-violations v) (:soft-violations v)))))
           runs)))

(defn- gate-section [runs]
  (let [fired (fired-counts runs)]
    (card "Governor gate"
          (str "The independent layer between the advisor and the ledger. "
               "HARD violations route straight to <code>:hold</code> and never "
               "pause for a human; a SOFT violation is what would open the "
               "checkpointed <code>:request-approval</code> gate.")
          (table ["rule" "class" "applies to" "what it checks" "fired in this run"]
                 (map (fn [{:keys [rule class scope basis]}]
                        (str (code-cell rule)
                             (raw-cell (str "<span class=\""
                                            (if (= "HARD" class) "oc-hard" "oc-warn")
                                            "\">" class "</span>"))
                             (code-cell scope)
                             (cell basis)
                             (cell (get fired rule 0))))
                      gate-catalog)))))

(defn- run-rows [runs]
  (map (fn [{:keys [thread request result]}]
         (let [state (:state result)
               verdict (:verdict state)
               hard (mapv :rule (:hard-violations verdict))
               soft (mapv :rule (:soft-violations verdict))]
           (str (code-cell thread)
                (code-cell (:op request))
                (code-cell (:subject request))
                (cell (get-in state [:proposal :value :confidence]))
                (code-cell (:status result))
                (raw-cell (let [d (:disposition state)]
                            (str "<span class=\""
                                 (case d :commit "oc-ok" :hold "oc-hard" "oc-warn")
                                 "\">" (esc (fmt d)) "</span>")))
                (cell (if (seq hard) hard nil))
                (cell (if (seq soft) soft nil)))))
       runs))

(defn- runs-section [runs]
  (card "Run trace"
        (str "One <code>langgraph.graph/run*</code> call per row, each on its "
             "own checkpointed thread against one compiled "
             "<code>rolling-stock.operation/build</code> actor. "
             "<code>confidence</code> and the violation lists are the "
             "<code>:advise</code> and <code>:govern</code> nodes' own output, "
             "pulled off the returned graph state.")
        (table ["thread" "op" "subject" "confidence" "run status" "disposition"
                "HARD violations" "SOFT violations"]
               (run-rows runs))))

(defn- ledger-section [ledger]
  (card "Audit ledger"
        (str "Everything <code>rolling-stock.store/append-ledger!</code> was "
             "called with, in append order. The <code>:commit</code> and "
             "<code>:hold</code> nodes of the compiled graph are its only "
             "callers, so this is the actor's entire durable write path.")
        (table ["#" "fact" "op" "subject" "actor" "disposition" "basis" "summary"]
               (map-indexed
                (fn [i f]
                  (str (cell (inc i))
                       (code-cell (:t f))
                       (code-cell (:op f))
                       (code-cell (:subject f))
                       (cell (:actor f))
                       (cell (:disposition f))
                       (cell (:basis f))
                       (cell (or (:summary f)
                                 (some->> (:violations f) (map :detail) (str/join " / "))))))
                ledger))))

(defn- summary-section [runs ledger]
  (let [dispositions (frequencies (map #(get-in % [:result :state :disposition]) runs))
        hard-rules (->> runs
                        (mapcat #(get-in % [:result :state :verdict :hard-violations]))
                        (map :rule) distinct sort)]
    (card "This run"
          nil
          (table ["measure" "value"]
                 [(str (cell "graph runs") (cell (count runs)))
                  (str (cell "committed") (cell (get dispositions :commit 0)))
                  (str (cell "held") (cell (get dispositions :hold 0)))
                  (str (cell "escalated to human approval") (cell (get dispositions :escalate 0)))
                  (str (cell "ledger facts appended") (cell (count ledger)))
                  (str (cell "distinct HARD rules fired")
                       (raw-cell (str "<span class=\"oc-hard\">"
                                      (esc (str/join ", " (map name hard-rules)))
                                      "</span>")))]))))

(defn- caveats-section [runs]
  (let [interrupted (filter #(= :interrupted (get-in % [:result :status])) runs)]
    (card "Not exercised — and why"
          nil
          (str "<ul class=\"oc-list\">"
               "<li>The graph's human-in-the-loop gate "
               "(<code>interrupt-before #{:request-approval}</code>) did not open: "
               (esc (str (count interrupted)))
               " of " (esc (str (count runs)))
               " runs interrupted. With the seeded data and the real "
               "<code>advisor/propose</code> dispatcher it cannot — every advisor "
               "proposal is hardcoded at confidence &gt;= 0.85 (floor is "
               (esc (str governor/confidence-floor))
               "), and the seed's only defect record is <code>:safety-critical? true</code>, "
               "which is a HARD violation that pre-empts the soft escalate branch.</li>"
               "<li>Consequently no <code>:approval-rejected</code> fact appears in the "
               "ledger. <code>:approval-granted</code> could not appear in any case: "
               "<code>operation/build</code> emits it to the in-memory <code>:audit</code> "
               "channel only, never through <code>store/append-ledger!</code>.</li>"
               "<li><code>:request-release-review</code> is blocked structurally rather "
               "than by data — the advisor's own proposal detail contains the word "
               "<em>release</em>, which is in the governor's forbidden-keyword set, so this "
               "op cannot commit for any subject. Recorded here as observed, not "
               "corrected.</li>"
               "<li>Two hold details in the ledger above read <em>禁止キーワード "
               "'true'</em>. That is the governor's own string, rendered verbatim: "
               "<code>release-forbidden-violations</code> and "
               "<code>process-control-block-violations</code> bind their offending word "
               "with <code>(some #(contains? kws %) words)</code>, which yields "
               "<code>true</code> rather than the word that matched. A real defect, "
               "surfaced by rendering real output; left for a separate change rather "
               "than patched behind this page.</li>"
               "<li>No jurisdiction requirement from <code>rolling-stock.facts</code> is "
               "checked above: <code>governor/evaluate</code> never calls "
               "<code>facts/required-evidence-satisfied?</code>, so that catalog is not "
               "part of this actor's gate today.</li>"
               "</ul>"))))

;; ----------------------------- page -----------------------------

(def ^:private landing-page "docs/index.html")

(defn- dads-css
  "The jp-go-digital-design-system stylesheet this repo already vendors
  into `docs/index.html` (upstream digital-go-jp/design-system-example-
  components-html, MIT). Reused verbatim so the console cannot drift
  from the repo's own product face and no token is hand-copied."
  [path]
  (let [h (slurp path)
        open "<style>"
        i (.indexOf h open)
        j (.indexOf h "</style>")]
    (when (or (neg? i) (neg? j))
      (throw (ex-info "vendored jp-go-dds <style> block not found" {:path path})))
    (subs h (+ i (count open)) j)))

(def ^:private console-css "
/* operator console -- layered on the vendored jp-go-dds tokens above */
.oc-lead { color: var(--color-neutral-solid-gray-700); line-height: 1.8; margin: 0 0 1.25rem; }
.oc-h3 { font-size: 1rem; font-weight: 700; margin: 1.75rem 0 .5rem; color: var(--color-neutral-solid-gray-800); }
.oc-h3:first-of-type { margin-top: .5rem; }
.oc-table { border-collapse: collapse; width: 100%; font-size: .8125rem; line-height: 1.6; }
.oc-table th, .oc-table td { text-align: left; padding: .45rem .6rem; border-bottom: 1px solid var(--color-neutral-solid-gray-200); vertical-align: top; }
.oc-table th { font-weight: 700; color: var(--color-neutral-solid-gray-700); background: var(--color-neutral-solid-gray-50); white-space: nowrap; }
.oc-table code { background: var(--color-neutral-solid-gray-50); padding: .1rem .3rem; border-radius: 3px; }
.oc-ok { color: var(--color-semantic-success-1); font-weight: 700; }
.oc-warn { color: var(--color-semantic-warning-orange-1); font-weight: 700; }
.oc-hard { color: var(--color-semantic-error-1); font-weight: 700; }
.oc-muted { color: var(--color-neutral-solid-gray-600); }
.oc-list { line-height: 1.8; padding-left: 1.25rem; margin: 0; color: var(--color-neutral-solid-gray-800); }
.oc-list code { background: var(--color-neutral-solid-gray-50); padding: .1rem .3rem; border-radius: 3px; }
")

(defn render
  "The whole page, from a `run-demo!` result."
  [{:keys [store runs]}]
  (let [ledger (vec (store/ledger store))]
    (str "<!DOCTYPE html>\n<html lang=\"en\">\n  <head>"
         "<meta charset=\"utf-8\">"
         "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
         "<meta name=\"color-scheme\" content=\"light\">"
         "<meta name=\"theme-color\" content=\"#ffffff\">"
         "<title>Operator console | cloud-itonami-isic-3020</title>"
         "<meta name=\"description\" content=\"Build-time console rendered from a real run of the rolling-stock manufacturing actor (ISIC 3020).\">"
         "<style>" (dads-css landing-page) console-css "</style>"
         "</head>\n  <body>\n"
         "    <div class=\"dds-ext-container\">\n"
         "      <header class=\"pf-header\">\n"
         "        <h1 class=\"dads-heading\" data-size=\"45\">Rolling stock manufacturing — operator console</h1>\n"
         "        <span class=\"dads-chip-label\" data-style=\"filled-1\" data-color=\"blue\">ISIC 3020 · rendered from a real actor run</span>\n"
         "      </header>\n"
         "      <div class=\"dds-ext-card\">\n"
         "        <p class=\"oc-lead\">Generated by <code>clojure -M:dev:render-html</code> "
         "(<code>rolling-stock.render-html</code>). Every row below is read back out of "
         "<code>rolling-stock.store</code> and the values <code>langgraph.graph/run*</code> "
         "returned from the compiled <code>rolling-stock.operation/build</code> graph — "
         "no mock, no hand-written HTML table, no invented subject id, no timestamp. "
         "Re-running against the same seed reproduces this file byte for byte.</p>\n"
         "      </div>\n"
         (summary-section runs ledger)
         (seed-section store ledger)
         (gate-section runs)
         (runs-section runs)
         (ledger-section ledger)
         (caveats-section runs)
         "      <footer class=\"pf-footer\">\n"
         "        <p>Open occupation blueprint — no invented usage or revenue metrics. "
         "Seed data is reference data from <code>rolling-stock.store/mem-store</code>, not production records.</p>\n"
         "      </footer>\n"
         "    </div>\n  </body>\n</html>\n")))

(defn -main
  "clojure -M:dev:render-html [out-file]"
  [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        demo (run-demo!)
        html (render demo)
        f (java.io.File. ^String out)]
    (when-let [p (.getParentFile f)] (.mkdirs p))
    (spit f html)
    (println "wrote" out
             (str "(" (count (:runs demo)) " graph runs, "
                  (count (store/ledger (:store demo))) " ledger facts, "
                  (count html) " chars)"))))
