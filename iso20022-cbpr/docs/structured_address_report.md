# CBPR+ Module — Structured-Address Detection Report (A3)

This report documents the **structured-address detection algorithm** of the `iso20022-cbpr`
module and records the mandated **A3 (structured-address detection)** two-candidate algorithm
investigation. Its subject is the **hero rule** `structured-address-min-town-country`
(severity **error**), the centerpiece of the SWIFT Standards Release 2026 (SR2026) change and the
single most consequential rule in the CBPR+ SR2026 rule inventory.

Detection is **centralized in one small, reusable classifier**,
`PostalAddressClassifier`, so that the Structured / Hybrid / Unstructured decision is made
**identically everywhere**. Every message type's `StructuredAddressRule` — in the
`rules/pacs008/`, `rules/pacs009/` and `rules/pain001/` subpackages — delegates the classification
to this one class, invoking it across **every party and agent postal-address occurrence** it walks.
The rule classes are therefore thin navigators: they locate each `PstlAdr` subject to the rule and
ask the classifier whether it is compliant.

This is a plain GitHub-flavored Markdown deliverable and contains **no runnable source**; the code
identifiers below (`getTwnNm()`, `isCompliant(...)`, and so on) are references to the implemented
Java, not code to execute. The classification semantics stated in the next section are
**authoritative** and are reproduced verbatim from the CBPR+ rule inventory; they are never
invented, weakened, added to, or reinterpreted here.

## Classification Semantics

The hero rule recognizes exactly three postal-address categories. Two of them (**Structured** and
**Hybrid**) satisfy the rule; the third (**fully-unstructured**) violates it. The authoritative
definitions are:

| Classification | Condition | Verdict |
|---|---|---|
| **Structured** | `TwnNm` present **and** `Ctry` present **and no** `AdrLine` | **PASS** |
| **Hybrid** | `TwnNm` present **and** `Ctry` present **and** one or more `AdrLine` | **PASS** |
| **Fully-unstructured** | `AdrLine` present while `TwnNm` **or** `Ctry` is missing | **FAIL** |

Two exemption / edge rules qualify how the categories are applied:

- **BIC-only agents are exempt.** An agent — or, in pacs.009, an FI *party* — identified by BICFI
  only carries **no postal address** (`PstlAdr` is `null`) and is therefore **not evaluated** by the
  hero rule. This is consistent with SWIFT guidance that, for agents, use of the BIC only continues
  to be a valid option (background). The exemption is realized in the rule classes, which simply skip
  any occurrence whose `PstlAdr` branch is absent.
- **A null / absent `PostalAddress24` is not itself a finding.** The classifier's `classify(...)`
  method defensively treats a `null` address as `UNSTRUCTURED` (it never throws), but the
  `StructuredAddressRule` classes **skip evaluation when the address branch is absent** (the exemption
  above). Consequently an *absent* address never produces a finding — only a **present** address that
  is fully-unstructured fails the rule.

## A3 — Detection Approach

The prompt mandates a documented two-candidate investigation for the structured-address detection
algorithm. Both candidates were considered; the chosen approach and its rationale follow.

### Primary (chosen) — typed sub-element inspection

The classifier inspects the **typed sub-elements** of `PostalAddress24` directly, consuming the
generated model **read-only** through **exactly three getters**: `getTwnNm()`, `getCtry()` and
`getAdrLine()`. The implemented logic is:

- **Town/country presence is blank-tolerant.** `TwnNm` and `Ctry` are tested with
  `StringUtils.isNotBlank`, so that an empty or whitespace-only element such as `<TwnNm></TwnNm>` is
  treated as *absent* and does not wrongly satisfy the mandate:
  `hasTwn = StringUtils.isNotBlank(addr.getTwnNm())` and
  `hasCtry = StringUtils.isNotBlank(addr.getCtry())`.
- **Address-line presence is a non-blank test over the list, not a null check.** `getAdrLine()`
  returns a JAXB list that is **never `null`** — the generated model lazily initializes it to an empty
  `ArrayList` — so presence is tested as
  `hasAdrLine = addr.getAdrLine().stream().anyMatch(StringUtils::isNotBlank)`, i.e. *at least one
  non-blank address line*.
- **Decision.** `hasTwn && hasCtry && !hasAdrLine` → `STRUCTURED`;
  `hasTwn && hasCtry && hasAdrLine` → `HYBRID`; otherwise → `UNSTRUCTURED`. The `else` branch
  deliberately captures both the canonical failure case (address-line-only) and the defensive
  incomplete cases (town or country missing, empty address, or a `null` argument). Accordingly,
  `classify(null)` → `UNSTRUCTURED`.
