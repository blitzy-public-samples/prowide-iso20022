# CBPR+ Module — Pre-Implementation Analysis

This document is the up-front (pre-implementation) analysis for the new `iso20022-cbpr` Gradle module of the Prowide ISO 20022 open-source Java library. The module adds **CBPR+ Standards Release 2026 (SR2026) validation** together with **address-scoped MT→MX migration** as owned, auditable, tested code — centered on the SWIFT **14 November 2026 structured-address mandate**. It is **purely additive**: a new, self-contained Gradle subproject that consumes the existing modules **read-only** and changes no shared source. The facts and integration plan captured here were established *before* implementation, and the implemented module honors them faithfully; this is a documentation deliverable only (no code, no runnable snippets).

> **Background context only.** The external SWIFT / SR2026 / 14 November 2026 references throughout this document establish the regulatory motivation for the feature. They are background context and never the source of a validation rule. The authoritative CBPR+ rule inventory (the 12 rule identifiers manifesting as 21 message-scoped applications) is the single source of truth for the validator; no external statement is used to invent, add, weaken, or strengthen any rule.

---

## Repository Facts

The following facts were verified against the repository as it stood **before** this effort. They govern how the new module is wired and what it may consume.

### Module topology

- The repository is a **72-subproject Gradle build**:
  - `iso20022-core` — the **sole hand-written module** (the only module with hand-authored `src/main/java` and tests);
  - `model-common-types` — the shared JAXB-generated dictionary of common ISO 20022 types;
  - **35 message categories**, each present as a `-mx` / `-types` pair (for example `model-pacs-mx` / `model-pacs-types`, `model-pain-mx` / `model-pain-types`).
- `settings.gradle` (**110 lines**) registers all of these subprojects. Before this effort it did **not** register any CBPR+ module — confirming this is genuinely new work.

### One central build script

- There is **exactly one build script**, the **root `build.gradle` (560 lines)**. No module — not even `iso20022-core` — carries its own `build.gradle`. Every module is configured centrally through `subprojects { }` and `configure(subprojects.findAll { ... })` blocks, plus per-project `project(':name') { }` blocks for the few modules that need bespoke dependencies.

### What the new module inherits vs. must declare

The central-configuration blocks determine what `iso20022-cbpr` gets automatically and what it must declare explicitly:

| Central block | Effect on a subproject | Applies to `iso20022-cbpr`? |
|---|---|---|
| `subprojects { }` | Applies `java-library`; sets `version = rootProject.version`; adds `commons-lang3`, `gson`, and `jaxb-impl` at `implementation` scope; sets `sourceSets.main.java.srcDirs = ['src/main/java', 'src/generated/java']` | **Yes — all inherited** |
| `configure(subprojects.findAll { it.name != 'model-common-types' })` | Adds `api project(':model-common-types')` | **Yes — auto-added**, so the module auto-gains the shared dictionary types (`PostalAddress24`, `PartyIdentification135`, `FinancialInstitutionIdentification18`, `BusinessApplicationHeaderV02Impl`) |
| `configure(subprojects.findAll { it.name.endsWith('-mx') })` | Adds `api project(':iso20022-core')`, `implementation` on the module's specific `-types` project, and JAXB | **No — skipped**, because the name does not end in `-mx` |

Because the `-mx` configuration block is skipped, the module **must explicitly declare** its API and model dependencies: `api project(':iso20022-core')` plus `implementation` on `model-pacs-mx`, `model-pacs-types`, `model-pain-mx`, and `model-pain-types`. The `-mx` modules depend on their `-types` companions at `implementation` (non-transitive) scope, which is why `model-pacs-types` and `model-pain-types` must be declared directly to place the transaction/group types on the compile classpath.

### Pinned toolchain and versions

| Item | Pinned value |
|---|---|
| Java language version | **Java 11** (`JavaLanguageVersion.of(11)`; source and target 11) |
| Gradle wrapper | **8.14.5** |
| `jaxb-impl` | `4.0.6` |
| `gson` | `2.14.0` |
| `commons-lang3` | `3.20.0` |
| `prowideCoreVersion` (`pw-swift-core`) | `SRU2025-10.3.14` |

No Java 17+ language feature is used anywhere in the module; source and target remain Java 11.

### The only two edits to pre-existing files

The deliverable is additive. The **only two edits to pre-existing files** are build wiring:

1. `settings.gradle` gains `include 'iso20022-cbpr'`.
2. The root `build.gradle` gains a `project(':iso20022-cbpr') { dependencies { … } }` block.

