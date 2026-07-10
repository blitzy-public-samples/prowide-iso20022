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
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <b>{@code instructed-agent-group-vs-tx}</b> for
 * <b>pacs.008.001.08</b> (FI-to-FI Customer Credit Transfer). Severity: {@link Severity#ERROR}.
 *
 * <p><b>Rule (verbatim):</b> "If GrpHdr/InstdAgt present, CdtTrfTxInf/InstdAgt must not be present."
 * This is the CBPR+ rule known as <b>"R6"</b>.
 *
 * <p><b>Interpretation &mdash; mutual exclusivity.</b> The Instructed Agent may be specified either
 * once at the group level ({@code GrpHdr/InstdAgt}) or per transaction ({@code CdtTrfTxInf/InstdAgt}),
 * but never both. When the group header carries an Instructed Agent, that agent applies to every
 * transaction in the message and no individual transaction may restate it. Accordingly:
 *
 * <ul>
 *   <li>If {@code GrpHdr/InstdAgt} is <em>absent</em>, this rule does not fire &mdash; transaction
 *       level {@code InstdAgt} is then permitted and this rule returns no findings.
 *   <li>If {@code GrpHdr/InstdAgt} is <em>present</em>, then any transaction that <em>also</em>
 *       carries its own {@code InstdAgt} violates the rule; one {@link Severity#ERROR} finding is
 *       emitted for each offending transaction so that collect-all reporting stays precise.
 * </ul>
 *
 * <p><b>Presence, not content.</b> The check is purely structural: presence is determined by object
 * identity ({@code getInstdAgt() != null}). The rule deliberately does <em>not</em> inspect the
 * BICFI or any other content of the agent &mdash; those concerns belong to the point-to-point
 * presence rule ({@code agent-point-to-point-bicfi-mandatory}) and the format rule
 * ({@code bicfi-format}), which are independent of this structural mutual-exclusivity rule.
 *
 * <p><b>Navigation (typed getters, read-only &mdash; Algorithm A1).</b>
 * {@link MxPacs00800108#getFIToFICstmrCdtTrf()} &rarr;
 * {@link FIToFICustomerCreditTransferV08#getGrpHdr()} ({@link GroupHeader93#getInstdAgt()}) and
 * {@link FIToFICustomerCreditTransferV08#getCdtTrfTxInf()} (each
 * {@link CreditTransferTransaction39#getInstdAgt()}). The generated model is consumed strictly
 * read-only; no setters and no {@code MxNode} navigation are used.
 *
 * <p><b>Resilience.</b> Consistent with the {@link CbprRule} contract,
 * {@link #check(MxPacs00800108)} never throws: every navigation hop is null-guarded and a
 * {@code null} message, {@code null} document root, {@code null} group header or {@code null}
 * transaction simply yields an empty (or partial) finding list rather than an exception.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @since 10.3.14
 */
public class InstructedAgentGroupVsTxRule implements CbprRule<MxPacs00800108> {

    /** The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory. */
    private static final String RULE_ID = "instructed-agent-group-vs-tx";

    /**
     * Evaluates the {@code instructed-agent-group-vs-tx} rule (CBPR+ "R6") against the supplied
     * pacs.008.001.08 message.
     *
     * <p>When {@code GrpHdr/InstdAgt} is present, this method returns one {@link Severity#ERROR}
     * {@link Finding} for every {@code CdtTrfTxInf} occurrence that also carries its own
     * {@code InstdAgt}. When the group-level Instructed Agent is absent, or when no transaction
     * restates it, an empty list is returned. The method never throws and never returns
     * {@code null}.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings (empty when the message complies with this rule), never
     *     {@code null}
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

        // The rule only applies when the group header carries an Instructed Agent. Presence is a
        // pure object-identity test; the agent's BICFI or other content is intentionally not read.
        GroupHeader93 grpHdr = cdtTrf.getGrpHdr();
        boolean grpInstdPresent = grpHdr != null && grpHdr.getInstdAgt() != null;
        if (!grpInstdPresent) {
            return findings;
        }

        // Group-level InstdAgt is present, so no transaction may restate its own InstdAgt. Emit one
        // finding per offending transaction to keep collect-all reporting precise.
        List<CreditTransferTransaction39> txs = cdtTrf.getCdtTrfTxInf();
        if (txs == null) {
            return findings;
        }

        for (int i = 0; i < txs.size(); i++) {
            CreditTransferTransaction39 tx = txs.get(i);
            if (tx == null) {
                continue;
            }
            if (tx.getInstdAgt() != null) {
                findings.add(new Finding(
                        RULE_ID,
                        Severity.ERROR,
                        "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]/InstdAgt",
                        "GrpHdr/InstdAgt is present, so transaction-level InstdAgt must not be present (CBPR+ R6)."));
            }
        }

        return findings;
    }
}
