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
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code interbank-settlement-date-presence}</strong>
 * for <strong>pacs.008.001.08</strong> (FI-to-FI Customer Credit Transfer).
 *
 * <p>This class encodes exactly one rule application from the authoritative CBPR+ rule inventory,
 * catalogued there as rule <strong>"R11"</strong> with severity {@link Severity#ERROR}:
 *
 * <blockquote>
 * <em>"If {@code GrpHdr/IntrBkSttlmDt} absent, {@code CdtTrfTxInf/IntrBkSttlmDt} must be present."</em>
 * </blockquote>
 *
 * <h2>Interpretation</h2>
 *
 * <p>The interbank settlement date ({@code IntrBkSttlmDt}) may be stated once at the group-header
 * level, in which case it applies to every transaction in the message. When the group-header date is
 * <em>absent</em>, each individual transaction must instead carry its own date. Concretely:
 *
 * <ul>
 *   <li>If {@code FIToFICstmrCdtTrf/GrpHdr/IntrBkSttlmDt} is present, the rule is satisfied for the
 *       whole message and produces <strong>no</strong> findings (transactions inherit the group
 *       date), regardless of whether individual transactions also state one.
 *   <li>If the group-header date is absent (the group header is missing, or present but without an
 *       {@code IntrBkSttlmDt}), then every {@code CdtTrfTxInf} entry is inspected: each transaction
 *       whose own {@code IntrBkSttlmDt} is missing yields exactly one {@link Severity#ERROR} finding.
 *       All offending transactions are reported (the rule never stops at the first) so that callers
 *       receive a complete, collect-all result.
 * </ul>
 *
 * <p>Presence is a pure {@code null} test on the parsed {@link java.time.LocalDate}; the date value is
 * never parsed, formatted or otherwise interpreted by this rule. A {@code null} group header is
 * treated as "group date absent", which makes a per-transaction date mandatory.
 *
 * <h2>Resilience</h2>
 *
 * <p>In line with the {@link CbprRule} contract, {@link #check(MxPacs00800108)} <strong>never
 * throws</strong>: a {@code null} message, a {@code null} message body, a {@code null} transaction
 * list and {@code null} list entries are all tolerated and simply short-circuit to (or are skipped
 * within) an empty result. Navigation uses read-only typed getters exclusively.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPacs00800108
 * @since 10.3.14
 */
public final class InterbankSettlementDatePresenceRule implements CbprRule<MxPacs00800108> {

    /**
     * The CBPR+ rule identifier for this rule, taken verbatim from the authoritative rule inventory
     * (kebab-case). Emitted on every {@link Finding} this rule produces.
     */
    private static final String RULE_ID = "interbank-settlement-date-presence";

    /**
     * Evaluates the pacs.008 R11 interbank-settlement-date presence rule against the supplied message.
     *
     * <p>Returns an empty list when the message complies &mdash; either the group-header
     * {@code IntrBkSttlmDt} is present (covering all transactions), or every transaction carries its
     * own date. When the group-header date is absent, one {@link Severity#ERROR} {@link Finding} is
     * returned per transaction that lacks its own {@code IntrBkSttlmDt}. The method never returns
     * {@code null} and never throws.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings, empty when the message satisfies this rule and never {@code null}
     */
    @Override
    public List<Finding> check(MxPacs00800108 message) {
        final List<Finding> findings = new ArrayList<>();

        // Resilience contract (CbprRule): a null or partial message is a normal, expected input and
        // must yield a clean pass rather than an exception.
        if (message == null) {
            return findings;
        }

        final FIToFICustomerCreditTransferV08 cdtTrf = message.getFIToFICstmrCdtTrf();
        if (cdtTrf == null) {
            return findings;
        }

        // A group-header IntrBkSttlmDt, when present, applies to every transaction in the message, so
        // the rule is satisfied for all of them and does not fire. A null group header is treated as
        // "group date absent", which makes a per-transaction date mandatory below.
        final GroupHeader93 grpHdr = cdtTrf.getGrpHdr();
        final boolean groupDatePresent = grpHdr != null && grpHdr.getIntrBkSttlmDt() != null;
        if (groupDatePresent) {
            return findings;
        }

        // Group-level date is absent: each transaction must carry its own IntrBkSttlmDt.
        final List<CreditTransferTransaction39> transactions = cdtTrf.getCdtTrfTxInf();
        if (transactions == null) {
            return findings;
        }

        for (int i = 0; i < transactions.size(); i++) {
            final CreditTransferTransaction39 tx = transactions.get(i);
            if (tx == null) {
                // Defensive: a null list entry represents no transaction to evaluate; skip it without
                // throwing so that the remaining transactions are still checked.
                continue;
            }
            if (tx.getIntrBkSttlmDt() == null) {
                findings.add(new Finding(
                        RULE_ID,
                        Severity.ERROR,
                        "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]/IntrBkSttlmDt",
                        "Interbank Settlement Date is absent at group level, so it must be present on each transaction (CBPR+ R11)."));
            }
        }

        return findings;
    }
}
