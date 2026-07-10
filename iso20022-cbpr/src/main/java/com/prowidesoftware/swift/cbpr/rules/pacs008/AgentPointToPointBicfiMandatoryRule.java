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
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <b>{@code agent-point-to-point-bicfi-mandatory}</b> for
 * <b>pacs.008.001.08</b> (FI-to-FI Customer Credit Transfer).
 *
 * <p><b>Rule (verbatim):</b> &ldquo;InstgAgt and InstdAgt FinInstnId/BICFI must be present.&rdquo;
 * Severity {@link Severity#ERROR}.
 *
 * <p>The two <em>point-to-point</em> agents of a pacs.008 message &mdash; the Instructing Agent
 * ({@code InstgAgt}) and the Instructed Agent ({@code InstdAgt}) &mdash; identify the immediate
 * sender and receiver of the interbank leg and must each be identified by a Business Identifier Code
 * ({@code FinInstnId/BICFI}). This rule asserts that such a BICFI is <b>present</b> for both agents.
 *
 * <h2>Group-header vs. transaction resolution</h2>
 *
 * <p>In pacs.008 the point-to-point agents may be carried once at the group-header level
 * ({@code FIToFICstmrCdtTrf/GrpHdr}), in which case they apply to every transaction, and/or repeated
 * per transaction ({@code FIToFICstmrCdtTrf/CdtTrfTxInf}). For each transaction the effective
 * {@code InstgAgt} (respectively {@code InstdAgt}) is considered present when a BICFI can be resolved
 * at <em>either</em> the transaction level <em>or</em> the group-header level. An
 * {@link Severity#ERROR} finding is emitted for a transaction only when no BICFI can be found in
 * either scope. A message that carries only a group header and no transactions is still evaluated
 * against the group-header agents.
 *
 * <h2>Scope</h2>
 *
 * <p>This rule verifies <b>presence</b> only; the ISO 9362 <em>format</em> of a BICFI is validated
 * separately by {@code BicfiFormatRule}. It applies exclusively to the point-to-point agents
 * ({@code InstgAgt}, {@code InstdAgt}); the other agents of the message ({@code DbtrAgt},
 * {@code CdtrAgt}, {@code IntrmyAgt1..3}, {@code PrvsInstgAgt1..3}) are out of scope for this rule and
 * are never required to carry a BICFI here.
 *
 * <p><b>Resilience:</b> in line with the module contract this rule never throws. Every navigation hop
 * is null-guarded, a {@code null} message (or any absent branch) yields an empty finding list, and all
 * detected violations are collected rather than stopping at the first.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @since 10.3.14
 */
public class AgentPointToPointBicfiMandatoryRule implements CbprRule<MxPacs00800108> {

    /** The verbatim CBPR+ rule identifier reported by every {@link Finding} this rule emits. */
    private static final String RULE_ID = "agent-point-to-point-bicfi-mandatory";

    /** Human-readable message for a missing Instructing Agent ({@code InstgAgt}) BICFI. */
    private static final String INSTG_MESSAGE =
            "Instructing Agent (InstgAgt) BICFI is mandatory and must be present at group or transaction level.";

    /** Human-readable message for a missing Instructed Agent ({@code InstdAgt}) BICFI. */
    private static final String INSTD_MESSAGE =
            "Instructed Agent (InstdAgt) BICFI is mandatory and must be present at group or transaction level.";

    /**
     * Evaluates the {@code agent-point-to-point-bicfi-mandatory} rule against the supplied
     * pacs.008.001.08 message.
     *
     * <p>Resolves the group-header {@code InstgAgt}/{@code InstdAgt} BICFI presence once, then checks
     * every transaction: an {@link Severity#ERROR} finding is added when neither the transaction nor
     * the group header supplies a BICFI for a given point-to-point agent. When the message carries no
     * transactions, the group-header agents alone are evaluated.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings, empty when both point-to-point agents are identified by a BICFI
     *     (never {@code null})
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

        // Point-to-point agents carried at the group-header level apply to every transaction.
        GroupHeader93 grpHdr = cdtTrf.getGrpHdr();
        boolean grpInstg = grpHdr != null && bicfiPresent(grpHdr.getInstgAgt());
        boolean grpInstd = grpHdr != null && bicfiPresent(grpHdr.getInstdAgt());

        List<CreditTransferTransaction39> txs = cdtTrf.getCdtTrfTxInf();
        if (txs == null || txs.isEmpty()) {
            // Header-only shape (no transactions): evaluate the group-header agents on their own.
            if (!grpInstg) {
                findings.add(new Finding(
                        RULE_ID, Severity.ERROR, "FIToFICstmrCdtTrf/GrpHdr/InstgAgt/FinInstnId/BICFI", INSTG_MESSAGE));
            }
            if (!grpInstd) {
                findings.add(new Finding(
                        RULE_ID, Severity.ERROR, "FIToFICstmrCdtTrf/GrpHdr/InstdAgt/FinInstnId/BICFI", INSTD_MESSAGE));
            }
            return findings;
        }

        for (int i = 0; i < txs.size(); i++) {
            CreditTransferTransaction39 tx = txs.get(i);
            if (tx == null) {
                continue;
            }
            String base = "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]";

            // The effective agent is present when a BICFI exists at the transaction OR group level.
            boolean instg = grpInstg || bicfiPresent(tx.getInstgAgt());
            if (!instg) {
                findings.add(new Finding(RULE_ID, Severity.ERROR, base + "/InstgAgt/FinInstnId/BICFI", INSTG_MESSAGE));
            }

            boolean instd = grpInstd || bicfiPresent(tx.getInstdAgt());
            if (!instd) {
                findings.add(new Finding(RULE_ID, Severity.ERROR, base + "/InstdAgt/FinInstnId/BICFI", INSTD_MESSAGE));
            }
        }
        return findings;
    }

    /**
     * Returns whether the supplied agent carries a non-blank {@code FinInstnId/BICFI}.
     *
     * <p>Fully null-safe: a {@code null} agent, a {@code null} {@code FinInstnId} or a {@code null} /
     * blank BICFI all yield {@code false} without throwing.
     *
     * @param agent the agent to inspect; may be {@code null}
     * @return {@code true} if a non-blank BICFI is present, {@code false} otherwise
     */
    private boolean bicfiPresent(BranchAndFinancialInstitutionIdentification6 agent) {
        if (agent == null) {
            return false;
        }
        FinancialInstitutionIdentification18 finInstnId = agent.getFinInstnId();
        return finInstnId != null && StringUtils.isNotBlank(finInstnId.getBICFI());
    }
}
