# Business Model: Community Railway Rolling Stock Manufacturing

## Classification
- Repository: `cloud-itonami-3020`
- ISIC Rev.5: `3020` — manufacture of railway locomotives and
  rolling stock
- Social impact: rail safety, supply-chain resilience,
  decarbonization of freight/passenger movement

## Customer
- independent rolling-stock manufacturers needing an auditable
  design-authority and type-approval platform
- contract manufacturers producing locomotives, wagons and carriages
- rail operators needing verifiable production and quality records
  for procured rolling stock
- regulators needing verifiable design-authority and type-approval
  compliance records
- programs that cannot accept closed, unauditable rolling-stock
  manufacturing platforms

## Offer
- design-authority and type-approval-scope version management
- robotics-assisted fabrication, welding, assembly and non-
  destructive-testing inspection
- production history and quality records
- delivery-release and disclosure records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per production line
- support retainer with SLA
- fabrication/welding/inspection robot integration and maintenance

## Trust Controls
- a robot action the governor refuses is never dispatched
- safety-critical actions (releasing a unit that has not passed
  type-approval inspection, changing a safety-critical design
  parameter) require human sign-off
- a unit cannot be released outside its verified design-authority
  scope
- release records require source verification evidence
- sensitive design and production data stays outside Git
