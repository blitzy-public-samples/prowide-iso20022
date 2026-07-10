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

import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.PostalAddressClassifier;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) hero rule {@code structured-address-min-town-country},
 * severity {@link Severity#ERROR}, for <strong>pacs.009.001.08</strong> (Financial Institution
 * Credit Transfer, {@link MxPacs00900108}).
 *
 * <p>The SWIFT structured-address mandate, effective 14 November 2026, requires that the postal
 * address of every applicable party and agent be provided either fully <em>structured</em> (town
 * name and country present, with no address lines) or as a <em>hybrid</em> (town name and country
 * present, plus one or more address lines). A fully-<em>unstructured</em> address &mdash; address
 * lines only, or an address missing the mandatory town or country &mdash; is no longer accepted on
 * the network and is flagged here as an {@link Severity#ERROR}.
 *
 * <h2>pacs.009 applicability &mdash; the BIC-only exemption</h2>
 *
 * <p>pacs.009 is a <em>financial-institution</em> credit transfer: in
 * {@link CreditTransferTransaction36} the debtor, creditor and ultimate parties
 * ({@code getUltmtDbtr}, {@code getDbtr}, {@code getCdtr}, {@code getUltmtCdtr}) are themselves
 * financial institutions of type {@link BranchAndFinancialInstitutionIdentification6}, exactly like
 * the agents. Consequently this rule applies uniformly to FI parties <em>and</em> agents.
 *
 * <p>Crucially, the rule only bites when an FI is identified <strong>by name and address</strong>.
 * An FI identified <strong>by BICFI only carries no postal address</strong> and is therefore
 * <strong>exempt</strong> from the structured-address mandate (per SWIFT, "for agents, use of the
 * BIC only continues to be a valid option"). This class realises that exemption by simply skipping
 * any occurrence whose {@code PstlAdr} branch is absent: a {@code null} postal address is a clean
 * <em>pass</em>, never a finding.
 *
 * <h2>Delegation and scope</h2>
 *
 * <p>The Structured / Hybrid / Unstructured decision is delegated in full to the shared
 * {@link PostalAddressClassifier}, so that all three CBPR+ message types apply identical hero-rule
 * semantics; this class never re-implements the town/country/address-line inspection. Every present
 * FI postal address across the group header and every transaction (all FI parties plus the
 * instructing, instructed, debtor, creditor, intermediary and previous-instructing agents) is
 * evaluated, and one finding is emitted per offending address, each carrying a distinct
 * {@code elementPath}.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>In keeping with {@link CbprRule}, {@link #check(MxPacs00900108) check} <strong>never
 * throws</strong>: a {@code null} message, an absent {@code FICdtTrf}, an empty transaction list and
 * any {@code null} party, agent, {@code FinInstnId} or {@code PstlAdr} are all tolerated and yield
 * either a finding or a clean pass. An empty returned list means the message satisfies this rule.
 *
 * @see PostalAddressClassifier
 * @see CbprRule
 * @see Finding
 * @see MxPacs00900108
 * @since 10.3.14
 */
public class StructuredAddressRule implements CbprRule<MxPacs00900108> {

    /**
     * The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory (AAP §0.1.1).
     */
    private static final String RULE_ID = "structured-address-min-town-country";

    /** Human-readable explanation attached to every finding this rule raises. */
    private static final String MESSAGE =
            "Postal address must be structured (TwnNm+Ctry, no AdrLine) or hybrid (TwnNm+Ctry + AdrLine); "
                    + "fully-unstructured (AdrLine only, or missing town/country) is not permitted under CBPR+ SR2026";

    /**
     * Evaluates the {@code structured-address-min-town-country} rule against a pacs.009.001.08
     * message.
     *
     * <p>Walks every financial-institution party and agent occurrence &mdash; the group-header
     * instructing/instructed agents and, per transaction, the four FI parties and the transaction-level
     * agents &mdash; and raises one {@link Severity#ERROR} {@link Finding} for each occurrence that
     * carries a postal address which is neither structured nor hybrid. Occurrences without a postal
     * address (BICFI-only FIs) are exempt and produce no finding.
     *
     * @param message the parsed pacs.009.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings, one per non-compliant postal address; empty when every present FI
     *     postal address is structured or hybrid (and for BIC-only exempt FIs); never {@code null}
     */
    @Override
    public List<Finding> check(MxPacs00900108 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }
        FinancialInstitutionCreditTransferV08 fi = message.getFICdtTrf();
        if (fi == null) {
            return findings;
        }

        // Group-header agents are evaluated once for the whole message.
        GroupHeader93 grpHdr = fi.getGrpHdr();
        if (grpHdr != null) {
            evaluateFi(grpHdr.getInstgAgt(), "FICdtTrf/GrpHdr/InstgAgt", findings);
            evaluateFi(grpHdr.getInstdAgt(), "FICdtTrf/GrpHdr/InstdAgt", findings);
        }

        // Per-transaction FI parties and agents. getCdtTrfTxInf() is lazily initialised and never null.
        List<CreditTransferTransaction36> txs = fi.getCdtTrfTxInf();
        for (int i = 0; i < txs.size(); i++) {
            CreditTransferTransaction36 tx = txs.get(i);
            if (tx == null) {
                continue;
            }
            String base = "FICdtTrf/CdtTrfTxInf[" + i + "]";

            // FI parties (all BranchAndFinancialInstitutionIdentification6 in pacs.009).
            evaluateFi(tx.getUltmtDbtr(), base + "/UltmtDbtr", findings);
            evaluateFi(tx.getDbtr(), base + "/Dbtr", findings);
            evaluateFi(tx.getCdtr(), base + "/Cdtr", findings);
            evaluateFi(tx.getUltmtCdtr(), base + "/UltmtCdtr", findings);

            // Agents.
            evaluateFi(tx.getInstgAgt(), base + "/InstgAgt", findings);
            evaluateFi(tx.getInstdAgt(), base + "/InstdAgt", findings);
            evaluateFi(tx.getDbtrAgt(), base + "/DbtrAgt", findings);
            evaluateFi(tx.getCdtrAgt(), base + "/CdtrAgt", findings);
            evaluateFi(tx.getIntrmyAgt1(), base + "/IntrmyAgt1", findings);
            evaluateFi(tx.getIntrmyAgt2(), base + "/IntrmyAgt2", findings);
            evaluateFi(tx.getIntrmyAgt3(), base + "/IntrmyAgt3", findings);
            evaluateFi(tx.getPrvsInstgAgt1(), base + "/PrvsInstgAgt1", findings);
            evaluateFi(tx.getPrvsInstgAgt2(), base + "/PrvsInstgAgt2", findings);
            evaluateFi(tx.getPrvsInstgAgt3(), base + "/PrvsInstgAgt3", findings);
        }

        return findings;
    }

    /**
     * Evaluates a single financial-institution occurrence and, when it carries a non-compliant postal
     * address, appends an {@link Severity#ERROR} finding to {@code findings}.
     *
     * <p>The method encodes the pacs.009 BIC-only exemption: if the FI, its {@code FinInstnId} or its
     * {@code PstlAdr} is absent, the occurrence is skipped (a clean pass). Only when a postal address
     * is actually present is the Structured/Hybrid/Unstructured decision made &mdash; and that decision
     * is delegated entirely to {@link PostalAddressClassifier#isCompliant(PostalAddress24)}. The method
     * never throws for any input.
     *
     * @param fiParty the financial-institution occurrence to evaluate; may be {@code null}
     * @param path the human-readable element path of {@code fiParty} (the {@code /FinInstnId/PstlAdr}
     *     suffix is appended for the finding)
     * @param findings the mutable accumulator to which a finding is added on violation
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
        PostalAddress24 addr = id.getPstlAdr();
        // BIC-only exemption: an FI with no postal address is identified by BICFI (or clearing/other)
        // only and is exempt from the structured-address mandate — skip it rather than flagging it.
        if (addr == null) {
            return;
        }
        if (!PostalAddressClassifier.isCompliant(addr)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, path + "/FinInstnId/PstlAdr", MESSAGE));
        }
    }
}