- **Public surface.** The classifier is a `final` utility class exposing two `public static` helpers:
  `AddressType classify(PostalAddress24)` — returning the nested enum
  `AddressType { STRUCTURED, HYBRID, UNSTRUCTURED }` — and the convenience predicate
  `boolean isCompliant(PostalAddress24)`, which returns `true` for `STRUCTURED` **or** `HYBRID` and
  `false` for `UNSTRUCTURED`. The three `StructuredAddressRule` classes call `isCompliant(...)` for
  each non-exempt address and raise a finding when it returns `false`.
- **No `MxNode`.** The classifier deliberately **does not use** generic `MxNode` path probing (the
  documented A3 alternative below).

**Advantages.** Typed inspection is **compile-safe** (a renamed or removed getter is a compile
error, not a silent runtime miss), **refactor-safe**, **minimal** (three getters and a two-line
decision), **reusable** across all three message types and every party/agent occurrence, and
**aligned** with the module-wide convention of typed, read-only model consumption.

### Alternative (documented, not chosen) — `MxNode` path probing

The alternative resolves the same sub-elements generically, via **string paths** against the parsed
`MxNode` tree: probing `.../PstlAdr/TwnNm`, `.../PstlAdr/Ctry` and `.../PstlAdr/AdrLine` with
`MxNode.findFirst(path)` / `MxNode.singlePathValue(path)` (path separator `/`).

- **Advantages.** Message-agnostic and uniform: the same three relative path segments describe a
  postal address wherever it appears, without importing any typed `dic` class.
- **Drawbacks.** Stringly-typed with **no compile-time safety** (a mistyped or drifted path fails
  silently at runtime rather than at build time); brittle against namespace/version drift between
  `.07`/`.08`/`.09`; and — because `MxNode` paths are absolute or context-relative — the caller must
  **enumerate every party and agent path as strings** for each message type, re-introducing exactly
  the per-occurrence duplication the typed classifier avoids.

### Tradeoff analysis and decision

**Typed sub-element inspection wins.** The decisive observation is that the sub-elements are
**identical across every `PostalAddress24`** regardless of where it sits in the message graph — a
debtor's address, a creditor's address and an intermediary agent's address are all the *same* typed
object with the *same* `getTwnNm()`/`getCtry()`/`getAdrLine()` accessors. A single small typed
classifier therefore serves **all** occurrences with compile-time safety and zero per-occurrence
duplication, while the message-specific navigation (which party/agent to visit) stays in each
`StructuredAddressRule`. `MxNode` path probing remains the **documented fallback**, useful if a
future need arises to classify an address reached only generically.

| Criterion | Typed sub-element inspection (chosen) | `MxNode` path probing (alternative) |
|---|---|---|
| Compile-time safety | Yes — getter changes are compile errors | No — path errors surface only at runtime |
| Coupling to graph position | None — same three getters everywhere | High — each occurrence needs its own string path |
| Code size / reuse | Minimal; one classifier reused by all rules | Paths must be enumerated per message/occurrence |
| Refactor / version-drift safety | High — typed against the model | Low — brittle to namespace/element drift |
| Failure mode on drift | Build fails fast | Silent miss (returns nothing) |
| Decision | **Primary** | Documented fallback |

## Application Across Messages

Each message type's `StructuredAddressRule` walks the full set of postal-address occurrences in
scope for that message, delegating every Structured / Hybrid / Unstructured decision to
`PostalAddressClassifier.isCompliant`. The classifier never changes; only the navigation differs.

- **pacs.008.001.08** (`MxPacs00800108`). The rule walks, in document order: the two group-header
  agents `GrpHdr/InstgAgt` and `GrpHdr/InstdAgt`; then, within every `CdtTrfTxInf` transaction, the
  five parties `Dbtr`, `Cdtr`, `UltmtDbtr`, `UltmtCdtr` and `InitgPty`; and the transaction agents
  `InstgAgt`, `InstdAgt`, `DbtrAgt`, `CdtrAgt`, `IntrmyAgt1..3` and `PrvsInstgAgt1..3`. **Party**
  addresses are read via `PartyIdentification135.getPstlAdr()`; **agent** addresses via
  `BranchAndFinancialInstitutionIdentification6.getFinInstnId()` →
  `FinancialInstitutionIdentification18.getPstlAdr()`. A party with no `PstlAdr`, and a BIC-only agent
  whose `FinInstnId` carries no `PstlAdr`, are both skipped.
