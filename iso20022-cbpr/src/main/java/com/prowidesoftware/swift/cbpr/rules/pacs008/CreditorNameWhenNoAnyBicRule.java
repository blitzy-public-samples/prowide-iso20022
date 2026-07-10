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
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule for <strong>pacs.008.001.08</strong>:
 * <em>creditor identifiability</em>.
 *
 * <p><strong>Rule (verbatim):</strong> &ldquo;If {@code Cdtr/Id/OrgId/AnyBIC} is absent, {@code Cdtr}
 * Name must be present.&rdquo; &mdash; severity {@link Severity#ERROR}, rule identifier
 * {@code creditor-name-mandatory-when-no-anybic}.
 *
 * <p>The intent is that the creditor of a customer credit transfer must always be identifiable. It is
 * identifiable when it either carries an organisation-identifier BIC
 * ({@code Cdtr/Id/OrgId/AnyBIC}) or, failing that, a human-readable name ({@code Cdtr/Nm}). If
 * <em>neither</em> is present the creditor cannot be identified and this rule raises an
 * {@link Severity#ERROR} finding. Because a creditor identified solely by {@code AnyBIC} legitimately
 * carries no name, the presence of a valid {@code AnyBIC} alone satisfies the rule; conversely a
 * present, non-blank name satisfies the rule when no {@code AnyBIC} is supplied.
 *
 * <h2>Scope</h2>
 *
 * <p>The check is applied to the creditor ({@code Cdtr}) of every
 * {@code CreditTransferTransaction39} in {@code FIToFICstmrCdtTrf/CdtTrfTxInf}. A message that carries
 * several transactions yields one finding per non-identifiable creditor; the rule reports them all
 * rather than stopping at the first.
 *
 * <h2>Navigation</h2>
 *
 * <p>The message object graph is traversed exclusively through typed, read-only getters (approach A1),
 * never through setters or {@code MxNode}:
 *
 * <pre>{@code
 * MxPacs00800108
 *   -> getFIToFICstmrCdtTrf()            : FIToFICustomerCreditTransferV08
 *   -> getCdtTrfTxInf()                  : List<CreditTransferTransaction39>
 *      -> getCdtr()                      : PartyIdentification135
 *         -> getNm()                     : String
 *         -> getId().getOrgId().getAnyBIC() : String
 * }</pre>
 *
 * <h2>Resilience</h2>
 *
 * <p>Consistent with the {@link CbprRule} contract, this rule <strong>never throws</strong>. Every
 * hop of the deep {@code Cdtr/Id/OrgId/AnyBIC} chain is null-guarded, and blank string values are
 * treated as absent via {@link StringUtils#isNotBlank(CharSequence)} (an empty {@code <AnyBIC/>} or
 * {@code <Nm/>} counts as missing). A {@code null} message, a {@code null} document root, a
 * {@code null} transaction list, an individual {@code null} transaction and a {@code null} creditor are
 * all handled gracefully: absent creditor identity legitimately produces the finding (as is expected
 * for partial or migrated messages), while a fully absent message tree yields an empty result.
 *
 * <p>The class is stateless and immutable, has an implicit no-argument constructor and is therefore
 * safe to reuse across threads.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPacs00800108
 * @since SRU2026
 */
public final class CreditorNameWhenNoAnyBicRule implements CbprRule<MxPacs00800108> {

    /**
     * The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory.
     *
     * <p>Every {@link Finding} produced by this rule carries this identifier so that callers can trace
     * the finding back to the single specification line it enforces.
     */
    private static final String RULE_ID = "creditor-name-mandatory-when-no-anybic";

    /**
     * Human-readable explanation attached to every finding raised by this rule.
     */
    private static final String MESSAGE =
            "Creditor has no Id/OrgId/AnyBIC and no Name; CBPR+ requires the creditor Name when AnyBIC is absent.";

    /**
     * Evaluates the {@code creditor-name-mandatory-when-no-anybic} rule against the supplied
     * pacs.008.001.08 message.
     *
     * <p>For each {@code CreditTransferTransaction39} present in
     * {@code FIToFICstmrCdtTrf/CdtTrfTxInf}, the creditor is deemed identifiable when it carries a
     * valid organisation-identifier BIC ({@code Cdtr/Id/OrgId/AnyBIC}) <em>or</em> a non-blank name
     * ({@code Cdtr/Nm}). When it carries neither, an {@link Severity#ERROR} finding is added. The
     * method inspects every transaction and aggregates all findings.
     *
     * <p>The method never throws: a {@code null} message, {@code null} document root or {@code null}
     * transaction list results in an empty list, and {@code null} transactions or creditors are
     * handled without raising an exception.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings, one per non-identifiable creditor; empty when the message complies
     *     with this rule and never {@code null}
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
        List<CreditTransferTransaction39> transactions = cdtTrf.getCdtTrfTxInf();
        if (transactions == null) {
            return findings;
        }
        for (int i = 0; i < transactions.size(); i++) {
            CreditTransferTransaction39 tx = transactions.get(i);
            if (tx == null) {
                continue;
            }
            PartyIdentification135 cdtr = tx.getCdtr();
            boolean hasAnyBic = anyBicPresent(cdtr);
            boolean hasName = cdtr != null && StringUtils.isNotBlank(cdtr.getNm());
            if (!hasAnyBic && !hasName) {
                findings.add(
                        new Finding(RULE_ID, Severity.ERROR, "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]/Cdtr", MESSAGE));
            }
        }
        return findings;
    }

    /**
     * Tests whether the given creditor is identified by a non-blank organisation BIC, i.e. whether
     * {@code Cdtr/Id/OrgId/AnyBIC} is present and not blank.
     *
     * <p>Each of the three nullable hops in the chain ({@code getId()}, {@code getOrgId()},
     * {@code getAnyBIC()}) is guarded so that the method never throws, and the terminal value is
     * checked with {@link StringUtils#isNotBlank(CharSequence)} so that an empty {@code <AnyBIC/>}
     * element counts as absent. In line with the rule text, only the {@code OrgId/AnyBIC} path is
     * considered; private-identifier ({@code PrvtId}) paths are intentionally not treated as an
     * {@code AnyBIC}.
     *
     * @param cdtr the creditor party identification to inspect; may be {@code null}
     * @return {@code true} if the creditor carries a non-blank {@code Id/OrgId/AnyBIC}; {@code false}
     *     otherwise (including when {@code cdtr} or any intermediate element is {@code null})
     */
    private boolean anyBicPresent(PartyIdentification135 cdtr) {
        return cdtr != null
                && cdtr.getId() != null
                && cdtr.getId().getOrgId() != null
                && StringUtils.isNotBlank(cdtr.getId().getOrgId().getAnyBIC());
    }
}
