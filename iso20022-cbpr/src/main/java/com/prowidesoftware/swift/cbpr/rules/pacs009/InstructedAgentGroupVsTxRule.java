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
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule {@code instructed-agent-group-vs-tx} for
 * <strong>pacs.009.001.08</strong> (Financial Institution Credit Transfer, {@link MxPacs00900108}).
 *
 * <p>This class implements rule <strong>R6</strong> of the authoritative CBPR+ rule inventory
 * (AAP &sect;0.1.1). Its canonical requirement, shared with the pacs.008 application of the same
 * identifier, is:
 *
 * <blockquote>If {@code GrpHdr/InstdAgt} is present, {@code CdtTrfTxInf/InstdAgt} must not be
 * present.</blockquote>
 *
 * <p>The Instructed Agent may be specified <em>either</em> once at the group level (in
 * {@code GrpHdr}) <em>or</em> individually per transaction (in {@code CdtTrfTxInf}), but never in
 * both places at once. When the group header carries an {@code InstdAgt}, that single value applies
 * to every transaction in the message, so repeating it at the transaction level is redundant and
 * therefore forbidden. This rule enforces that mutual exclusivity.
 *
 * <h2>Scope and severity</h2>
 *
 * <p>The rule is a pure structural check between the group-level and transaction-level
 * {@code InstdAgt} elements; it makes <em>no</em> reference to postal addresses, party names or
 * BICFI content. Every violation it reports carries {@link Severity#ERROR}, matching the
 * {@code error} classification declared for this rule in the inventory.
 *
 * <h2>Applicability</h2>
 *
 * <p>R6 only applies when the group-level {@code InstdAgt} is actually present. If
 * {@code FICdtTrf/GrpHdr} is absent, or if {@code GrpHdr/InstdAgt} is absent, then a
 * transaction-level {@code InstdAgt} is perfectly legitimate and this rule is a no-op (it returns an
 * empty list). Only when the group-level {@code InstdAgt} is present does the presence of any
 * transaction-level {@code InstdAgt} become a violation.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>In accordance with the {@link CbprRule} contract, {@link #check(MxPacs00900108) check}
 * <strong>never throws</strong>: a {@code null} message, an absent {@code FICdtTrf}, an absent
 * {@code GrpHdr}, and {@code null} entries within the transaction list are all tolerated and simply
 * yield a clean pass or the appropriate findings. An empty returned list means the message complies;
 * a non-empty list reports one {@link Finding} for <em>each</em> transaction that carries its own
 * {@code InstdAgt} while the group header also carries one &mdash; the rule reports all violations
 * rather than stopping at the first. The generated model is consumed strictly read-only, through
 * typed getter chains only.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPacs00900108
 * @since 10.3.14
 */
public final class InstructedAgentGroupVsTxRule implements CbprRule<MxPacs00900108> {

    /**
     * The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory. Every
     * {@link Finding} produced by this rule reports this identifier.
     */
    private static final String RULE_ID = "instructed-agent-group-vs-tx";

    /**
     * The human-readable explanation attached to every finding produced by this rule.
     */
    private static final String VIOLATION_MESSAGE =
            "R6: GrpHdr/InstdAgt is present, so CdtTrfTxInf/InstdAgt must not be present";

    /**
     * Evaluates rule R6 ({@code instructed-agent-group-vs-tx}) against the supplied pacs.009.001.08
     * message.
     *
     * <p>The algorithm is intentionally minimal and defensive:
     *
     * <ol>
     *   <li>Return immediately (compliant, empty list) when the message or its {@code FICdtTrf}
     *       content is absent &mdash; there is nothing to check.
     *   <li>Determine whether the group-level {@code InstdAgt} is present. If it is not (either
     *       because {@code GrpHdr} is absent or because {@code GrpHdr/InstdAgt} is absent), the rule
     *       does not apply and an empty list is returned.
     *   <li>Otherwise, walk every {@code CdtTrfTxInf} entry and, for each transaction that carries
     *       its own {@code InstdAgt}, add one {@link Severity#ERROR} finding whose element path
     *       identifies the offending transaction by its zero-based index.
     * </ol>
     *
     * <p>This method never throws, regardless of how sparse or partial the message is.
     *
     * @param message the parsed pacs.009.001.08 message to validate; may be {@code null} or partial
     * @return a list containing one {@link Finding} per violating transaction, or an empty list when
     *     the message complies with R6; never {@code null}
     */
    @Override
    public List<Finding> check(MxPacs00900108 message) {
        List<Finding> findings = new ArrayList<>();

        // A null message, or a message with no FICdtTrf content, cannot violate R6.
        if (message == null) {
            return findings;
        }
        FinancialInstitutionCreditTransferV08 fiCdtTrf = message.getFICdtTrf();
        if (fiCdtTrf == null) {
            return findings;
        }

        // R6 only applies when the group-level InstdAgt is present. When GrpHdr or its InstdAgt is
        // absent, transaction-level InstdAgt is permitted and the rule is a no-op.
        GroupHeader93 grpHdr = fiCdtTrf.getGrpHdr();
        BranchAndFinancialInstitutionIdentification6 groupInstdAgt = (grpHdr == null) ? null : grpHdr.getInstdAgt();
        boolean groupInstdAgtPresent = groupInstdAgt != null;
        if (!groupInstdAgtPresent) {
            return findings;
        }

        // Group-level InstdAgt is present: no transaction may also carry its own InstdAgt.
        // getCdtTrfTxInf() never returns null (the generated accessor lazily initializes the list).
        List<CreditTransferTransaction36> transactions = fiCdtTrf.getCdtTrfTxInf();
        for (int i = 0; i < transactions.size(); i++) {
            CreditTransferTransaction36 tx = transactions.get(i);
            if (tx == null) {
                continue;
            }
            BranchAndFinancialInstitutionIdentification6 txInstdAgt = tx.getInstdAgt();
            if (txInstdAgt != null) {
                findings.add(new Finding(
                        RULE_ID, Severity.ERROR, "FICdtTrf/CdtTrfTxInf[" + i + "]/InstdAgt", VIOLATION_MESSAGE));
            }
        }

        return findings;
    }
}
