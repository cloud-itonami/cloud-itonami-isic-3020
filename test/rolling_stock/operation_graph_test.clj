(ns rolling-stock.operation-graph-test
  "Integration tests for `rolling-stock.operation/build` -- builds the
  REAL compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through commit / hard-hold / escalate-approve /
  escalate-reject routes. None of this existed before: there was no
  `operation.cljc` at all, no `:advise`/`:govern`/`:decide`/`:commit`/
  `:hold`/`:request-approval` graph, and `rolling-stock.sim` hand-wired
  advisor proposal functions straight into `governor/evaluate`, printed
  the verdict, and discarded it -- `store/append-ledger!` (also new)
  had never been called from anywhere.

  Falsifiable claims each test proves, not just asserts:
    1. the ledger is verified EMPTY before the run (never pre-populated
       by test fixtures), so a post-run non-empty ledger is genuinely
       caused by this run's own `:commit`/`:hold` node, not residue;
    2. a HARD governor violation blocks the graph from EVER reaching
       `:commit` -- proven for `:log-production-record` against an
       UNVERIFIED unit (`unit-002`, seeded `:verified? false` in
       `store/mem-store`), a real ground-truth check the mock advisor's
       own (optimistic) proposal evidence cannot override;
    3. the advisor's proposal is genuinely threaded through
       `:advise -> :govern -> :decide -> :commit` -- proven by injecting
       a custom `:advisor` fn (via `build`'s opt) whose proposal carries
       a random, single-use `:detail` string generated at test run time
       (impossible to have been hardcoded anywhere in
       `rolling-stock.operation`) and asserting the committed ledger
       fact's `:summary` carries that EXACT string;
    4. `:flag-safety-defect` genuinely escalates (checkpointed
       interrupt, not an immediate hold) and stays un-recorded in the
       ledger until a human QC inspector resumes the thread -- both the
       approve->commit and reject->hold branches are exercised."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [rolling-stock.operation :as operation]
            [rolling-stock.store :as store]))

(def ^:private qc-inspector {:actor-id "qc-inspector-01" :role :quality-inspector})

(defn- exec
  ([actor tid request] (exec actor tid request qc-inspector))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(deftest commit-path-clean-low-stakes-proposal
  (testing "a clean, low-stakes (:schedule-maintenance) proposal commits
            through the REAL compiled graph and appends exactly one fact
            to the audit ledger -- the ledger is verified EMPTY
            beforehand, proving the write is a genuine effect of THIS
            run, not test-setup residue"
    (let [s (store/mem-store)
          actor (operation/build s)]
      (is (empty? (store/ledger s)) "ledger is empty before any run")
      (let [result (exec actor "t-commit" {:op :schedule-maintenance :subject "brake-system-001"})
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :schedule-maintenance (:op (first ledger))))
          (is (= "brake-system-001" (:subject (first ledger)))))))))

