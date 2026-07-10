# CBPR+ Module — Validation Design Report

The `iso20022-cbpr` module validates three ISO 20022 MX payment message types — **pacs.008.001.08**
(FI-to-FI Customer Credit Transfer), **pacs.009.001.08** (Financial Institution Credit Transfer) and
**pain.001.001.09** (Customer Credit Transfer Initiation) — against the CBPR+ Standards Release 2026
(SR2026) rule set, centred on the SWIFT 14 November 2026 structured-address mandate. This report
documents the two mandated two-candidate algorithm investigations that shaped the validator's design —
**A1 (model navigation)** and **A2 (rule organization)** — and then proves, with an exhaustive
rule-to-method coverage table, that every rule application maps to exactly one explicit check method
with **zero omissions**.

The validator's core behavioral contract governs every design decision below:

> `CbprValidator.validate(AbstractMX)` returns a `ValidationResult` and **never throws**. It evaluates
> every rule applicable to the message's concrete type, **collects all findings** (it never stops at
> the first), and reports non-compliance as returned `Finding` data rather than as exceptions.

There is intentionally **no runtime rules-interpreter**: each rule application is encoded at generation
time as an explicit Java check method, and the CBPR+ rule inventory is the single authoritative source —
no rule is invented, added, weakened or strengthened. External SWIFT/SR2026 references are background
context only. The module targets **Java 11** only.

## A1 — Model Navigation

CBPR+ rules must read party, agent, postal-address and header sub-elements scattered throughout the
parsed MX object graph. Two navigation strategies were investigated.

### Primary (chosen) — typed getter chains

Navigation proceeds through the generated model's typed getters, following the same object graph the
JAXB runtime materializes. For pacs.008, for example:

- `MxPacs00800108.getFIToFICstmrCdtTrf()` → `FIToFICustomerCreditTransferV08` → `GroupHeader93` and
  `List<CreditTransferTransaction39>`;
- parties via `PartyIdentification135.getNm()` / `getPstlAdr()` / `getId()`;
- agents via `BranchAndFinancialInstitutionIdentification6.getFinInstnId()` →
  `FinancialInstitutionIdentification18.getBICFI()`.

The pacs.009 and pain.001 rules follow the analogous typed chains: `MxPacs00900108.getFICdtTrf()` →
`FinancialInstitutionCreditTransferV08`, and `MxPain00100109.getCstmrCdtTrfInitn()` →
`CustomerCreditTransferInitiationV09` → `List<PaymentInstruction30>` → `List<CreditTransferTransaction34>`.

**Advantages:**

- **Compile-safe** — every navigation step is checked by the Java compiler; a wrong element name is a
  compile error, not a silent runtime miss.
- **Refactor-safe** — the generated model is on the compile classpath, so IDE refactorings and annual
  SRU regenerations surface any breaking change immediately.
- **IDE-navigable** — auto-completion and "go to definition" work end to end through the model.
- **Aligned with typed read-only consumption** of the generated model — the module consumes the
  `Mx*` / `dic` types exactly as generated, with no reflection and **no string paths that can drift**.

### Alternative (documented, not chosen) — `MxNode` path-based navigation

`iso20022-core` provides `MxNode`, a generic tree of the parsed XML that supports string / XPath-like
traversal — for example `findFirst(String path)`, `singlePathValue(String path)` and
`findFirstByName(String name)`.

**Advantages:**

- **Uniform and message-agnostic** — one traversal idiom works for any message type without importing
  the concrete generated classes.

**Drawbacks:**

- **Stringly-typed paths** — element paths are string literals with no compile-time checking.
- **No compile-time safety** — a typo or an SRU element rename fails silently at runtime (a `null`
  node) rather than at build time.
- **Harder to refactor** — string paths are invisible to IDE refactorings.
- **Weaker IDE support** — no auto-completion or navigation into the model.

### Tradeoff analysis and decision

The three target message types are **fixed and known** (pacs.008.001.08, pacs.009.001.08,
pain.001.001.09) and their generated classes are already on the module's compile classpath. In that
setting the generality of `MxNode` buys nothing, while its stringly-typed paths forfeit exactly the
compile-time and refactor safety that matters most for a rule set that must stay correct across annual
SRU regenerations. **Typed getter chains are therefore chosen as the primary navigation strategy;**
`MxNode` is retained only as a documented alternative.

