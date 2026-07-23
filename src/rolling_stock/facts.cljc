(ns rolling-stock.facts
  "Per-jurisdiction rolling-stock manufacturing safety and design requirements.
  Every jurisdiction in this catalog is backed by an official spec-basis.
  NEVER invent requirements without an official citation.

  :FRA is scoped more narrowly than JPN/USA/GBR -- only vehicle-
  authorization is verified and included; the equivalent of
  structural-integrity/braking-system/electrical-safety is honestly
  absent rather than guessed by analogy (France's framework routes that
  technical detail through EU-level TSIs, not restated in EPSF's own
  guidance this iteration read).

  :DEU is likewise scoped narrower than JPN/USA/GBR -- vehicle-
  authorization (initial acceptance) and periodic-inspection are verified
  and included; structural-integrity/braking-system/electrical-safety are
  honestly absent because the Eisenbahn-Bau- und Betriebsordnung (EBO)
  sections this iteration read (its own § index plus §§ 3 and 32) do not
  restate those as independently-citable sub-standards the way JPN/USA/GBR's
  entries do.

  :KOR is likewise scoped narrower than JPN/USA/GBR -- design type-approval
  and post-manufacture completion inspection are verified and included;
  structural-integrity/braking-system/electrical-safety are honestly absent
  because Article 26(3) of the Railroad Safety Act (철도안전법) itself routes
  the technical standards for rolling stock to a separately published
  Ministerial notification (고시) that this iteration did not fetch, rather
  than restating them as independently-citable sub-standards within the Act
  the way JPN/USA/GBR's entries do (same honest-absence pattern as :FRA/:DEU).

  This is deliberately a starting catalog (honest coverage reporting) to
  prove the governor contract end-to-end, not a claim of global coverage.
  Adding a jurisdiction is additive: one map entry citing a real official
  source -- never fabricate a jurisdiction's requirements to make coverage
  look bigger.")

;; ----------------------------- jurisdiction catalog -----------------------------

(def catalog
  "Per-jurisdiction rolling-stock manufacturing requirements with official spec-basis citations."
  {
   :JPN
   {:name "Japan"
    :requirements
    {:structural-integrity {:description "Crashworthiness and structural frame welding verification"
                           :required true
                           :spec-basis "Railway Technical Standards (鉄道技術基準) §4.2"
                           :evidence [:weld-inspection-cert :frame-stress-analysis :material-cert]}
     :braking-system {:description "Braking system component and integration verification"
                     :required true
                     :spec-basis "Railway Safety Regulation (鉄道安全規則) §2.1"
                     :evidence [:braking-component-cert :integration-test-pass :failsafe-verification]}
     :electrical-safety {:description "Electrical system and power-distribution safety certification"
                        :required true
                        :spec-basis "Railway Technical Standards §5.3"
                        :evidence [:electrical-inspection :grounding-verification :short-circuit-test]}
     :wheel-and-axle {:description "Wheel and axle manufacturing and inspection per standards"
                     :required true
                     :spec-basis "Railway Maintenance Safety Standard (鉄道保守安全基準) §3"
                     :evidence [:wheel-inspection-cert :axle-material-cert :rolling-quality-test]}}}

   :USA
   {:name "United States"
    :requirements
    {:structural-integrity {:description "FRA crashworthiness and structural design requirements"
                           :required true
                           :spec-basis "Federal Railroad Administration (FRA) Part 238 - Passenger Equipment Safety Standards"
                           :evidence [:crashworthiness-test :frame-analysis :material-compliance]}
     :braking-system {:description "Air brake system and emergency braking verification per FRA standard"
                     :required true
                     :spec-basis "FRA Part 238.205 (Braking Systems)"
                     :evidence [:brake-system-inspection :emergency-brake-test :failsafe-cert]}
     :electrical-safety {:description "Electrical equipment and grounding compliance"
                        :required true
                        :spec-basis "FRA Part 238.301 (Electrical Power Systems)"
                        :evidence [:electrical-inspection :ground-continuity-test :short-circuit-protection]}}}

   :GBR
   {:name "United Kingdom"
    :requirements
    {:structural-integrity {:description "Rail Safety and Standards Board (RSSB) structural compliance"
                           :required true
                           :spec-basis "RSSB Rolling Stock Standards Part 1: Structural Design (GM/RT2100)"
                           :evidence [:static-load-test :weld-inspection :material-certification]}
     :braking-system {:description "Braking system design and performance per RSSB standards"
                     :required true
                     :spec-basis "RSSB GM/RT2150 (Braking Systems)"
                     :evidence [:braking-performance-test :failsafe-verification :emergency-brake-cert]}
     :driver-cab {:description "Driver cab ergonomics and emergency exit requirements"
                 :required true
                 :spec-basis "RSSB GM/RT2200 (Driver Facilities)"
                 :evidence [:ergonomic-assessment :emergency-exit-cert :visibility-test]}}}

   ;; France -- WebFetch-verified 2026-07-21 directly against legifrance.gouv.fr
   ;; and securite-ferroviaire.fr (EPSF's own official site). Deliberately
   ;; scoped to ONE requirement (vehicle-authorization) rather than force-
   ;; matching JPN/USA/GBR's more granular structural/braking/electrical
   ;; breakdown -- this iteration verified EPSF's overall statutory mandate
   ;; and authorization principle (safety-demonstration-based, applicant
   ;; bears the burden of proof) but did not find equally specific,
   ;; independently-citable sub-standards for structural/braking/electrical
   ;; the way JPN/USA/GBR's entries have (those cite specific technical
   ;; standard sections; France's framework routes technical detail through
   ;; EU-level TSIs referenced by, not restated in, EPSF's own guidance
   ;; pages this iteration read).
   :FRA
   {:name "France"
    :requirements
    {:vehicle-authorization {:description "Placing a rail vehicle on the market/into service requires an authorization from the Établissement public de sécurité ferroviaire (EPSF, France's national railway safety authority) or, for vehicles whose domain of use extends beyond France, the European Union Agency for Railways (with national aspects still evaluated by EPSF); the applicant bears the burden of demonstrating safety-regulation compliance via safety-demonstration documentation"
                             :required true
                             :spec-basis "Code des transports art. L2221-1 (EPSF's mandate as national safety authority under EU Directive 2016/798, covering railway safety AND interoperability, i.e. rolling-stock authorization) -- confirmed directly on both legifrance.gouv.fr and EPSF's own securite-ferroviaire.fr site"
                             :evidence [:epsf-or-era-authorization :safety-demonstration-file]}}}

   ;; Germany -- direct-curl-verified 2026-07-21/22 against
   ;; gesetze-im-internet.de (the Bundesministerium der Justiz's official
   ;; federal-law portal; plain HTML with a real text layer, no OCR needed):
   ;; the EBO section index at https://www.gesetze-im-internet.de/ebo/,
   ;; EBO § 32 "Abnahme und Untersuchung der Fahrzeuge" at
   ;; https://www.gesetze-im-internet.de/ebo/__32.html, and EBO § 3
   ;; "Ausnahmen, Genehmigungen" at https://www.gesetze-im-internet.de/ebo/__3.html
   ;; (referenced by § 32 Abs. 1 for the approving authority). Deliberately
   ;; scoped to TWO requirements (initial vehicle acceptance/authorization,
   ;; and mandatory recurring inspection) rather than force-matching
   ;; JPN/USA/GBR's more granular structural/braking/electrical breakdown --
   ;; those sub-topics were not found restated as independently-citable
   ;; standards within the EBO sections this iteration read (EBO's other
   ;; vehicle-related sections found in the index, e.g. §§ 19/22/25, cover
   ;; axle loads and vehicle gauge/clearance, not manufacturing-safety
   ;; standards in the same shape as JPN/USA/GBR's entries), so honestly
   ;; left out rather than guessed by analogy.
   :DEU
   {:name "Germany"
    :requirements
    {:vehicle-authorization {:description "New railway vehicles may only be put into service once they have been formally accepted (\"abgenommen\"); this acceptance is granted by the Eisenbahn-Bundesamt (EBA, Federal Railway Authority) for federal railways and railway undertakings based abroad, or by the competent state (Land) authority for non-federally-owned railways"
                             :required true
                             :spec-basis "Eisenbahn-Bau- und Betriebsordnung (EBO) § 32 Abs. 1 (\"Neue Fahrzeuge dürfen erst in Betrieb genommen werden, wenn sie abgenommen worden sind (§ 3 Abs. 2).\"), referencing § 3 Abs. 2 for the approving authority (\"...erteilen 1. für Eisenbahnen des Bundes sowie für Eisenbahnverkehrsunternehmen mit Sitz im Ausland das Eisenbahn-Bundesamt, 2. für die nichtbundeseigenen Eisenbahnen die zuständige Landesbehörde.\") -- confirmed directly on gesetze-im-internet.de, the Bundesministerium der Justiz's official federal-law portal"
                             :evidence [:eba-or-land-authority-acceptance :vehicle-acceptance-cert]}
     :periodic-inspection {:description "Vehicles must be inspected periodically as scheduled (\"planmäßig wiederkehrend\"); absent other maintenance-body requirements, an inspection is required at least every six years (extendable up to eight years if the vehicle's condition permits), and records of each inspection must be kept"
                           :required true
                           :spec-basis "Eisenbahn-Bau- und Betriebsordnung (EBO) § 32 Abs. 2-4 (\"Die Fahrzeuge sind planmäßig wiederkehrend zu untersuchen ... soll eine Untersuchung mindestens alle sechs Jahre durchgeführt werden ... Über die Untersuchungen der Fahrzeuge sind Nachweise zu führen.\") -- confirmed directly on gesetze-im-internet.de"
                           :evidence [:periodic-inspection-record :inspection-interval-compliance]}}}

   ;; South Korea -- WebFetch/curl-verified 2026-07-23 directly against
   ;; elaw.klri.re.kr (the Korea Legislation Research Institute's official
   ;; English statute-translation portal; Railroad Safety Act / 철도안전법,
   ;; hseq=55943, via /eng_service/lawViewContent.do?hseq=55943#<joSeq>) and
   ;; main.kotsa.or.kr (한국교통안전공단, TS / Korea Transportation Safety
   ;; Authority, the government-designated inspection body's own site).
   ;; Deliberately scoped to TWO requirements (design type-approval, and
   ;; post-manufacture completion inspection) rather than force-matching
   ;; JPN/USA/GBR's more granular structural/braking/electrical breakdown --
   ;; Article 26(3) itself routes the technical standards for rolling stock
   ;; to a separately published Ministerial notification (고시) that this
   ;; iteration did not fetch, so honestly left out rather than guessed by
   ;; analogy (same pattern as :FRA/:DEU). Note: the elaw.klri.re.kr English
   ;; translation reflects amendments through Act No. 17746 (Dec. 22, 2020)
   ;; and carries KLRI's own disclaimer that it is "for reference only, and
   ;; [is] neither official nor legally effective"; this iteration did not
   ;; independently check for post-2020 Korean-only amendments to Articles
   ;; 26/26-6 specifically, and lbox.kr (an alternate Korean statute mirror)
   ;; returned HTTP 403 rather than a bot-detection challenge, so it was left
   ;; unreachable rather than probed further.
   :KOR
   {:name "South Korea"
    :requirements
    {:vehicle-authorization {:description "A person who intends to manufacture or import rolling stock to be operated domestically must obtain type approval for the rolling stock's design from the Minister of Land, Infrastructure and Transport (MOLIT, 국토교통부); granting type approval (or approval for a later design modification) requires MOLIT to conduct an inspection for type approval verifying the rolling stock meets the technical standards for rolling stock prescribed and publicly notified by MOLIT, and operating rolling stock without this type approval is prohibited. MOLIT entrusts this type-approval inspection to the Korea Transportation Safety Authority (TS, 한국교통안전공단), which performs it through its Railroad Approval Division (철도승인처)"
                             :required true
                             :spec-basis "Railroad Safety Act (철도안전법) Article 26 (Type Approval for Rolling Stock) paragraphs (1), (3) and (5) (\"A person who intends to manufacture or import rolling stock to be operated domestically shall obtain type approval for a design for the relevant rolling stock from the Minister of Land, Infrastructure and Transport, as prescribed by Ministerial Decree of Land, Infrastructure and Transport. ... Where the Minister of Land, Infrastructure and Transport grants type approval under paragraph (1) ..., he or she shall conduct an inspection for type approval, to verify whether the relevant rolling stock meets the technical standards for rolling stock prescribed and publicly notified by the Minister of Land, Infrastructure and Transport. ... No person shall operate rolling track without obtaining type approval under paragraph (1).\"); entrustment of this inspection to TS confirmed via Article 27-3 (Entrustment of Inspection Affairs) of the same Act and independently via TS's own site citing \"철도안전법 제26조(철도차량 형식승인), 제27조(철도용품 형식승인) 및 동법 시행령 제28조의2(검사업무의 위탁)\" as its legal basis -- confirmed directly on elaw.klri.re.kr (hseq=55943) and main.kotsa.or.kr"
                             :evidence [:molit-type-approval :ts-type-approval-inspection :technical-standards-compliance]}
     :completion-inspection {:description "A manufacturer approved under Article 26-3 (Approval of Manufacturers of Rolling Stock) must undergo a completion inspection, executed by MOLIT, verifying that the rolling stock as actually manufactured conforms to the type approval obtained under Article 26; MOLIT issues a certificate of completion inspection once the rolling stock passes"
                             :required true
                             :spec-basis "Railroad Safety Act (철도안전법) Article 26-6 (Completion Inspection of Rolling Stock) paragraphs (1)-(2) (\"A person who has obtained approval as a manufacturer pursuant to Article 26-3 shall undergo a completion inspection executed by the Minister of Land, Infrastructure and Transport to verify whether the relevant rolling stock has been manufactured in accordance with the type approval obtained under Article 26. ... Where rolling stock has passed the completion inspection pursuant to paragraph (1), the Minister of Land, Infrastructure and Transport shall issue a certificate of completion inspection for rolling stock prescribed by Ministerial Decree of Land, Infrastructure and Transport to a manufacturer of rolling stock.\") -- confirmed directly on elaw.klri.re.kr (hseq=55943)"
                             :evidence [:completion-inspection-cert :type-approval-conformance]}}}})

;; ----------------------------- coverage reporting (honest) -----------------------------

(defn coverage
  "Report what fraction of worldwide jurisdictions have official spec-basis
  in this catalog. Honest about out-of-scope coverage."
  []
  (let [catalog-count (count catalog)
        world-jurisdictions 194]
    {:implemented catalog-count
     :worldwide-jurisdictions world-jurisdictions
     :coverage-pct (* 100.0 (/ catalog-count world-jurisdictions))
     :note "Starting catalog to prove governor contract end-to-end, not global coverage claim"}))

;; ----------------------------- helpers -----------------------------

(defn requirement-citations
  "Get all official citations for a jurisdiction's requirements."
  [jurisdiction]
  (get-in catalog [jurisdiction :requirements]))

(defn required-evidence-satisfied?
  "Check if a checklist satisfies this jurisdiction's evidence requirements."
  [jurisdiction checklist]
  (let [reqs (get-in catalog [jurisdiction :requirements])]
    (every? (fn [[_req-key req-spec]]
              (if (:required req-spec)
                (let [evidence-keys (set (:evidence req-spec))]
                  (every? #(contains? checklist %) evidence-keys))
                true))
            reqs)))
