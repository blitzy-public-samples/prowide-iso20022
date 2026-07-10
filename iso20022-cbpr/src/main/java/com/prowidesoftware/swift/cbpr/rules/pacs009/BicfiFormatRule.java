/*
 * Copyright 2006-2025 Prowide
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.prowidesoftware.swift.cbpr.rules.pacs009;

import com.prowidesoftware.swift.cbpr.BicValidator;
import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code bicfi-format}</strong> for
 * <strong>pacs.009.001.08</strong> (Financial Institution Credit Transfer, {@link MxPacs00900108}).
 *
 * <p>The authoritative requirement from the CBPR+ rule inventory is: <em>"All FinInstnId/BICFI match
 * ISO 9362"</em> &mdash; every Business Identifier Code carried by a financial-institution
 * identification anywhere in the message must be a well-formed ISO 9362 BIC (8 or 11 characters). The
 * severity of a violation is {@link Severity#ERROR}.
 *
 * <h2>What this rule checks &mdash; and what it deliberately does not</h2>
 *
 * <p>This rule validates <strong>only the format of a BICFI value that is actually present</strong>.
 * It does <strong>not</strong> require a BICFI to exist: in pacs.009 a financial institution may
 * legitimately be identified by name together with a structured/hybrid postal address, or by a
 * clearing-system member identification and/or LEI, in which case no {@code BICFI} is populated. The
 * presence or mandatory-ness of a BICFI is governed by other rules (for example the FI-party
 * identification preference and the point-to-point agent rules); consequently, when a
 * {@code BICFI} is {@code null} or blank this rule stays <em>silent</em> and emits no finding. Only a
 * BICFI that is present <em>and</em> malformed produces a finding.
 *
 * <h2>Scope of inspection</h2>
 *
 * <p>Because pacs.009 is a financial-institution-to-financial-institution transfer, both the payment
 * "parties" (debtor/creditor and their ultimate counterparts) and the settlement-chain "agents" are
 * modelled as {@link BranchAndFinancialInstitutionIdentification6}. The rule therefore inspects every
 * such occurrence:
 *
 * <ul>
 *   <li>the group-header instructing and instructed agents
 *       ({@code FICdtTrf/GrpHdr/InstgAgt}, {@code FICdtTrf/GrpHdr/InstdAgt}); and
 *   <li>for each transaction in {@code FICdtTrf/CdtTrfTxInf}, the FI parties
 *       ({@code UltmtDbtr}, {@code Dbtr}, {@code Cdtr}, {@code UltmtCdtr}) and the agents
 *       ({@code InstgAgt}, {@code InstdAgt}, {@code DbtrAgt}, {@code CdtrAgt},
 *       {@code IntrmyAgt1}, {@code IntrmyAgt2}, {@code IntrmyAgt3},
 *       {@code PrvsInstgAgt1}, {@code PrvsInstgAgt2}, {@code PrvsInstgAgt3}).
 * </ul>
 *
 * <p>Each occurrence is reached through typed getter chains (no {@code MxNode} navigation and no
 * casts), keeping the traversal compile-safe and refactor-safe, and the generated model is consumed
 * strictly read-only.
 *
 * <h2>Delegation and behavioral contract</h2>
 *
 * <p>The ISO 9362 pattern itself is <strong>not</strong> reimplemented here: the check is delegated
 * entirely to the shared {@link BicValidator#isValid(String)} predicate, which is used identically by
 * the pacs.008, pacs.009 and pain.001 {@code bicfi-format} rules so that the single authoritative
 * pattern {@code [A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?} lives in exactly one place.
 *
 * <p>In line with the {@link CbprRule} contract this rule <strong>never throws</strong>: a
 * {@code null} message, a missing {@code FICdtTrf} body, absent group header, a {@code null}
 * transaction, a {@code null} party/agent, a {@code null} {@code FinInstnId} or an absent
 * {@code BICFI} are all treated as ordinary, expected inputs. It <strong>collects every</strong>
 * violation rather than stopping at the first, returning one {@link Severity#ERROR} finding per
 * malformed BICFI, each carrying a distinct {@code elementPath}. A returned empty list means the
 * message complies with this rule; the returned list is never {@code null}.
 *
 * @see BicValidator
 * @see CbprRule
 * @see MxPacs00900108
 * @since 10.3.14
 */
public class BicfiFormatRule implements CbprRule<MxPacs00900108> {

    /**
     * The CBPR+ rule identifier, preserved verbatim from the authoritative rule inventory. Emitted on
     * every {@link Finding} this rule produces.
     */
    private static final String RULE_ID = "bicfi-format";

    /**
     * Creates a new, stateless {@code bicfi-format} rule for pacs.009.001.08.
     *
     * <p>A public no-argument constructor is provided so the validator can instantiate the rule
     * reflection-free with {@code new BicfiFormatRule()}. The rule holds no state and is safe to reuse
     * across messages.
     */
    public BicfiFormatRule() {
        // Stateless rule; nothing to initialize.
    }