One deliberate nuance: **header-dependent rules do not use `MxNode` either.** The pain.001
`related-present-when-copydupl` rule obtains the parsed header via the message's `getAppHdr()` (reusing
the existing `AppHdrParser`, which defaults to the CBPR+ `BAH_V2` header) and **downcasts it to
`BusinessAppHdrV02`** to read `getCpyDplct()` and `getRltd()`. This reuses existing header handling
verbatim — no new header parser is written, and **no change to `iso20022-core` is required.**

| Criterion | Typed getter chains (chosen) | `MxNode` path-based (alternative) |
|---|---|---|
| Compile-time safety | Yes — every step checked by the compiler | No — string paths unchecked |
| Refactor / SRU-regen safety | Breakages surface at build time | Breakages surface at runtime (silent `null`) |
| IDE support | Full auto-completion and navigation | Minimal (opaque string paths) |
| Generality across message types | Bound to the three known types | Uniform for any message type |
| Fit for a fixed, known rule set | Strong | Weak (generality unused) |
| Path-drift risk | None (no string paths) | High (literal string paths) |

## A2 — Rule Organization

The 21 rule applications must be organized so that each maps to exactly one explicit, independently
testable check. Two organizations were investigated.

### Primary (chosen) — one class per rule

Each rule application is its own class, grouped by message subpackage under
`com.prowidesoftware.swift.cbpr.rules`:

- `rules/pacs008/` — 9 classes;
- `rules/pacs009/` — 7 classes;
- `rules/pain001/` — 5 classes.

Every rule class implements the shared functional contract `CbprRule<T extends AbstractMX>`, exposing
**exactly one public `check(...)` method** that returns `List<Finding>` (an empty list means the
message complies with that rule; the method never returns `null` and never throws). The message-specific
navigation paths are bound inside each class against its concrete message type (for example
`CbprRule<MxPacs00800108>`), while cross-cutting logic is delegated to the Group-2 helpers so the rule
classes stay thin:

- `PostalAddressClassifier` — Structured / Hybrid / Unstructured classification (the hero rule);
- `BicValidator` — the ISO 9362 BICFI pattern;
- `FinXCharset` — the FIN-X base and extended character sets.

Each rule class holds a `private static final String RULE_ID` (the exact kebab-case identifier from the
inventory) and a public no-arg constructor so the validator can instantiate it directly.

### Alternative (documented, not chosen) — grouped methods per message

The alternative is a single validator class per message type, with one private method per rule
(`checkStructuredAddress(...)`, `checkBicfiFormat(...)`, and so on).

**Advantages:**

- **Fewer files** — three classes instead of twenty-one.

**Drawbacks:**

- **Large classes** — each message class accretes every rule, mixing unrelated concerns.
- **Weaker single-responsibility** — a change to one rule risks the whole message class.
- **Harder to test one rule in isolation** — private methods cannot be unit-tested directly without
  going through the whole message validator.
- **Harder to prove a 1:1 rule mapping** — private methods are not enumerable the way a package of
  named public classes is.

### Tradeoff analysis and decision

**One class per rule is chosen.** It makes the "exactly one explicit Java validation method per rule"
mandate literal and independently testable, keeps each class small and focused, and — because it groups
the classes by message subpackage — cleanly resolves the fact that several rule **simple names repeat**
across message types. The modest cost of more files is far outweighed by the clarity of the one-to-one
mapping that the rule-to-method coverage table below depends on.

### Validator dispatch design

`CbprValidator` exposes the single entry point `ValidationResult validate(AbstractMX message)` and
dispatches by concrete type using `instanceof` on `MxPacs00800108`, `MxPacs00900108` and
`MxPain00100109`. Three private factory methods assemble the ordered rule lists — `pacs008Rules()` (9),
`pacs009Rules()` (7) and `pain001Rules()` (5), for **21 rules in total**. Because the rule **simple
names repeat** across the three subpackages, the validator instantiates each rule by its
**fully-qualified class name** (for example
`new com.prowidesoftware.swift.cbpr.rules.pacs008.StructuredAddressRule()`), which avoids import
collisions without any aliasing.

