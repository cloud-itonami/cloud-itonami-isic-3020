(ns rolling-stock.operation
  "OperationActor for the community railway rolling-stock manufacturing
  coordinator.

  `build` compiles a REAL langgraph-clj StateGraph
  (`langgraph.graph/state-graph` + `compile-graph`) that seals
  `rolling-stock.advisor`'s proposal dispatcher into a single node
  (`:advise`), ALWAYS routes its proposal through the independent
  Rolling Stock Manufacturing Governor (`:govern`) before anything
  commits, and gives high-stakes ops a real human-in-the-loop approval
  gate (`:request-approval`, `interrupt-before` + checkpoint-based
  resume) instead of silently returning a verdict map nobody acts on.
  Mirrors `pastaops.operation` (cloud-itonami-isic-1074) /
  `transportops.operation` (cloud-itonami-isic-869) node/edge structure,
  wired to this repo's own advisor/governor/store.

  PRIOR BUGS (fixed here):
    1. There was no `operation.cljc` at all -- `rolling-stock.sim`
       hand-wired the four advisor proposal functions straight into
       `governor/evaluate` and printed the verdict to stdout. No
       `langgraph.graph` `state-graph`/`add-node`/`compile-graph` call
       existed anywhere in `src/`, despite `deps.edn` correctly
       declaring real `io.github.com-junkawasaki/langgraph-clj` (with a
       real `:git/sha`) at the top level -- the dependency was wired,
       nothing used it.
    2. `rolling-stock.store` had no append-only audit ledger at all --
       `governor/evaluate`'s verdict was never durably recorded anywhere.
       Fixed: `store/ledger` + `store/append-ledger!` (new), genuinely
       called from this namespace's compiled graph's `:commit`/`:hold`
       node handlers below.

  `rolling-stock.advisor`'s four proposal-builders stay PLAIN FUNCTIONS
  (not a `defprotocol`) -- wiring them into a real compiled graph node
  did not require a protocol; `advisor/propose` (partial-applied over an
  advisor value) is the injection seam this `build` accepts as its
  `:advisor` opt.

  Governor stakes, derived from what `rolling-stock.governor/evaluate`
  actually checks (read there first):
    - HARD (always -> :hold, no override): no spec-basis citation,
      release-forbidden keyword, process-control-forbidden keyword,
      unverified unit record (`:log-production-record` only).
    - SOFT (always -> :escalate unless a hard violation already holds):
      low confidence, OR `:flag-safety-defect` (unconditionally, per
      `confidence-gate-violations` -- a safety-defect flag ALWAYS needs
      a human look, matching the governor namespace's own doc comment).
    - Otherwise -> :commit.

  One graph run = one manufacturing-coordination request. No unbounded
  inner loop -- each run is auditable and checkpointed. A vehicle/unit's
  operating history is advanced by MANY runs (log-production-record /
  schedule-maintenance / flag-safety-defect / request-release-review),
  each its own independent graph run, and every commit/hold/
  approval-rejected decision fact lands in `rolling-stock.store`'s
  append-only ledger (`store/append-ledger!`)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [rolling-stock.advisor :as advisor]
            [rolling-stock.governor :as governor]
            [rolling-stock.store :as store]))

;; ----------------------------- audit fact shapes -----------------------------

(defn- commit-record
  "The store-level payload a commit represents. This actor has no
  separate stateful commit-record! entity beyond the seeded reference
  data -- the ledger fact itself is the durable record of what
  happened, same discipline as `pastaops.operation`'s
  `:schedule-maintenance` / `:flag-food-safety-concern` (ops with no
  dedicated mutable flag of their own)."
  [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:subject request)]
   :value   (:value proposal)
   :payload (:value proposal)})

(defn- commit-fact
  "The audit fact written when a proposal commits."
  [request context proposal]
  {:t           :committed
   :op          (:op request)
   :actor       (:actor-id context)
   :subject     (:subject request)
   :disposition :commit
   :basis       (:cites proposal)
   :summary     (get-in proposal [:value :detail])
   :record      (:value proposal)})

(defn- hold-fact
  "The audit fact written when a proposal is rejected (HOLD). Built here
  (not in `rolling-stock.governor`, which returns only a verdict map --
  `{:holds? :hard-violations :soft-violations :clean?}`, no `hold-fact`
  helper of its own)."
  [request context verdict]
  {:t           :governor-hold
   :op          (:op request)
   :actor       (:actor-id context)
   :subject     (:subject request)
   :disposition :hold
   :basis       (mapv :rule (:hard-violations verdict))
   :violations  (:hard-violations verdict)})

;; ----------------------------- StateGraph -----------------------------

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a plain 1-arg fn of `request` -> proposal (default:
                     `(partial advisor/propose (advisor/mock-advisor))`).
                     Inject a test double, or a real LLM-backed dispatcher,
                     here without touching the graph wiring below.
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (partial advisor/propose (advisor/mock-advisor))
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      (g/add-node :govern
        (fn [{:keys [proposal]}]
          {:verdict (governor/evaluate proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [disposition (cond
                               (:holds? verdict)              :hold
                               (seq (:soft-violations verdict)) :escalate
                               :else                            :commit)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(hold-fact request context verdict)]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :subject (:subject request)
                        :reasons (mapv :rule (:soft-violations verdict))}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :subject (:subject request) :by (:by approval)}]}
            {:disposition :hold
             :audit [{:t :approval-rejected
                      :op (:op request) :actor (:actor-id context)
                      :subject (:subject request)
                      :disposition :hold
                      :basis (conj (mapv :rule (:soft-violations verdict)) :approver-rejected)
                      :violations (conj (vec (:soft-violations verdict))
                                        {:rule :approver-rejected})}]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal]}]
          (let [f (commit-fact request context proposal)]
            (store/append-ledger! store f)
            {:audit [f]})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