    /**
     * Validates that every BICFI present on any financial-institution identification in the supplied
     * pacs.009.001.08 message is well-formed per ISO 9362.
     *
     * <p>The method walks the group-header agents and every transaction's FI parties and agents,
     * delegating the per-value format decision to {@link #evaluateFi}. It never throws and always
     * returns a non-{@code null} list: an empty list signals compliance, while each element describes a
     * distinct malformed BICFI (a present value that fails {@link BicValidator#isValid(String)}).
     *
     * @param message the parsed pacs.009.001.08 message to validate; may be {@code null} or partial,
     *     in which case only the branches that are present are inspected
     * @return the list of {@link Severity#ERROR} findings, one per malformed BICFI, or an empty list
     *     when the message complies with this rule; never {@code null}
     */
    @Override
    public List<Finding> check(MxPacs00900108 message) {
        List<Finding> findings = new ArrayList<>();

        // A null message is a legitimate, expected input: report nothing and never throw.
        if (message == null) {
            return findings;
        }

        FinancialInstitutionCreditTransferV08 body = message.getFICdtTrf();
        if (body == null) {
            // No FI credit-transfer body means there are no agents or FI parties to inspect.
            return findings;
        }

        // (1) Group-header agents: the instructing and instructed agents that frame the message.
        GroupHeader93 groupHeader = body.getGrpHdr();
        if (groupHeader != null) {
            evaluateFi(groupHeader.getInstgAgt(), "FICdtTrf/GrpHdr/InstgAgt", findings);
            evaluateFi(groupHeader.getInstdAgt(), "FICdtTrf/GrpHdr/InstdAgt", findings);
        }

        // (2) Per-transaction FI parties and agents. In pacs.009 the debtor/creditor are themselves
        //     financial institutions, so both "parties" and "agents" are modelled as
        //     BranchAndFinancialInstitutionIdentification6 and are inspected uniformly.
        List<CreditTransferTransaction36> transactions = body.getCdtTrfTxInf();
        if (transactions != null) {
            for (int i = 0; i < transactions.size(); i++) {
                CreditTransferTransaction36 tx = transactions.get(i);
                if (tx == null) {
                    continue;
                }
                // A 0-based transaction index keeps each finding's elementPath distinct when several
                // transactions each carry a malformed BICFI on the same party/agent role.
                String base = "FICdtTrf/CdtTrfTxInf[" + i + "]";

                // FI parties (may be identified by BICFI, or by name + address / clearing code / LEI).
                evaluateFi(tx.getUltmtDbtr(), base + "/UltmtDbtr", findings);
                evaluateFi(tx.getDbtr(), base + "/Dbtr", findings);
                evaluateFi(tx.getCdtr(), base + "/Cdtr", findings);
                evaluateFi(tx.getUltmtCdtr(), base + "/UltmtCdtr", findings);

                // Agents along the settlement chain.
                evaluateFi(tx.getInstgAgt(), base + "/InstgAgt", findings);
                evaluateFi(tx.getInstdAgt(), base + "/InstdAgt", findings);
                evaluateFi(tx.getDbtrAgt(), base + "/DbtrAgt", findings);
                evaluateFi(tx.getCdtrAgt(), base + "/CdtrAgt", findings);
                evaluateFi(tx.getIntrmyAgt1(), base + "/IntrmyAgt1", findings);
                evaluateFi(tx.getIntrmyAgt2(), base + "/IntrmyAgt2", findings);
                evaluateFi(tx.getIntrmyAgt3(), base + "/IntrmyAgt3", findings);
                // Previous instructing agents also carry a FinInstnId/BICFI that, when present, must be
                // a well-formed ISO 9362 code; include them so a malformed BICFI here is not missed.
                evaluateFi(tx.getPrvsInstgAgt1(), base + "/PrvsInstgAgt1", findings);
                evaluateFi(tx.getPrvsInstgAgt2(), base + "/PrvsInstgAgt2", findings);
                evaluateFi(tx.getPrvsInstgAgt3(), base + "/PrvsInstgAgt3", findings);
            }
        }

        return findings;
    }

    /**
     * Evaluates a single financial-institution occurrence, adding a finding when it carries a present
     * but malformed BICFI.
     *
     * <p>Every navigation hop is null-guarded so the method is safe for the sparsest partial messages:
     * a {@code null} party, a {@code null} {@code FinInstnId} or an absent (null/blank) {@code BICFI}
     * yields no finding and no exception. When a BICFI value <em>is</em> present, its format is decided
     * solely by {@link BicValidator#isValid(String)}; a value that fails that predicate produces one
     * {@link Severity#ERROR} finding at {@code path + "/FinInstnId/BICFI"}.
     *
     * @param fiParty the financial-institution identification to inspect; may be {@code null}
     * @param path the human-readable element path of {@code fiParty} (for example
     *     {@code FICdtTrf/CdtTrfTxInf[0]/Dbtr}); the {@code /FinInstnId/BICFI} suffix is appended for
     *     the finding
     * @param findings the accumulator to which a finding is added when a malformed BICFI is detected
     */
    private void evaluateFi(
            BranchAndFinancialInstitutionIdentification6 fiParty, String path, List<Finding> findings) {
        if (fiParty == null) {
            return;
        }
        FinancialInstitutionIdentification18 id = fiParty.getFinInstnId();
        if (id == null) {
            return;
        }
        String bicfi = id.getBICFI();

        // Only the FORMAT of a PRESENT BICFI is this rule's concern. An absent BICFI is legitimate
        // (the FI may be identified by name + structured address, clearing code, or LEI) and its
        // presence is governed by other rules, so stay silent when it is null or blank.
        if (StringUtils.isNotBlank(bicfi) && !BicValidator.isValid(bicfi)) {
            findings.add(new Finding(
                    RULE_ID,
                    Severity.ERROR,
                    path + "/FinInstnId/BICFI",
                    "BICFI '" + bicfi
                            + "' does not match ISO 9362 (must be 8 or 11 characters: 4 alphanumeric bank + 2 alpha country + 2 alphanumeric location, optional 3 alphanumeric branch)"));
        }
    }
}
