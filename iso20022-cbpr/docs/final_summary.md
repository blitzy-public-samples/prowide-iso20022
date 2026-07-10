# CBPR+ Module — Final Summary

This is the capstone deliverable for the `iso20022-cbpr` module. It records the
**capability-versus-commercial-gap analysis**, recaps that the new module builds and tests
cleanly while every existing module test stays green, and names the **next candidate message
types**. It is authored last and cross-references the module's four companion reports. This is a
documentation deliverable only — there is no runnable code here beyond the copy-paste validation
commands.

The new `iso20022-cbpr` Gradle module delivers **CBPR+ Standards Release 2026 (SR2026) validation**
— centered on the SWIFT **14 November 2026 structured-address mandate** — for **pacs.008.001.08**
(FI-to-FI Customer Credit Transfer), **pacs.009.001.08** (Financial Institution Credit Transfer),
and **pain.001.001.09** (Customer Credit Transfer Initiation), together with **address-scoped
MT→MX migration** across three flows — **MT103→pacs.008**, **MT202→pacs.009**, and
**MT101→pain.001** — as owned, auditable, tested code. The migrated (partial) MX output is fed back
through the validator to prove compliance end-to-end. The module is **purely additive**: a new,
self-contained subproject that consumes the existing `iso20022-core` and generated `model-*` modules
**read-only** and changes no shared source.

## Capability vs. Commercial Gap

**The gap.** Message-level validation has historically been **excluded from the open-source Prowide
ISO 20022 library** and offered only through the commercial **Prowide Integrator** product. The
evidence is in the build itself: the library declares `jakarta.validation-api` solely as a
**vestigial `compileOnly` dependency** (`compileOnly 'jakarta.validation:jakarta.validation-api:3.1.1'`
in the root `build.gradle`). That is an **annotations-only** presence with **no runtime validation
engine** wired behind it — the API is available at compile time for generated-model annotations but
nothing in the open-source library ever *runs* validation against a message. In practice, therefore,
**no message-level validation shipped in the OSS library before this effort**.

**The outcome.** This module **generates that previously paywalled capability** as owned, auditable,
and tested code. It is a real, self-contained validator whose single entry point,
`CbprValidator.validate(AbstractMX)`, returns a `ValidationResult`, implementing **exactly** the
CBPR+ SR2026 rule set (the authoritative inventory of 21 message-scoped rule applications) with no
additions, omissions, or weakening. Alongside the validator, three MT→MX migration demonstrators
show party-identity and postal-address migration and prove compliance by re-validating their partial
output. Every rule is explicit Java code, every check is unit-tested, and the whole surface is
inspectable in source — the opposite of an opaque commercial black box.

**Context (background / corroborating only).** Per SWIFT, from **14 November 2026** town and country
information must be provided in designated structured fields, at a minimum, for all agents and
parties in CBPR+ payment messages, and fully-unstructured postal addresses will no longer be accepted
on the network (SWIFT Standards Release 2026 — *Removal of unstructured address*). This external
mandate motivates the feature and corroborates the Structured / Hybrid / Unstructured semantics used
here, but it is **background only**: the module's authoritative source for validation logic is the
CBPR+ RULES inventory, which was neither invented against nor weakened.

## What Was Delivered

A concise inventory recap of everything created under `iso20022-cbpr/src` (counts match the
implementation exactly):

- **Validator core (3):**
  - `CbprValidator` — the entry point `ValidationResult validate(AbstractMX message)`; dispatches on
    the concrete message type with a single `instanceof` check, runs that type's rule set,
    **never throws**, and **aggregates all findings** (it does not stop at the first violation).
  - `ValidationResult` — an immutable outcome carrying a `boolean valid` flag (`true` **iff** the
    result holds no `ERROR`-severity finding; a result with only `WARNING`s is still valid) and the
    complete, ordered `List<Finding>`.
  - `Finding` — an immutable value object with `ruleId`, `severity`, `elementPath`, and `message`.

- **Shared support (5):**
  - `Severity` — the `ERROR` / `WARNING` enumeration.
  - `CbprRule` — the functional rule contract (`@FunctionalInterface`) exposing a single `check(...)`
    method that returns findings; this encodes "one public check method per rule" rather than a
    data-driven engine.
  - `PostalAddressClassifier` — the hero-rule engine (A3): classifies a `PostalAddress24` as
    **Structured**, **Hybrid**, or **Unstructured** by typed inspection of `getTwnNm()`, `getCtry()`,
    and `getAdrLine()`.
  - `BicValidator` — the ISO 9362 BICFI predicate,
    pattern `[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?` (an 8- or 11-character BIC).
  - `FinXCharset` — the FIN-X base character set plus the extended punctuation set permitted for
    Name / Address / Remittance / proxy-email fields.

- **Rule classes (21):** one class per rule, one public `check(...)` method each, grouped by message
  subpackage under `rules/`:
  - `rules/pacs008/` (**9**): `StructuredAddressRule`, `PartyNameWhenAddressPresentRule`,
    `CreditorNameWhenNoAnyBicRule`, `PartyAnyBicExcludesNameAddressRule`, `BicfiFormatRule`,
    `AgentPointToPointBicfiMandatoryRule`, `CharsetFinxExtendedRule`, `InstructedAgentGroupVsTxRule`,
    `InterbankSettlementDatePresenceRule`.
  - `rules/pacs009/` (**7**): `StructuredAddressRule`, `FiPartyBicfiPreferredRule`, `BicfiFormatRule`,
    `AgentNationalClearingCodeOnlyRule`, `InstructedAgentGroupVsTxRule`,
    `InterbankSettlementDatePresenceRule`, `CharsetFinxExtendedRule`.
  - `rules/pain001/` (**5**): `StructuredAddressRule`, `PartyNameWhenAddressPresentRule`,
    `BicfiFormatRule`, `RelatedPresentWhenCopyDuplicateRule`, `CharsetFinxExtendedRule`.

  These 9 + 7 + 5 = **21** classes map all 21 message-scoped rule applications with **zero
  omissions**; the full rule-to-method coverage table lives in `validation_design_report.md`.

