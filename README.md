# cloud-itonami-3020

Open Business Blueprint for **ISIC Rev.5 3020**: manufacture of
railway locomotives and rolling stock.

This repository designs a forkable OSS business for community
rolling-stock manufacturing: design-authority and type-approval-scope
management, robotics-assisted fabrication, welding and inspection of
locomotives/wagons/carriages, and production/quality records — run by
a qualified manufacturer so a rolling-stock builder keeps its own
design-approval and inspection history instead of renting a closed
manufacturing-execution platform.

## Scope note: manufacturing, not rail operation

`cloud-itonami-isic-4911` (passenger rail) and `cloud-itonami-isic-
4912` (freight rail) are rail OPERATORS -- their own docs mention
"rolling-stock maintenance" only as an OPERATOR'S OWN upkeep of
vehicles it already owns and runs in service, never as manufacturing
new rolling stock. This repository is deliberately scoped to the
SEPARATE business of BUILDING locomotives and rolling stock: a
manufacturer whose product must pass its own type-approval/design-
authority regime before any operator can run it in service (the EU's
Technical Specifications for Interoperability under Directive
(EU) 2016/797; Japan's 鉄道車両の設計認可 rolling-stock design
authorization under the Railway Business Act, distinct from the
mvatek operator-facing 鉄道事業法 licensing that governs `4911`/`4912`;
the US FRA's rolling-stock approval under 49 CFR Parts 238/239). A
rolling-stock manufacturer sells to MULTIPLE rail operators and never
itself operates a rail service.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (welding, fabrication,
assembly-line inspection, non-destructive-testing scan robots)
operate under an actor that proposes actions and an independent
**Rolling Stock Manufacturing Governor** that gates them. The
governor never releases a unit for delivery itself;
`:high`/`:safety-critical` actions (a production step outside verified
design-authority scope, a delivery release without a completed
inspection/type-approval pass, a quality record without verified
evidence) require human sign-off.

## Core Contract

```text
intake + identity + design-authority/type-approval scope + production order
        |
        v
Rolling Stock Manufacturing Advisor -> Rolling Stock Manufacturing Governor -> production record, inspection record, release, or human approval
        |
        v
robot actions (gated) + production record + quality record + audit ledger
```

No automated advice can release a unit for delivery the governor
refuses, advance production outside its verified design-authority
scope, or publish a quality record without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `3020`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/eda`](https://github.com/kotoba-lang/eda) — design-authority artifact management
- [`kotoba-lang/cae`](https://github.com/kotoba-lang/cae) — structural/crashworthiness simulation evidence

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