A generic `runRules(...)` helper drives execution: it wraps **each** rule invocation in an individual
`try/catch` so that a rule which encounters a `null` branch or a malformed element yields findings (or a
clean pass) rather than propagating an exception. Any escaping `RuntimeException` is caught, logged and
swallowed, and the remaining rules still run. This is what guarantees the validator **never throws** and
**aggregates every finding** into the returned result. Both `validate(null)` and any unsupported MX type
return an **empty, valid result** (no rule set applies, so there is nothing to report).

### Result and finding model

- **`ValidationResult`** has exactly two members: a `boolean valid` and a `List<Finding> findings`
  (defensively copied into an unmodifiable list, never `null`). Validity is strictly **derived**:
  `valid` is `true` **if and only if no finding has `Severity.ERROR`**. A result carrying only
  `WARNING`-severity findings therefore **remains valid**, and the flag can never inconsistently claim
  validity while an error is present.
- **`Finding`** is an immutable value object with exactly four fields, in order: `String ruleId`,
  `Severity severity`, `String elementPath`, `String message`. It is a pure data carrier — it holds no
  reference to the message, performs no validation of its own, and tolerates `null` attributes without
  throwing.
- **`Severity`** is an enum with exactly two constants: `ERROR` and `WARNING`.

## Rule-to-Method Coverage (21 applications, zero omissions)

The CBPR+ inventory comprises **12 distinct rule IDs** that manifest as **21 message-scoped
applications** (pacs.008 = 9, pacs.009 = 7, pain.001 = 5). Each application maps to exactly one rule
class exposing exactly one public `check(...)` method. The three tables below enumerate that mapping in
full; every rule-id literal is reproduced verbatim in kebab-case.

**Table 1 — pacs.008.001.08 (9 applications).** Package `com.prowidesoftware.swift.cbpr.rules.pacs008`;
each class `implements CbprRule<MxPacs00800108>`.

| # | Rule ID | Severity | Rule class (one public check method) | Package |
|---|---|---|---|---|
| 1 | `structured-address-min-town-country` | error | `StructuredAddressRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 2 | `party-name-mandatory-when-address-present` | error | `PartyNameWhenAddressPresentRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 3 | `creditor-name-mandatory-when-no-anybic` | error | `CreditorNameWhenNoAnyBicRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 4 | `party-anybic-excludes-name-address` | error | `PartyAnyBicExcludesNameAddressRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 5 | `bicfi-format` | error | `BicfiFormatRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 6 | `agent-point-to-point-bicfi-mandatory` | error | `AgentPointToPointBicfiMandatoryRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 7 | `charset-finx-extended-for-name-address` | error | `CharsetFinxExtendedRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 8 | `instructed-agent-group-vs-tx` | error (R6) | `InstructedAgentGroupVsTxRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |
| 9 | `interbank-settlement-date-presence` | error (R11) | `InterbankSettlementDatePresenceRule` | `com.prowidesoftware.swift.cbpr.rules.pacs008` |

**Table 2 — pacs.009.001.08 (7 applications).** Package `com.prowidesoftware.swift.cbpr.rules.pacs009`;
each class `implements CbprRule<MxPacs00900108>`.

| # | Rule ID | Severity | Rule class (one public check method) | Package |
|---|---|---|---|---|
| 1 | `structured-address-min-town-country` | error | `StructuredAddressRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |
| 2 | `fi-party-bicfi-preferred` | warning | `FiPartyBicfiPreferredRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |
| 3 | `bicfi-format` | error | `BicfiFormatRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |
| 4 | `agent-national-clearing-code-only` | warning | `AgentNationalClearingCodeOnlyRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |
| 5 | `instructed-agent-group-vs-tx` | error (R6) | `InstructedAgentGroupVsTxRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |
| 6 | `interbank-settlement-date-presence` | error (R11) | `InterbankSettlementDatePresenceRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |
| 7 | `charset-finx-extended-for-name-address` | error | `CharsetFinxExtendedRule` | `com.prowidesoftware.swift.cbpr.rules.pacs009` |

**Table 3 — pain.001.001.09 (5 applications).** Package `com.prowidesoftware.swift.cbpr.rules.pain001`;
each class `implements CbprRule<MxPain00100109>`.

