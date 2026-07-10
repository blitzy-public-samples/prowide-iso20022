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
package com.prowidesoftware.swift.cbpr.rules.pacs008;

import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.PostalAddressClassifier;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) <strong>hero rule</strong>
 * {@code structured-address-min-town-country} (severity {@link Severity#ERROR}) for
 * <strong>pacs.008.001.08</strong> (FI-to-FI Customer Credit Transfer).
 *
 * <p>The rule enforces the SWIFT structured-address mandate that takes effect on 14 November 2026:
 * every party and agent postal address carried by a pacs.008 message must be either
 * <em>structured</em> or <em>hybrid</em>; a fully-unstructured postal address is no longer
 * permitted on the network. The three authoritative categories (never invented, weakened or
 * strengthened here) are:
 *
 * <ul>
 *   <li><b>Structured</b> &mdash; {@code TwnNm} and {@code Ctry} present with no {@code AdrLine}
 *       &rarr; <b>passes</b>.
 *   <li><b>Hybrid</b> &mdash; {@code TwnNm} and {@code Ctry} present together with one or more
 *       {@code AdrLine} &rarr; <b>passes</b>.
 *   <li><b>Unstructured</b> &mdash; {@code AdrLine} present while {@code TwnNm} or {@code Ctry} is
 *       missing &rarr; <b>fails</b> (an {@link Severity#ERROR}-severity {@link Finding} is raised).
 * </ul>
 *
 * <p>The Structured / Hybrid / Unstructured decision is <strong>not</strong> re-implemented here: it
 * is delegated in full to the shared {@link PostalAddressClassifier} (Algorithm A3) so that the
 * classification is made identically across pacs.008, pacs.009 and pain.001 and across every party
 * and agent occurrence. This class is merely the pacs.008 navigator that locates every postal
 * address subject to the rule and asks the classifier whether each one is compliant.
 *
 * <h2>Addresses inspected</h2>
 *
 * <p>For pacs.008.001.08 the rule walks, in document order:
 *
 * <ul>
 *   <li>the two group-header agents &mdash; {@code GrpHdr/InstgAgt} and {@code GrpHdr/InstdAgt};
 *   <li>within every {@code CdtTrfTxInf} transaction, the five parties &mdash; {@code Dbtr},
 *       {@code Cdtr}, {@code UltmtDbtr}, {@code UltmtCdtr} and {@code InitgPty};
 *   <li>within every {@code CdtTrfTxInf} transaction, the ten agents &mdash; {@code InstgAgt},
 *       {@code InstdAgt}, {@code DbtrAgt}, {@code CdtrAgt}, {@code IntrmyAgt1..3} and
 *       {@code PrvsInstgAgt1..3}.
 * </ul>
 *
 * <p><b>BIC-only agents are exempt.</b> An agent identified by BIC only carries no
 * {@code FinInstnId/PstlAdr}; consistent with SWIFT guidance ("for agents, use of the BIC only
 * continues to be a valid option"), such an agent is simply skipped &mdash; the classifier is never
 * invoked for a {@code null} address. Likewise a party with no {@code PstlAdr} is not subject to
 * this rule and produces no finding.
 *
 * <h2>Behavioural contract</h2>
 *
 * <p>This rule <strong>never throws</strong>. Every navigation step is null-guarded, and the whole
 * generated {@code Mx*}/{@code dic} object graph is consumed <strong>read-only</strong> through
 * typed getters (Algorithm A1); no setter is called and {@code MxNode} is not used. The rule
 * <strong>collects all findings</strong> rather than stopping at the first violation, so a message
 * with several non-compliant parties or agents yields several findings, each carrying an indexed
 * {@code elementPath} (for example {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/Cdtr/PstlAdr}) that
 * unambiguously identifies the offending element.
 *
 * <p>Instances are stateless and therefore safe to reuse and to share across threads; the validator
 * creates them via the implicit public no-argument constructor.
 *
 * @see PostalAddressClassifier
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @since 10.3.14
 */
public class StructuredAddressRule implements CbprRule<MxPacs00800108> {

    /** The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory. */
    private static final String RULE_ID = "structured-address-min-town-country";

    /**
     * Human-readable explanation shared by every finding this rule raises. It describes the single
     * failure mode of the hero rule: a fully-unstructured postal address (address lines present
     * without both a structured town name and country).
     */
    private static final String UNSTRUCTURED_MESSAGE =
            "Postal address is fully unstructured (AdrLine present without both TwnNm and Ctry); "
                    + "CBPR+ SR2026 requires a structured (TwnNm+Ctry) or hybrid address.";

    /**
     * Evaluates the structured-address hero rule against a pacs.008.001.08 message and returns one
     * {@link Finding} per non-compliant postal address.
     *
     * <p>The method is fully null-safe: a {@code null} message, an absent
     * {@code FIToFICstmrCdtTrf}, an absent group header, an empty transaction list, {@code null}
     * transactions, absent parties/agents and absent postal addresses are all tolerated and simply
     * contribute no findings. An empty returned list therefore means "compliant (or nothing to
     * check)"; the list is never {@code null}.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the (possibly empty, never {@code null}) list of structured-address findings
     */
    @Override
    public List<Finding> check(MxPacs00800108 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }

        FIToFICustomerCreditTransferV08 cdtTrf = message.getFIToFICstmrCdtTrf();
        if (cdtTrf == null) {
            return findings;
        }

        // Group-header agents (a BIC-only agent carries no PstlAdr and is skipped inside the helper).
        GroupHeader93 grpHdr = cdtTrf.getGrpHdr();
        if (grpHdr != null) {
            checkAgentAddress(grpHdr.getInstgAgt(), "FIToFICstmrCdtTrf/GrpHdr/InstgAgt", findings);
            checkAgentAddress(grpHdr.getInstdAgt(), "FIToFICstmrCdtTrf/GrpHdr/InstdAgt", findings);
        }

        // Every transaction: its five parties and ten agents. Index the path so multi-transaction
        // findings are unambiguous for the per-rule and migration tests.
        List<CreditTransferTransaction39> txs = cdtTrf.getCdtTrfTxInf();
        if (txs != null) {
            for (int i = 0; i < txs.size(); i++) {
                CreditTransferTransaction39 tx = txs.get(i);
                if (tx == null) {
                    continue;
                }
                String base = "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]";

                // Parties (PartyIdentification135): name-and-address bearing occurrences.
                checkPartyAddress(tx.getDbtr(), base + "/Dbtr", findings);
                checkPartyAddress(tx.getCdtr(), base + "/Cdtr", findings);
                checkPartyAddress(tx.getUltmtDbtr(), base + "/UltmtDbtr", findings);
                checkPartyAddress(tx.getUltmtCdtr(), base + "/UltmtCdtr", findings);
                checkPartyAddress(tx.getInitgPty(), base + "/InitgPty", findings);

                // Agents (BranchAndFinancialInstitutionIdentification6): BIC-only agents are exempt.
                checkAgentAddress(tx.getInstgAgt(), base + "/InstgAgt", findings);
                checkAgentAddress(tx.getInstdAgt(), base + "/InstdAgt", findings);
                checkAgentAddress(tx.getDbtrAgt(), base + "/DbtrAgt", findings);
                checkAgentAddress(tx.getCdtrAgt(), base + "/CdtrAgt", findings);
                checkAgentAddress(tx.getIntrmyAgt1(), base + "/IntrmyAgt1", findings);
                checkAgentAddress(tx.getIntrmyAgt2(), base + "/IntrmyAgt2", findings);
                checkAgentAddress(tx.getIntrmyAgt3(), base + "/IntrmyAgt3", findings);
                checkAgentAddress(tx.getPrvsInstgAgt1(), base + "/PrvsInstgAgt1", findings);
                checkAgentAddress(tx.getPrvsInstgAgt2(), base + "/PrvsInstgAgt2", findings);
                checkAgentAddress(tx.getPrvsInstgAgt3(), base + "/PrvsInstgAgt3", findings);
            }
        }

        return findings;
    }

    /**
     * Inspects a single party's postal address and, when it is present but not compliant, appends a
     * structured-address finding. A {@code null} party or a party with no {@code PstlAdr} is not
     * subject to this rule and contributes no finding.
     *
     * @param party the party to inspect; may be {@code null}
     * @param path the element path prefix identifying this party (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/Cdtr})
     * @param findings the accumulator to which a finding is appended when the address is
     *     unstructured
     */
    private void checkPartyAddress(PartyIdentification135 party, String path, List<Finding> findings) {
        if (party == null) {
            return;
        }
        PostalAddress24 addr = party.getPstlAdr();
        if (addr == null) {
            // No postal address present: the party is not subject to the structured-address rule.
            return;
        }
        if (!PostalAddressClassifier.isCompliant(addr)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, path + "/PstlAdr", UNSTRUCTURED_MESSAGE));
        }
    }

    /**
     * Inspects a single agent's postal address and, when it is present but not compliant, appends a
     * structured-address finding. A {@code null} agent, an agent with no {@code FinInstnId}, or an
     * agent whose {@code FinInstnId} carries no {@code PstlAdr} (the BIC-only case) is exempt and
     * contributes no finding.
     *
     * @param agent the agent to inspect; may be {@code null}
     * @param path the element path prefix identifying this agent (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/DbtrAgt})
     * @param findings the accumulator to which a finding is appended when the address is
     *     unstructured
     */
    private void checkAgentAddress(
            BranchAndFinancialInstitutionIdentification6 agent, String path, List<Finding> findings) {
        if (agent == null) {
            return;
        }
        FinancialInstitutionIdentification18 fi = agent.getFinInstnId();
        if (fi == null) {
            return;
        }
        PostalAddress24 addr = fi.getPstlAdr();
        if (addr == null) {
            // BIC-only agent: no postal address is carried, so the agent is exempt from this rule.
            return;
        }
        if (!PostalAddressClassifier.isCompliant(addr)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, path + "/FinInstnId/PstlAdr", UNSTRUCTURED_MESSAGE));
        }
    }
}