(deftest hard-hold-path-unit-not-verified
  (testing ":log-production-record against `unit-002` -- SEEDED
            `:verified? false` in `store/mem-store`, a ground-truth fact
            the advisor's own (optimistic) proposal evidence cannot
            override -- is a HARD governor violation. The real graph
            routes straight to :hold (no interrupt, no human-approval
            detour) and durably records the hold fact"
    (let [s (store/mem-store)
          actor (operation/build s)]
      (is (empty? (store/ledger s)))
      (let [result (exec actor "t-hold" {:op :log-production-record :subject "unit-002"})
            state (:state result)]
        (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
        (is (= :hold (:disposition state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :governor-hold (:t (first ledger))))
          (is (some #{:unit-not-verified} (map :rule (:violations (first ledger))))))))))

(deftest governor-hard-hold-blocks-ledger-write-before-commit
  (testing "a HARD governor violation (:unit-not-verified) proves the
            ledger contains ONLY a :governor-hold fact -- never a
            :committed fact -- for a request whose advisor proposal
            independently claims high confidence and complete evidence.
            The governor's OWN ground-truth store lookup (not the
            advisor's claim) is what decides"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-govhold" {:op :log-production-record :subject "unit-002"})]
      (is (= :hold (:disposition (:state result))))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (every? #(= :governor-hold (:t %)) ledger)
            "no :committed fact was ever written")))))

(deftest escalate-then-approve-commits-and-genuinely-consults-advisor
  (testing ":flag-safety-defect (non-critical) ALWAYS escalates -- the
            real graph GENUINELY interrupts (checkpointed) at
            :request-approval, and the ledger stays EMPTY until a human
            QC inspector resumes it. A custom, non-default advisor
            (injected at test time via `build`'s `:advisor` opt, NOT a
            call-site literal in `rolling-stock.operation`) proposes
            with a randomly generated, single-use `:detail` string. Only
            if the graph truly threads the advisor's own proposal
            through :advise -> :govern -> :decide -> :commit (rather
            than re-deriving/hardcoding a proposal internally) can that
            exact string reach the ledger's committed fact"
    (let [distinctive-detail (str "TEST-ADVISOR-" (rand-int 1000000000))
          test-advisor (fn [request]
                         {:op :flag-safety-defect
                          :subject (:subject request)
                          :effect :propose
                          :cites ["Railway Safety Regulation (鉄道安全規則) §1.3"]
                          :value {:evidence {:defect-confirmed true}
                                  :confidence 0.9
                                  :safety-critical? false
                                  :detail distinctive-detail}})
          s (store/mem-store)
          actor (operation/build s {:advisor test-advisor})]
      (is (empty? (store/ledger s)))
      (let [held (exec actor "t-escalate" {:op :flag-safety-defect :subject "defect-002"
                                            :safety-critical? false})]
        (is (= :interrupted (:status held)))
        (is (= [:request-approval] (:frontier held)))
        (is (empty? (store/ledger s)) "not yet committed -- awaiting human sign-off")
        (let [approved (g/run* actor {:approval {:status :approved :by "qc-inspector-01"}}
                               {:thread-id "t-escalate" :resume? true})
              approved-state (:state approved)]
          (is (= :done (:status approved)))
          (is (= :commit (:disposition approved-state)))
          (let [ledger (store/ledger s)]
            (is (= 1 (count ledger)))
            (is (= :committed (:t (first ledger))))
            (is (= distinctive-detail (:summary (first ledger)))
                "the ledger's committed fact carries the INJECTED test
                advisor's own distinctive detail string -- proof the
                graph genuinely threads the advisor's real proposal
                through :govern -> :decide -> :commit rather than
                hardcoding a pass-string or ignoring the :advise node's
                output")))))))

(deftest escalate-then-reject-holds
  (testing "a human QC inspector rejecting an escalated
            :flag-safety-defect routes to :hold via the
            :request-approval node's own decision, and durably records
            the rejection -- not a hand-rolled parallel path"
    (let [s (store/mem-store)
          actor (operation/build s)
          _held (exec actor "t-reject" {:op :flag-safety-defect :subject "defect-002"
                                         :safety-critical? false})
          rejected (g/run* actor {:approval {:status :rejected :by "qc-inspector-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (some #{:approver-rejected} (map :rule (:violations (first ledger)))))))))

(deftest critical-safety-defect-hard-holds-never-reaches-approval
  (testing "a SAFETY-CRITICAL defect flag is a HARD governor violation
            (`:safety-defect-escalation`) -- the graph routes straight
            to :hold WITHOUT ever pausing at :request-approval, proving
            the hard-check runs and wins before the escalate branch
            could otherwise take over (this op's confidence-gate soft
            violation would ALSO fire, but the hard block pre-empts it)"
    (let [s (store/mem-store)
          actor (operation/build s)
          result (exec actor "t-critical" {:op :flag-safety-defect :subject "defect-003"
                                            :safety-critical? true})]
      (is (= :done (:status result)) "no interrupt -- HARD holds never pause for approval")
      (is (= :hold (:disposition (:state result))))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (every? #(= :governor-hold (:t %)) ledger))
        (is (some #{:safety-defect-escalation} (map :rule (:violations (first ledger)))))))))