- **pacs.009.001.08** (`MxPacs00900108`). pacs.009 is a *financial-institution* credit transfer: its
  debtor, creditor and ultimate parties are themselves
  `BranchAndFinancialInstitutionIdentification6`, exactly like the agents, so the rule applies
  uniformly to FI parties **and** agents. The rule uses a private `evaluateFi(...)` helper that
  null-guards the FI, its `FinInstnId` and its `PstlAdr`, applies the **BIC-only exemption**
  (`if (addr == null) return;`), and only flags a **present, non-compliant** address — at the path
  `.../FinInstnId/PstlAdr`. It walks the group-header agents and, per transaction, the FI parties
  `UltmtDbtr`, `Dbtr`, `Cdtr`, `UltmtCdtr` plus the transaction agents (`InstgAgt`, `InstdAgt`,
  `DbtrAgt`, `CdtrAgt`, `IntrmyAgt1..3`, `PrvsInstgAgt1..3`).
- **pain.001.001.09** (`MxPain00100109`). The rule walks the debtor-side parties `Dbtr` and
  `UltmtDbtr` at the **PaymentInformation** (`PaymentInstruction30`) level, and the creditor-side
  parties `Cdtr` and `UltmtCdtr` at the **transaction** (`CreditTransferTransaction34`) level. As a
  rule-fidelity discipline, it deliberately does **not** inspect `InitgPty`, the transaction-level
  `UltmtDbtr`, or any *agent* address for the hero rule — extending the rule to those would
  *strengthen* it and is forbidden.

Each failing occurrence yields **exactly one** `Finding` carrying
`ruleId = structured-address-min-town-country`, `severity = ERROR`, and an `elementPath` that points
at the offending `PstlAdr` (for example `FIToFICstmrCdtTrf/CdtTrfTxInf[0]/Cdtr/PstlAdr` for pacs.008,
or `CstmrCdtTrfInitn/PmtInf[0]/CdtTrfTxInf[0]/Cdtr/PstlAdr` for pain.001). Every rule is null-safe and
**collects all findings** rather than stopping at the first, so a message with several non-compliant
addresses produces several findings.

## Fixture Corroboration

The semantics above are exercised end-to-end by the module's test fixtures. The following fixtures
correspond to the classification table one-for-one:

- **`pacs008_structured_address.xml`** — the Debtor has `TwnNm`=Buenos Aires and `Ctry`=AR, and the
  group-header `InstdAgt` has `TwnNm`=Los Angeles and `Ctry`=US, with **no `AdrLine`** anywhere →
  **Structured → PASS** (`valid=true`, zero structured-address findings).
- **`pacs008_hybrid_address.xml`** — the same base plus **one `<AdrLine>`** added on the Debtor
  (`TwnNm`+`Ctry`+`AdrLine`) → **Hybrid → PASS** (`valid=true`).
- **`pacs008_unstructured_address.xml`** — the Creditor gains an **`AdrLine`-only** `PstlAdr` (address
  lines present, **no `TwnNm`/`Ctry`**) → **fully-unstructured → FAIL**: exactly one
  `structured-address-min-town-country` **ERROR** on the Creditor, and `valid=false`. This fixture
  doubles as the **hero-rule negative demo**.
- **`pacs009_unstructured_address_negative.xml`** and **`pain001_unstructured_address_negative.xml`** —
  an FI (pacs.009) / party (pain.001) gains an `AdrLine`-only address, producing a hero-rule
  **ERROR** in each message type.

Beyond these message-level fixtures, the classifier is also exercised **directly** by
`PostalAddressClassifierTest` (JUnit 5 + AssertJ, **no mocking**), which drives real
`PostalAddress24` objects through `classify(...)` / `isCompliant(...)` to confirm the Structured /
Hybrid / Unstructured verdicts and the `null`-address handling.

## Regulatory Background (context only)

From **14 November 2026**, SWIFT requires town and country to be provided in designated
**structured fields**, at a minimum, for all agents and parties in CBPR+ payment messages;
fully-unstructured postal addresses will **no longer be accepted** on the network. Only fully
**structured** or **hybrid** addresses are accepted thereafter. The change ships as part of SWIFT
**Standards Release 2026 (SR2026)**.

This regulatory summary is **background / corroborating context only.** The CBPR+ RULES inventory in
the specification remains the **single authoritative source** for the classification semantics and
was **not** altered, strengthened, weakened, or added to by any external statement; where this report
and any external source could appear to differ, the specification's rule inventory governs.

## Companion Deliverables

This report (A3) is one of five markdown deliverables for the `iso20022-cbpr` module:

- **`validation_design_report.md`** — the A1 (model navigation) and A2 (rule organization)
  investigations, plus the full rule-to-method **coverage table** proving every rule application maps
  to exactly one method.
- **`migration_report.md`** — the A4 (MT→MX address mapping) investigation and per-pair mapping
  tables for the MT103→pacs.008, MT202→pacs.009 and MT101→pain.001 flows.
- **`pre_implementation_analysis.md`** — the up-front analysis of the module, its scope and its
  integration surface.
- **`final_summary.md`** — the capability-versus-commercial-gap analysis and the next candidate
  message types.

