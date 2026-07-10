# CBPR+ Module — MT→MX Address Migration Report (A4)

This report documents the **address-scoped MT→MX migration** capability of the `iso20022-cbpr`
module (Capability B of the CBPR+ SR2026 effort). The module demonstrates migration of legacy SWIFT
MT payment messages to their ISO 20022 MX equivalents for three flows:

- **MT103 → pacs.008.001.08** (`Mt103ToPacs008` → `MxPacs00800108`) — FI-to-FI Customer Credit Transfer;
- **MT202 → pacs.009.001.08** (`Mt202ToPacs009` → `MxPacs00900108`) — Financial Institution Credit Transfer;
- **MT101 → pain.001.001.09** (`Mt101ToPain001` → `MxPain00100109`) — Customer Credit Transfer Initiation.

Each migrator produces a **partial** MX message that is then fed back through `CbprValidator` (in the
accompanying tests) to prove the CBPR+ compliance behaviour end-to-end — in particular that the hero
rule `structured-address-min-town-country` passes for structured/hybrid addresses and fails for a
fully-unstructured address.

This document also records the mandated **A4 (MT→MX address mapping)** two-candidate algorithm
investigation, a **per-pair mapping table** for the three flows, the **bounded MT-API usage**, and the
**gap notes**. It is a plain-Markdown deliverable and contains no runnable source; the illustrative MT
field snippets below are verbatim fixture content, not code.

## Scope and Governing Constraints

The migration is deliberately narrow, and stating its boundaries up front prevents any
misinterpretation of the mapping tables that follow.

- **Strictly address-scoped.** Only the debtor/creditor **party identity** (party `Nm`, and — for the
  BIC-only options — the party `AnyBIC`), the **postal address** (`PstlAdr`) and the **accompanying
  account/party identifier** are migrated. No amount, no agents, no remittance information, no dates, no
  references and no charges are mapped. The leading MT account/party-identifier line (for example
  `/12345678`) is recognised so it is never mis-read as a name or an address line, and it **is** projected
  onto the target account branch (`DbtrAcct` / `CdtrAcct`) as a `CashAccount38` whose `Id` is an
  `IBAN` when the identifier is structurally an IBAN, and otherwise a generic
  `Othr`/`GenericAccountIdentification1`.
- **Partial by design.** Because only party identity and postal address are populated, **every other
  branch of every target message is intentionally left `null`** — group header, agents, amounts,
  settlement dates, payment/type information, remittance and references. This is a deliberate property,
  not an omission: a downstream validator is expected to report other (non structured-address) findings
  on these partial messages, so tests assert **specifically on the structured-address findings** rather
  than on overall `valid`.