**No repository-wide dependency is added, removed, or version-bumped.** The module merely *declares* dependencies that already exist in the build at their pinned versions; it introduces no new coordinate and no additional JAXB implementation.

The wiring block has the following shape (illustrative, for clarity only):

```groovy
project(':iso20022-cbpr') {
    dependencies {
        api project(':iso20022-core')
        implementation project(':model-pacs-mx'); implementation project(':model-pacs-types')
        implementation project(':model-pain-mx'); implementation project(':model-pain-types')
        // model-common-types auto-added by the shared configure(...) block
        testImplementation "org.junit.jupiter:junit-jupiter:5.11.4"
    }
}
```

---

## Read-Only Integration Surface

The module consumes existing code **exclusively read-only**. No existing source file is edited beyond the two build-wiring edits described above; the generated `model-*` classes and the `iso20022-core` public API are consumed as-is, never modified. The consumption edges are:

- **Validator input contract** — `AbstractMX`, the common base of every generated `Mx*` message. The entry point `CbprValidator.validate(AbstractMX)` dispatches on the concrete subtype via `instanceof` (and the corresponding cast), so each rule receives a strongly-typed message.
- **Message roots** — `MxPacs00800108` and `MxPacs00900108` (from `model-pacs-mx`); `MxPain00100109` (from `model-pain-mx`).
- **Transaction / group types**:
  - pacs.008 — `FIToFICustomerCreditTransferV08`, `GroupHeader93`, `CreditTransferTransaction39`;
  - pacs.009 — `FinancialInstitutionCreditTransferV08`, `CreditTransferTransaction36`;
  - pain.001 — `CustomerCreditTransferInitiationV09`, `PaymentInstruction30`, `CreditTransferTransaction34`.
- **Shared dictionary types** (from `model-common-types`):
  - `PartyIdentification135` (`getNm` / `getPstlAdr` / `getId`);
  - `PostalAddress24` (`getTwnNm` / `getCtry` / `getAdrLine`);
  - `BranchAndFinancialInstitutionIdentification6` → `FinancialInstitutionIdentification18` (`getBICFI`);
  - `Party38Choice` → `OrganisationIdentification29` (`getAnyBIC` / `getLEI` / `getOthr`);
  - `BusinessApplicationHeaderV02Impl`.
- **Header handling** — reuse `AppHdrParser.parse(...)`, which defaults to **BAH_V2** (the CBPR+ default header) and returns `Optional<AppHdr>`. The concrete `BusinessAppHdrV02` type is used for the pain.001 CopyDuplicate rule. **No new header-parsing code is written** — the existing header handling is reused as-is.
- **Generic navigation (documented alternative)** — `MxNode`, the A1 alternative to typed getter chains. The primary approach is typed getters; `MxNode` remains the recorded fallback.
- **MT source parsing (migration)** — `AbstractMT.parse(...)` and the MT103 / MT202 / MT101 field accessors, reached **transitively** through Prowide Core (`pw-swift-core`), which is on the classpath because `iso20022-core` declares it at `api` scope. The hard constraint here is absolute: **Prowide Core source is NOT ingested**; only the prompt's MT API REFERENCE signatures are used; **no new MT parser is written and no MT API is invented** — any missing MT capability is recorded as a documented gap rather than filled with a guessed API.

---

## Target Message Types

Exactly **three** message types are in scope, all of which already exist as generated types in the repository:

| Message type | Description | Message root class |
|---|---|---|
| **pacs.008.001.08** | FI-to-FI Customer Credit Transfer | `MxPacs00800108` |
| **pacs.009.001.08** | Financial Institution Credit Transfer | `MxPacs00900108` |
| **pain.001.001.09** | Customer Credit Transfer Initiation | `MxPain00100109` |

The typed-getter navigation root for each message type (the entry getter the traversal chains start from) is:

- **pacs.008.001.08** — `MxPacs00800108.getFIToFICstmrCdtTrf()` → `FIToFICustomerCreditTransferV08` → `GroupHeader93` + `List<CreditTransferTransaction39>`.
- **pacs.009.001.08** — `MxPacs00900108.getFICdtTrf()` → `FinancialInstitutionCreditTransferV08` → `GroupHeader93` + `List<CreditTransferTransaction36>`.
  - **Critical distinction:** in `CreditTransferTransaction36` the parties (`getDbtr` / `getCdtr` / `getUltmtDbtr` / `getUltmtCdtr`) are **financial institutions** (`BranchAndFinancialInstitutionIdentification6`), **not** `PartyIdentification135`. A financial institution identified by BICFI only carries no postal address and is therefore **exempt** from the structured-address hero rule.