| # | Rule ID | Severity | Rule class (one public check method) | Package |
|---|---|---|---|---|
| 1 | `structured-address-min-town-country` | error | `StructuredAddressRule` | `com.prowidesoftware.swift.cbpr.rules.pain001` |
| 2 | `party-name-mandatory-when-address-present` | error | `PartyNameWhenAddressPresentRule` | `com.prowidesoftware.swift.cbpr.rules.pain001` |
| 3 | `bicfi-format` | error | `BicfiFormatRule` | `com.prowidesoftware.swift.cbpr.rules.pain001` |
| 4 | `related-present-when-copydupl` | warning (R1) | `RelatedPresentWhenCopyDuplicateRule` | `com.prowidesoftware.swift.cbpr.rules.pain001` |
| 5 | `charset-finx-extended-for-name-address` | error | `CharsetFinxExtendedRule` | `com.prowidesoftware.swift.cbpr.rules.pain001` |

**Total: 9 + 7 + 5 = 21 applications; 12 distinct rule IDs; 0 omissions.**

Six simple class names — `StructuredAddressRule`, `BicfiFormatRule`, `CharsetFinxExtendedRule`,
`InstructedAgentGroupVsTxRule`, `InterbankSettlementDatePresenceRule` and
`PartyNameWhenAddressPresentRule` — recur across the subpackages. This is intentional: the rule text
differs per message type (for example, in pacs.009 the parties are **financial institutions** rather
than customers, so the "same" rule inspects a different object shape), so each occurrence is a distinct
class bound to its own message type with its own `check(...)`. That repetition of simple names across
message scopes is exactly why the per-message subpackage layout — and the validator's
fully-qualified instantiation of each rule — are used.

## Per-Rule Scope Notes

- **pacs.008 `StructuredAddressRule` (hero rule)** — walks the group agents (`InstgAgt` / `InstdAgt`)
  plus the five transaction parties (`Dbtr` / `Cdtr` / `UltmtDbtr` / `UltmtCdtr` / `InitgPty`) and the
  transaction agents, delegating the Structured / Hybrid / Unstructured decision to
  `PostalAddressClassifier.isCompliant`. BIC-only agents (a `null` `PstlAdr`) carry no postal address
  and are exempt.
- **pacs.009 rules** — operate on **financial-institution** parties
  (`BranchAndFinancialInstitutionIdentification6`) rather than customer parties; the hero rule likewise
  exempts FIs with a `null` `PstlAdr` (BIC-only). `fi-party-bicfi-preferred` returns **no finding** when
  a BICFI is present (the preferred identification) and only a **`WARNING`** otherwise — it is **never
  escalated to `ERROR`**. `agent-national-clearing-code-only` emits **one informational `WARNING`** only
  when all agents resolve to a single country and at least one is clearing-code-only.
- **pain.001 hero-rule scope** — `Dbtr` / `UltmtDbtr` at the `PaymentInstruction30` level plus
  `Cdtr` / `UltmtCdtr` at the `CreditTransferTransaction34` level. `related-present-when-copydupl` is
  the **only** pain.001 rule that inspects the header: it reads `message.getAppHdr()`, downcasts to
  `BusinessAppHdrV02`, and emits a **`WARNING`** when `getCpyDplct()` is present but `getRltd()` is
  empty. It must remain a `WARNING`; no new header parser is introduced and no `iso20022-core` change is
  made.
- **`bicfi-format` (all three message types)** — delegates to `BicValidator` (the ISO 9362 pattern) and
  flags only a **present-but-malformed** BICFI; an absent or blank BICFI is silent (mandatory presence
  is governed by other rules, such as `agent-point-to-point-bicfi-mandatory`).
  **`charset-finx-extended-for-name-address` (all three)** — delegates to `FinXCharset.isValidExtended`
  over the Name and address fields, which additionally permit the extended punctuation set beyond the
  FIN-X base charset.

## Companion Deliverables

This report (A1 and A2) is one of five markdown deliverables for the `iso20022-cbpr` module:

- **`structured_address_report.md`** — the A3 investigation (structured-address detection) and the
  authoritative hero-rule (`structured-address-min-town-country`) classification semantics
  (Structured / Hybrid / Unstructured).
- **`migration_report.md`** — the A4 investigation (MT→MX address mapping) and the per-pair mapping
  tables for the MT103→pacs.008, MT202→pacs.009 and MT101→pain.001 flows.
- **`pre_implementation_analysis.md`** — the up-front repository and requirements analysis.
- **`final_summary.md`** — the capability-versus-commercial-gap analysis and the next candidate message
  types.

