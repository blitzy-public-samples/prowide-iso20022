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
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule
 * <strong>{@code interbank-settlement-date-presence}</strong> (severity {@link Severity#ERROR}) for
 * <strong>pacs.009.001.08</strong> (Financial Institution Credit Transfer, {@link MxPacs00900108}).
 *
 * <p>This is the CBPR+ rule known as <strong>R11</strong>. Its authoritative requirement, preserved
 * verbatim from the CBPR+ rule inventory, is:
 *
 * <blockquote>"If GrpHdr/IntrBkSttlmDt absent, CdtTrfTxInf/IntrBkSttlmDt must be present"</blockquote>
 *
 * <p>The interbank settlement date may be carried once for the whole message at group-header level
 * ({@code FICdtTrf/GrpHdr/IntrBkSttlmDt}) or individually on each transaction
 * ({@code FICdtTrf/CdtTrfTxInf/IntrBkSttlmDt}). The rule is a <em>presence fallback</em>: a settlement
 * date must exist at <em>either</em> the group level <em>or</em> the transaction level. Concretely:
 *
 * <ul>
 *   <li>when the group-header {@code IntrBkSttlmDt} is <strong>present</strong>, the requirement is
 *       satisfied for every transaction and no per-transaction date is required &mdash; the check
 *       short-circuits and reports nothing;
 *   <li>when the group-header {@code IntrBkSttlmDt} is <strong>absent</strong>, each transaction must
 *       carry its own {@code IntrBkSttlmDt}; every transaction that lacks it produces exactly one
 *       {@link Severity#ERROR} {@link Finding}.
 * </ul>
 *
 * <h2>Navigation</h2>
 *
 * <p>The message object graph is traversed exclusively through typed getters (read-only consumption of
 * the generated model &mdash; no {@code MxNode}, no reflection, no model edits):
 * {@link MxPacs00900108#getFICdtTrf()} &rarr;
 * {@link FinancialInstitutionCreditTransferV08#getGrpHdr()} /
 * {@link FinancialInstitutionCreditTransferV08#getCdtTrfTxInf()} &rarr;
 * {@link GroupHeader93#getIntrBkSttlmDt()} and {@link CreditTransferTransaction36#getIntrBkSttlmDt()}.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>In line with {@link CbprRule}, this rule <strong>never throws</strong>. A {@code null} message, a
 * {@code null} {@code FICdtTrf}, a {@code null} group header, a {@code null} transaction element and an
 * empty transaction list are all handled gracefully: they yield findings or a clean pass, never an
 * exception. When the group-header date is absent and the transaction list is empty there is no
 * transaction that could carry the date, so nothing is reported (no synthetic finding is fabricated for
 * a non-existent transaction). The returned list is empty when the message complies and is never
 * {@code null}.
 *
 * <p>Instances are stateless and hold no mutable data, so a single instance may be reused across
 * messages and shared between threads. The (implicit) public no-argument constructor lets the validator
 * create the rule with {@code new InterbankSettlementDatePresenceRule()}.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPacs00900108
 * @since SRU2026
 */
public class InterbankSettlementDatePresenceRule implements CbprRule<MxPacs00900108> {

    /**
     * The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory. Emitted on every
     * {@link Finding} this rule produces.
     */
    private static final String RULE_ID = "interbank-settlement-date-presence";

    /**
     * Human-readable explanation attached to every finding this rule raises. Declared once as a constant
     * so that all findings from this rule share an identical, stable message.
     */
    private static final String MESSAGE =
            "R11: GrpHdr/IntrBkSttlmDt is absent, so CdtTrfTxInf/IntrBkSttlmDt must be present";

    /**
     * Evaluates the {@code interbank-settlement-date-presence} rule (R11) against the supplied
     * pacs.009.001.08 message.
     *
     * <p>The algorithm mirrors the presence-fallback semantics described in the class documentation:
     *
     * <ol>
     *   <li>a {@code null} message or a {@code null} {@code FICdtTrf} means there is nothing to
     *       evaluate &mdash; a clean pass (empty list);
     *   <li>if the group-header {@code IntrBkSttlmDt} is present, the requirement is satisfied for the
     *       whole message and the check returns immediately with no findings;
     *   <li>otherwise every transaction is inspected in order, and each one whose own
     *       {@code IntrBkSttlmDt} is absent (including a {@code null} transaction element) contributes a
     *       single {@link Severity#ERROR} finding whose {@code elementPath} carries the transaction's
     *       zero-based index for precise diagnostics.
     * </ol>
     *
     * @param message the parsed pacs.009.001.08 message to validate; may be {@code null} or partially
     *     populated
     * @return the list of findings; empty when the rule is satisfied and never {@code null}
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

        // The group-header settlement date, when present, satisfies R11 for every transaction: no
        // per-transaction date is then required, so the whole check short-circuits with no findings.
        GroupHeader93 grpHdr = fi.getGrpHdr();
        boolean groupDatePresent = grpHdr != null && grpHdr.getIntrBkSttlmDt() != null;
        if (groupDatePresent) {
            return findings;
        }

        // Group-header date is absent: each transaction must carry its own IntrBkSttlmDt.
        // getCdtTrfTxInf() never returns null (the generated accessor lazily initializes an empty list),
        // and an empty list legitimately yields no findings: there is no transaction to carry the date.
        List<CreditTransferTransaction36> txs = fi.getCdtTrfTxInf();
        for (int i = 0; i < txs.size(); i++) {
            CreditTransferTransaction36 tx = txs.get(i);
            if (tx == null || tx.getIntrBkSttlmDt() == null) {
                findings.add(new Finding(
                        RULE_ID, Severity.ERROR, "FICdtTrf/CdtTrfTxInf[" + i + "]/IntrBkSttlmDt", MESSAGE));
            }
        }

        return findings;
    }
}