- **pain.001.001.09** — `MxPain00100109.getCstmrCdtTrfInitn()` → `CustomerCreditTransferInitiationV09` → `List<PaymentInstruction30>` → `List<CreditTransferTransaction34>`.

Message-level validation was historically **excluded from the open-source library** and offered only through the commercial Prowide Integrator product; this module generates that previously paywalled capability as owned, tested code (the full capability-gap analysis lives in `final_summary.md`).

---

## Test Fixtures — Adaptation Templates

The only pre-existing fixtures relevant to the target message types are:

- `iso20022-core/src/test/resources/pacs.008.001.07.xml`
- `iso20022-core/src/test/resources/pacs.009.001.07.xml`

Both are at version **`.07`**, and both contain debtor/creditor and agent structures with `PstlAdr` sub-trees that can be adapted into the new module's fixtures. They are used purely as **adaptation templates** — the originals are **REFERENCE only and remain untouched**.

### Namespace adaptation (`.07` → `.08` / `.09`)

The new module's MX fixtures are adapted from these templates by rewriting the version namespace `.07` → `.08` (pacs.008) or `.07` → `.09` (pacs.009) in **both** places it appears:

- the header `MsgDefIdr` element, and
- the `Document` element's `xmlns`.

### Concrete adaptation fact

In `pacs.008.001.07.xml` the group-header `InstdAgt` postal address carries `TwnNm` (`Los Angeles`) and `CtrySubDvsn` but **no `Ctry`** — which the postal-address classifier would treat as **unstructured**. The adapted `.08` fixture therefore **adds `<Ctry>US</Ctry>`** so the address becomes **structured**. In the same template the `Dbtr` postal address already has `TwnNm` = `Buenos Aires` plus `Ctry` = `AR`, so it is already structured and needs no change.

### No pre-existing pain.001 fixture

There is **no pain.001 fixture** anywhere in the repository. Consequently, `pain001_structured_address.xml` is built **from scratch** rather than adapted from an existing template.

---

## Build Plan — Outside-In

The module is built outside-in, so it compiles cleanly at every step. This mirrors the implemented approach:

1. **Wire the module** (Group 0): add `include 'iso20022-cbpr'` to `settings.gradle` and the `project(':iso20022-cbpr')` dependency block to the root `build.gradle`, so the module resolves against `iso20022-core` and the model modules and compiles empty.
2. **Create the validator skeleton and shared helpers**: `CbprValidator`, `ValidationResult`, `Finding`, `Severity`, `CbprRule`, `PostalAddressClassifier`, `BicValidator`, and `FinXCharset`, so cross-cutting logic (address classification, BICFI format, FIN-X charset) exists once and the rule classes stay thin.
3. **Implement the 21 rule classes** against typed model paths — 9 for pacs.008, 7 for pacs.009, 5 for pain.001 — each returning findings via the shared helpers.
4. **Build the address-scoped migrators**: `Mt103ToPacs008`, `Mt202ToPacs009`, `Mt101ToPain001`, and `AddressMigrationSupport`. Each parses the source MT, reads only the debtor/creditor identity and postal-address fields, and leaves all other branches null — the migrated MX messages are **partial by design**.
5. **Prove behavior** with fixtures and tests using JUnit 5 + AssertJ + XMLUnit, with **no mocking**, including at least one migration from a fully-unstructured MT source that must fail the structured-address hero rule.
6. **Capture rationale** in the five markdown deliverables — this document plus `validation_design_report.md`, `structured_address_report.md`, `migration_report.md`, and `final_summary.md`.

### Core behavioral contract (stated up front)

The validator **never throws**. Non-compliant, absent, or malformed elements yield `Finding` objects rather than exceptions, and the validator **collects every finding** (it does not stop at the first violation) before returning a `ValidationResult`. That result's `valid` flag is `true` **if and only if there is no `ERROR`-severity finding** — a result carrying only `WARNING`-severity findings is still valid.

---

## Companion Deliverables

This document is one of five markdown deliverables for the module. The companions are:

- **`validation_design_report.md`** — the A1 (model navigation: typed getter chains vs. `MxNode`) and A2 (rule organization: one class per rule vs. grouped methods) investigations, plus the full rule-to-method coverage table proving all 21 rule applications map to exactly one method with zero omissions.
- **`structured_address_report.md`** — the A3 investigation (structured-address detection: typed sub-element inspection vs. `MxNode` path probing) and the Structured / Hybrid / Unstructured classification semantics.
- **`migration_report.md`** — the A4 investigation (MT→MX address mapping), the per-pair mapping tables, and the documented gaps.
- **`final_summary.md`** — the capability-versus-commercial-gap analysis and the next candidate message types.