- **MT-API-bounded.** The migrators consume the source MT **read-only** through the public Prowide Core
  (`pw-swift-core`) MT API only. **No new MT parser is written and no MT API is invented.** The
  `pw-swift-core` source was not read; the migration relies solely on the documented MT API REFERENCE
  signatures (see [MT API Usage](#mt-api-usage-bounded-to-the-reference)). Any capability absent from
  that reference is recorded as a documented gap rather than invented.
- **Read-only model consumption, new output construction.** The generated `Mx*` / `dic` model classes
  are never modified. Each migrator *builds* a brand-new target `Mx*` object via the generated fluent
  setters; constructing output is legitimate and is not a mutation of any generated class.
- **Java 11 only.** No Java 17+ language features are used. The hero-rule classification semantics
  (Structured / Hybrid / Unstructured) are defined authoritatively in `structured_address_report.md`
  and are only referenced here; this report never invents or weakens a rule.

## A4 — MT→MX Address Mapping

The A4 investigation concerns **how to translate an MT party's name-and-address content into an ISO
20022 `PostalAddress24`**. Two candidate mapping strategies were considered; both are ultimately
required, because the module must demonstrate *both* a compliant migration and a mandated failing
migration.

### Candidate (a) — Structured "F"-field line-semantic mapping

The structured SWIFT party fields — Field **50F** / **59F** for customer parties (on both MT103 and the
MT101 Sequence B) — encode their content as **numbered subfield lines** of the form `N/detail`. Only these
numbered "F" fields drive this candidate; the MT101 account-based options `50H` and `50G` are **not**
numbered "F" fields and are covered by candidate (b) and the BIC-only path respectively (see
[Decision](#decision)). The line semantics applied by the migration are:

- line **`1/` = Name**;
- line **`2/` = Address Line**;
- line **`3/` = Country and Town**, formatted `CC/Town`, where `CC` is the ISO 3166 two-letter country
  code and the remainder is the town name.

These map onto a `PostalAddress24` as follows:

- each **`2/`** line becomes an `AdrLine` entry;
- the **`3/`** line is split on the **first `/`** into `Ctry` (the `CC`) and `TwnNm` (the town remainder);
- the **`1/`** line (Name) is used for the party `Nm` and is *not* placed in the address; lines `4/`–`8/`,
  if present, are intentionally ignored because only town and country are relevant to the CBPR+
  structured-address mandate — no additional mapping is invented.

The resulting address lets the validator's classifier reach the correct verdict:

- Town + Country present with **no** `AdrLine` → **Structured** (passes the hero rule);
- Town + Country present with **one or more** `AdrLine` → **Hybrid** (passes the hero rule).

The mere presence or absence of `2/` lines therefore naturally selects Structured versus Hybrid; both
outcomes are CBPR+ compliant.

### Candidate (b) — Unstructured field mapping

The free-format party fields — Field **59** (no option letter) and Field **50K** on MT103, and Field
**50H** (account plus unstructured name-and-address) on the MT101 Sequence B — carry name-and-address
lines with **no numbered structure**. These map to an **`AdrLine`-only** partial `PostalAddress24`:
after skipping any leading account/party-identifier line, the first meaningful line is taken as the
party `Nm` (and is not added to the address), and every subsequent non-blank line is appended as an
`AdrLine`. Crucially, **no `TwnNm` and no `Ctry` are set** — none is present in the source, and none is
ever inferred.

The product is therefore a **fully-unstructured** address that **deliberately FAILS** the hero rule
`structured-address-min-town-country`. This is the intentional negative path: it exists precisely to
prove that the validator flags non-compliance end-to-end, and it must never be "upgraded" into a
structured address.

### Decision

**Both candidates are implemented.** They are not mutually exclusive alternatives to choose between;
they are complementary behaviours selected at run time by *which MT field option is actually present*
on the source message. A structured `50F`/`59F` drives candidate (a); a free-format `59`/`50K` — and the
MT101 account-plus-unstructured `50H` — drives candidate (b). The MT101 account-plus-BIC `50G` and the
BIC-only customer options (`50A`/`59A` on MT103, `59A` on MT101) belong to **neither** candidate: they
carry no postal address, so no `PostalAddress24` is built for them (see [Gap Notes](#gap-notes)); instead
their party BIC identity is preserved as `Id/OrgId/AnyBIC`. This dual implementation is required to
satisfy the module's objectives: demonstrate a **compliant** migration (candidate a) *and* the
**mandated failing** migration (candidate b) that proves the validator's non-compliance detection.

Both candidates are encapsulated in a single shared helper so the three migrators stay thin and the
line-semantics live in exactly one place.

### The shared helper — `AddressMigrationSupport`

`AddressMigrationSupport` is a **`pw-swift-core`-free** static utility (a `final` class with a private
constructor; it cannot be instantiated). By design it depends on **no** MT-API type: it operates purely
on plain `List<String>` line inputs that the migrators extract from the MT fields. Keeping the helper
MT-API-free confines every dependency on the Prowide Core MT field model to the three migrator classes
and makes the address-assembly logic trivially unit-testable in isolation.

It recognises numbered lines with a single regular expression — `^\s*([1-9])\s*/(.*)$` — where group 1
is the line number (`1`–`9`) and group 2 is the detail. A leading account/party-identifier line such as
`/12345678` does **not** match (it has no leading digit before the slash), so it is naturally excluded
from the numbered-line address parsing; it is captured separately by `accountIdentifierFromLines` and
projected onto the target account branch by `accountFromLines`.

Its public methods are:

- **`nameFromLines(List<String>)`** — extracts the party name: from the `1/` line when the field is a
  structured "F" field, otherwise from the first non-blank line that is not an account/party-identifier
  line. Returns `null` when no name can be determined.
- **`structuredAddressFromFFieldLines(List<String>)`** — implements **candidate (a)**: builds a
  Structured or Hybrid `PostalAddress24` from the numbered lines (`2/` → `AdrLine`, `3/` → `Ctry` +
  `TwnNm`).
- **`unstructuredAddressFromLines(List<String>)`** — implements **candidate (b)**: builds the
  `AdrLine`-only partial `PostalAddress24` (no `TwnNm`/`Ctry`) that is expected to fail the hero rule.
- **`accountIdentifierFromLines(List<String>)`** — returns the leading account/party identifier (the
  content of the first `/…` line, with the leading `/` stripped), or `null` when none is present, so the
  accompanying identifier is never silently discarded.
- **`accountFromLines(List<String>)`** — builds a `CashAccount38` from that identifier, placing it on
  `Id/IBAN` when it is structurally an IBAN and otherwise on `Id/Othr/Id`
  (`GenericAccountIdentification1`); returns `null` when the field carries no identifier. The migrators
  attach the result to the target `DbtrAcct` / `CdtrAcct` branch.

Two implementation notes matter for fidelity:

- The generated `PostalAddress24` exposes **no `setAdrLine` setter**; its `getAdrLine()` returns a
  never-`null`, live, mutable `List<String>`. The helper therefore **appends** address lines directly
  to that live list.
- Every method is **null-safe and never throws**: `null`, empty, blank or malformed input yields an
  empty (but non-`null`) `PostalAddress24`, a `null` name, or a `null` `CashAccount38`, and the returned
  address and account are always brand-new objects — no input is ever mutated. The helper only *builds*
  addresses and account identifiers; it never *classifies* addresses and never weakens a rule.
  Structured / Hybrid / Unstructured classification is performed later by the validator's
  `PostalAddressClassifier`.

## Per-Pair Mapping

The three migrators each expose a `translate(...)` entry point (accepting either the raw FIN `String`
or an already-parsed MT object) and build a fresh, partial target message. The table summarises the
address-scoped mapping for each flow; the per-row notes below the table give the detail.

| MT flow | MX target | Source MT party fields | MX target path | Address outcome |
|---|---|---|---|---|
| MT103 → pacs.008.001.08 | `MxPacs00800108` (`FIToFICstmrCdtTrf`) | Debtor: `50F` / `50K` / `50A`; Creditor: `59F` / `59` / `59A` | Parties → `.../CdtTrfTxInf/Dbtr` and `.../CdtTrfTxInf/Cdtr` as `PartyIdentification135` (`Nm` + `PstlAdr`, or `Id/OrgId/AnyBIC` for the BIC options); accounts → `.../CdtTrfTxInf/DbtrAcct` and `.../CdtTrfTxInf/CdtrAcct` as `CashAccount38` | Option-dependent: `50F`/`59F` → Structured/Hybrid (pass); `50K`/`59` → unstructured (fails hero); `50A`/`59A` → BIC (`AnyBIC`), no address |
| MT202 → pacs.009.001.08 | `MxPacs00900108` (`FICdtTrf`) | Beneficiary institution: `58A` (BIC only, e.g. `IRVTUS3NXXX`) | `.../CdtTrfTxInf/Cdtr/FinInstnId/BICFI` via `BranchAndFinancialInstitutionIdentification6` → `FinancialInstitutionIdentification18` | **No `PstlAdr`** → hero rule **N/A** (BIC-exempt) |
| MT101 → pain.001.001.09 | `MxPain00100109` (`CstmrCdtTrfInitn`) | Ordering customer: `50F` / `50H` / `50G`; Beneficiary: `59F` / `59` / `59A` (Sequence B, first occurrence) | Debtor party → `.../PmtInf/Dbtr` (account → `.../PmtInf/DbtrAcct`); Creditor party → `.../PmtInf/CdtTrfTxInf/Cdtr` (account → `.../PmtInf/CdtTrfTxInf/CdtrAcct`) — parties as `PartyIdentification135`, accounts as `CashAccount38` | Option-dependent: `50F`/`59F` → Structured/Hybrid (pass); `50H`/`59` → unstructured (fails hero); `50G`/`59A` → BIC (`AnyBIC`), no address |

**MT103 → pacs.008.001.08** — `Mt103ToPacs008.translate(String fin)` parses the FIN and requires an
`MT103`, then maps the ordering customer (debtor) and beneficiary customer (creditor) onto a single
`CreditTransferTransaction39` inside a fresh `FIToFICustomerCreditTransferV08` body. The debtor `50a`
options are resolved in priority `50F` (structured) → `50K` (unstructured) → `50A` (BIC only); the
creditor `59a` options in priority `59F` (structured) → `59` (no-option, unstructured — the mandated
failing path) → `59A` (BIC only). Structured options delegate to
`AddressMigrationSupport.structuredAddressFromFFieldLines(...)`; the unstructured options delegate to
`AddressMigrationSupport.unstructuredAddressFromLines(...)`; the BIC-only options carry no name or
postal address and instead preserve the party BIC identity as `Id/OrgId/AnyBIC` (via
`Party38Choice.setOrgId(new OrganisationIdentification29().setAnyBIC(...))`), inventing no address.
Independently of the option, the accompanying account/party identifier is projected onto `DbtrAcct` /
`CdtrAcct` via `AddressMigrationSupport.accountFromLines(...)` when present.

**MT202 → pacs.009.001.08** — `Mt202ToPacs009.translate(...)` parses and requires an `MT202`, then
maps the beneficiary institution from Field `58A` onto the creditor financial institution of a single
`CreditTransferTransaction36` inside a fresh `FinancialInstitutionCreditTransferV08` body. The party is
a *financial institution* identified by its `BICFI`; such BIC-identified agents **carry no postal
address**, so this class deliberately builds no `PostalAddress24` and — uniquely among the three
migrators — **does not use `AddressMigrationSupport`**. The BIC is located robustly from the field's
raw value: lines are split, blank and `/`-prefixed party-identifier lines are skipped, and the last
line matching the ISO 9362 pattern `[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?` is taken as the
`BICFI`. Because the party is BIC-only, the hero rule is **not applicable** to this flow.

**MT101 → pain.001.001.09** — `Mt101ToPain001.translate(...)` parses and requires an `MT101`, then maps
the ordering customer (debtor) and beneficiary (creditor) onto a single `PaymentInstruction30` /
`CreditTransferTransaction34` pair inside a fresh `CustomerCreditTransferInitiationV09` body. Because
the ordering customer and beneficiary live in the **repetitive Sequence B**, the MT accessors return a
`List<Field*>` rather than a single occurrence; the migrator guards for a non-`null`, non-empty list and
consumes the **first** occurrence (`get(0)`) for this single-transaction demonstration. The ordering
customer is resolved in priority `50F` (structured) → `50H` (account + unstructured name-and-address,
fails hero) → `50G` (account + BIC, no address); Fields `50C` / `50L` identify the *instructing party*
and are intentionally never consulted. The beneficiary is resolved analogously `59F` → `59` → `59A`.
For the BIC-only options (`50G` / `59A`) the party identity is preserved by setting `Id/OrgId/AnyBIC`
(via `Party38Choice.setOrgId(new OrganisationIdentification29().setAnyBIC(...))`); no address is ever
invented for those options. As on MT103, the accompanying account/party identifier is projected onto
`DbtrAcct` (on the `PaymentInstruction30`) / `CdtrAcct` (on the `CreditTransferTransaction34`) via
`AddressMigrationSupport.accountFromLines(...)` when present.

Under all three rows the same address-scoped guarantee holds: **only** the debtor/creditor `Nm` and
`PstlAdr` — plus, on the BIC-only options, the party `AnyBIC` identity — together with the accompanying
account/party identifier (`DbtrAcct` / `CdtrAcct`) are populated. Every other branch (group header,
agents, amounts, settlement dates, payment/type information, remittance and references) is left `null`.
The migrated messages are **partial by design**.

## MT API Usage (bounded to the reference)

All MT parsing and field access is confined to the documented MT API REFERENCE signatures; no other
Prowide Core surface is touched.

- **Parsing entry point** — `AbstractMT.parse(...)`, which returns the concrete MT subtype.
- **Concrete MT types** — `MT103` and `MT101` (package `com.prowidesoftware.swift.model.mt.mt1xx`) and
  `MT202` (package `com.prowidesoftware.swift.model.mt.mt2xx`). Each migrator verifies the parsed type
  with an `instanceof` check.
- **Field accessors** — the single-occurrence accessors `getField50A` / `getField50F` / `getField50K`,
  `getField59` / `getField59A` / `getField59F` and `getField58A`; and, for MT101 Sequence B, the
  list-returning accessors `getField50F` / `getField50G` / `getField50H` and `getField59` /
  `getField59A` / `getField59F` (each returning `List<Field*>`).
- **Field content** — the universal `Field.getValue()` accessor is used to obtain the raw multi-line
  field value, which the migrators split on CR/LF or LF boundaries into the `List<String>` lines that
  `AddressMigrationSupport` consumes. `Field.getComponents()` is the component-based alternative
  available within the same bounded reference surface.

**Explicit statement of restraint.** Only these MT API REFERENCE signatures are used. The
`pw-swift-core` source was **not** read, fetched or looked up; **no MT parser was written**; and **no
MT API was invented**. Where a needed capability is not covered by the reference, it is recorded as a
gap below rather than fabricated.

A note on error semantics that is easy to conflate with validation: a `null`, unparseable, or
wrong-type input to a migrator is a **caller-contract violation** and is raised as an
`IllegalArgumentException`. This is deliberately **distinct** from message-level CBPR+ validation, which
is performed by `CbprValidator` and **never throws** — non-compliance there is always a returned
`Finding`.

## Gap Notes

- **MT202 carries no postal address.** A pacs.009 party is a financial institution identified by its
  `BICFI`, so the MT202 migration produces a **BIC-only** FI and the hero rule
  `structured-address-min-town-country` is **Not Applicable** (the BIC-exempt path, consistent with
  SWIFT guidance that use of the BIC only remains valid for agents). The migrated pacs.009 therefore
  carries **no structured-address finding** for that party; and because the sample `58A` value
  (`IRVTUS3NXXX`) is a valid 11-character ISO 9362 code, it carries **no `bicfi-format` finding** for
  that party either.
- **BIC-only customer options carry no address.** The MT103 `50A`/`59A` and MT101 `50G`/`59A` options
  identify the party by BIC and carry no postal address; the migration preserves the party identity by
  setting `Id/OrgId/AnyBIC` (for both MT103 and MT101) and **invents no address data**. The accompanying
  account/party identifier, when present on these options, is still migrated onto `DbtrAcct` / `CdtrAcct`.
  The absence of a postal address on the BIC-only options is a documented consequence of the source, not
  a fabricated town/country.
- **No invented MT API.** Any MT capability absent from the MT API REFERENCE is treated as a gap, not
  synthesised. No such invented API was introduced anywhere in the migration code.
- **Partial messages carry other findings.** Because the migrated MX messages populate only party
  identity, postal address and the accompanying account identifier, running them through `CbprValidator`
  will legitimately surface other (non structured-address) findings — for example missing agents or
  missing settlement dates. The migration tests therefore assert **specifically on the structured-address
  findings**, never on the overall `valid` flag.

## Fixture & Test Corroboration

The MT fixtures below are **implemented** and are parsed through the real MT API against real `Mx*`
objects; the fixture names match the committed files under `src/test/resources/` exactly. The migration
**integration tests** named below (`Mt103ToPacs008Test`, `Mt202ToPacs009Test`, `Mt101ToPain001Test`) and
the `CbprValidator` they exercise are **delivered in this module**: they compile and pass as part of the
`iso20022-cbpr` test suite. Each test parses its MT fixture, runs the address-scoped migrator's
`translate(String)` to obtain a **partial** `Mx*` message, feeds that message back through
`CbprValidator.validate(...)`, and asserts **specifically on the structured-address finding** (its
presence or absence, and its severity) rather than on the overall `valid` flag. The end-to-end verdicts
described below are therefore assertions the tests actually make against the delivered implementation.

- **`mt103_structured.txt`** — debtor `:50F:` = `1/JOHN DOE` + `3/GB/LONDON` (no `2/` line →
  **Structured**); creditor `:59F:` = `1/JANE SMITH` + `2/MAIN AVENUE 22` + `3/US/NEW YORK` (has a `2/`
  line → **Hybrid**). `Mt103ToPacs008Test.structuredMt103MigrationPassesHeroRule` migrates this message to
  a compliant pacs.008 whose debtor and creditor both pass the hero rule and asserts that **no
  `structured-address-min-town-country` finding** is present.
- **`mt103_unstructured.txt`** — the **mandated negative migration**: the debtor `:50F:` is structured
  and passes, but the creditor `:59:` (no option letter) carries free-format address lines (`JANE SMITH` /
  `123 NOWHERE STREET` / `NEW YORK NY 10001`) that map to an **`AdrLine`-only / fully-unstructured**
  address, which **FAILS** the hero rule on the creditor.
  `Mt103ToPacs008Test.unstructuredMt103MigrationFailsHeroRule` asserts that the
  `structured-address-min-town-country` finding is **present** with `Severity.ERROR` and that its element
  path identifies the creditor (`Cdtr`); it does not assert on overall validity.
- **`mt202.txt`** — `:58A:IRVTUS3NXXX` (BIC only, 11 characters).
  `Mt202ToPacs009Test.bicOnlyMt202MigrationHasNoStructuredAddressFinding` asserts that the migrated
  pacs.009 has **no** structured-address finding for the beneficiary institution (the BIC-exempt path),
  and `migratedBicfiIsValidFormat` asserts there is **no `bicfi-format` finding** while confirming — via a
  deep getter — that the beneficiary `BICFI` was actually mapped as `IRVTUS3NXXX`, so the pass is genuine
  rather than vacuous.
- **`mt101.txt`** — Sequence B ordering customer `:50F:` = `1/ROBERT BROWN` + `3/DE/BERLIN` (no `2/` →
  **Structured**) and beneficiary `:59F:` = `1/MARIA WEBER` + `2/PARK LANE 7` + `3/FR/PARIS` (has a `2/` →
  **Hybrid**). `Mt101ToPain001Test.mt101SequenceBMigrationPassesHeroRule` migrates this message to a
  compliant pain.001 and asserts that **no structured-address finding** is present for either party.

These tests use **JUnit 5 + AssertJ** with **no mocking**, exercising real MT parsing and real `Mx*`
construction throughout — consistent with the repository's no-mock testing convention. The MT103 and
MT101 tests each additionally include a `migrationDoesNotThrow` check confirming that `translate(...)`
completes without throwing for a well-formed MT source.

## Companion Deliverables

This report is one of five markdown deliverables for the `iso20022-cbpr` module. For the topics it
references but does not restate:

- **`structured_address_report.md`** — the authoritative hero-rule (`structured-address-min-town-country`)
  classification semantics (Structured / Hybrid / Unstructured) and the A3 investigation.
- **`validation_design_report.md`** — the A1 (model navigation) and A2 (rule organization) decisions and
  the full rule-to-method coverage table.
- **`pre_implementation_analysis.md`** — the up-front repository and requirements analysis.
- **`final_summary.md`** — the capability-versus-commercial gap analysis and the next candidate message
  types.