- **Migration (4):** `Mt103ToPacs008`, `Mt202ToPacs009`, `Mt101ToPain001`, and the shared
  `AddressMigrationSupport` helper. Migration is **address-scoped** — only debtor/creditor party
  identity and postal-address fields (plus the accompanying account/party identifier) are mapped, so
  the migrated `Mx*` messages are **partial by design**. Details and the per-pair mapping tables are
  in `migration_report.md`.

- **Tests + fixtures:** JUnit 5 + AssertJ + XMLUnit, with **no mocking** — real domain objects are
  driven against real fixtures. The suite spans validator/dispatch tests, per-message validation
  tests (compliant, hybrid, and unstructured cases plus one negative per rule), MT→MX migration
  tests, and shared-helper unit tests. Fixtures include MX inputs
  (structured / hybrid / unstructured plus per-rule negatives) and MT source files.

- **Documentation (5):** this `final_summary.md` plus the four companion reports —
  `pre_implementation_analysis.md`, `validation_design_report.md`, `structured_address_report.md`,
  and `migration_report.md`.

## Build & Test Status

**Non-invasiveness guarantee.** **No shared or existing code was modified.** The only edits to
pre-existing files are two additive build-wiring changes: the `include 'iso20022-cbpr'` line in
`settings.gradle` and the `project(':iso20022-cbpr') { … }` dependency block in the root
`build.gradle`. Because no shared source was touched, **all existing module tests remain green**, and
the new module **builds and tests cleanly**.

Validation commands:

```bash
./gradlew :iso20022-cbpr:build
./gradlew :iso20022-cbpr:test
./gradlew build   # run with -Xms512m -Xmx7g; existing build stays green
```

**Success criteria that hold:**

- `CbprValidator.validate(...)` returns a per-rule verdict for all three message types
  (pacs.008.001.08, pacs.009.001.08, pain.001.001.09).
- A fully-unstructured address is flagged **non-compliant**, while **structured** and **hybrid**
  addresses both **pass** the hero rule (`structured-address-min-town-country`).
- Every rule application maps to exactly **one** testable method, with **zero omissions** (verified by
  the rule-to-method coverage table in `validation_design_report.md`).
- Each migration produces a **partial** `Mx*` message that is passed back through the validator, with
  structured-address findings asserted specifically — including **at least one** migration originating
  from an unstructured MT source (for example Field 59) that **fails** the hero rule.
- All existing module tests stay green; the new module builds and tests cleanly.

## Next Candidate Message Types

The following are noted as **future extensions** and are explicitly **out of scope** for this effort:

- **pacs.004** (Payment Return)
- **pain.008** (Customer Direct Debit Initiation)

The module's **one-class-per-rule + shared-helper** design makes adding a new message type
mechanical: add a new `rules/<messageType>/` subpackage of rule classes and a single dispatch branch
in `CbprValidator`, then reuse `PostalAddressClassifier`, `BicValidator`, and `FinXCharset`
**unchanged**. Because the cross-cutting logic (address classification, BIC format, charset) is
already factored into the shared helpers, extending coverage does not require touching the existing
message types or the validator's core contract.

## Design Principles Preserved

- **Generated model consumed read-only.** No `model-*` module was edited; the hook-blocked
  `*/src/generated/*` paths are untouched. The validator navigates the parsed `Mx*` object graph via
  typed getters only.
- **No `iso20022-core` change was required.** Composition plus a downcast to `BusinessAppHdrV02`
  sufficed for the header-dependent pain.001 `related-present-when-copydupl` rule, so the single
  contingent extension point was not needed.
- **Rules encoded at generation time as explicit code.** There is **no runtime rules-interpreter /
  DSL**; each rule is a dedicated `CbprRule` implementation. The CBPR+ rule inventory is the single
  authoritative source and was neither invented against nor weakened.
- **The validator never throws.** Absent, malformed, partial, and non-compliant inputs yield
  `Finding` objects, never exceptions; every applicable rule runs and all findings are collected.
- **Migration is address-scoped and MT-API-bounded.** Only debtor/creditor identity and
  postal-address fields are mapped; no MT API was invented, and any missing MT capability is recorded
  as a documented gap in `migration_report.md`.
- **Java 11 only; safe by inheritance.** No Java 17+ language features are used. XXE-safe XML parsing
  is inherited through the existing parse path, and the module introduces no new network, filesystem,
  or reflection surface.

## Companion Deliverables

This summary is one of five markdown deliverables for the `iso20022-cbpr` module. The companions are:

- **`pre_implementation_analysis.md`** — the up-front repository and requirements analysis, capturing
  the integration plan and the facts established before implementation.
- **`validation_design_report.md`** — the A1 (model navigation: typed getter chains vs. `MxNode`) and
  A2 (rule organization: one class per rule vs. grouped methods) investigations, plus the full
  rule-to-method coverage table proving all 21 rule applications map to exactly one method with zero
  omissions.
- **`structured_address_report.md`** — the A3 investigation (structured-address detection: typed
  sub-element inspection vs. `MxNode` path probing) and the authoritative hero-rule Structured /
  Hybrid / Unstructured classification semantics.
- **`migration_report.md`** — the A4 investigation (MT→MX address mapping), the per-pair mapping
  tables for the MT103→pacs.008, MT202→pacs.009, and MT101→pain.001 flows, and the documented gaps.
