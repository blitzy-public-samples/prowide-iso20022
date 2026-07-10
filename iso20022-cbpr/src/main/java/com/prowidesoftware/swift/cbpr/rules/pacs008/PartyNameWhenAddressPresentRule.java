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
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule
 * <strong>{@code party-name-mandatory-when-address-present}</strong> for
 * <strong>pacs.008.001.08</strong> (FI-to-FI Customer Credit Transfer).
 *
 * <p><em>Rule (verbatim, severity {@link Severity#ERROR}):</em> &ldquo;If a party postal address is
 * present, party Name must be present.&rdquo;
 *
 * <h2>Scope and interpretation</h2>
 *
 * <p>The rule is scoped to the <strong>parties</strong> (never the agents) carried by every
 * {@link CreditTransferTransaction39} in the message: {@code Dbtr}, {@code Cdtr},
 * {@code UltmtDbtr}, {@code UltmtCdtr} and {@code InitgPty}, each modelled as a
 * {@link PartyIdentification135}. Agents are {@code FinInstnId}-based financial-institution
 * identifications and are governed by the dedicated agent rules, not by this one.
 *
 * <p>The trigger is strictly the <em>presence of the postal-address object</em>: a party postal
 * address is considered present when {@link PartyIdentification135#getPstlAdr()} is non-{@code null}.
 * This is intentionally independent of whether that address is structurally compliant &mdash; the
 * Structured / Hybrid / Unstructured quality of an address is the sole concern of the separate
 * {@code StructuredAddressRule}, so this rule performs <em>no</em> structural inspection and does not
 * consult the shared postal-address classifier. When (and only when) an address is present, the party
 * {@code Name} must also be present. A name is treated as present only when
 * {@link PartyIdentification135#getNm()} is non-blank: {@link StringUtils#isBlank(CharSequence)} is
 * used deliberately so that a {@code null}, empty ({@code <Nm></Nm>}) or whitespace-only name all
 * count as absent and therefore fail the rule.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>Consistent with the {@link CbprRule} contract, this rule <strong>never throws</strong>: every
 * navigation step is null-guarded, absent optional branches yield a clean pass, and each distinct
 * violation is reported as its own {@link Finding} (severity {@link Severity#ERROR}). The rule
 * collects every violation across all transactions and all five party occurrences rather than
 * stopping at the first, and an empty result signals full compliance.
 *
 * <p>The generated {@code Mx*} / {@code dic} model is consumed strictly read-only through typed
 * getter chains; the rule holds no state and is safe to share across threads.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPacs00800108
 * @since 10.3.14
 */
public class PartyNameWhenAddressPresentRule implements CbprRule<MxPacs00800108> {

    /**
     * The CBPR+ rule identifier, in kebab-case, taken verbatim from the authoritative SR2026 rule
     * inventory for pacs.008.001.08.
     */
    private static final String RULE_ID = "party-name-mandatory-when-address-present";

    /** Human-readable explanation attached to every finding this rule emits. */
    private static final String MESSAGE =
            "Party has a postal address but no Name; CBPR+ requires the party Name when a postal address is present.";

    /**
     * Evaluates the {@code party-name-mandatory-when-address-present} rule against the supplied
     * pacs.008.001.08 message.
     *
     * <p>Walks every {@link CreditTransferTransaction39} in
     * {@code FIToFICstmrCdtTrf/CdtTrfTxInf} and, for each transaction, inspects the five party
     * occurrences ({@code Dbtr}, {@code Cdtr}, {@code UltmtDbtr}, {@code UltmtCdtr} and
     * {@code InitgPty}). A {@link Finding} is emitted for every party that carries a postal address
     * but no (non-blank) name. Every branch is null-guarded, so the method returns a (possibly empty)
     * list and never throws &mdash; even for a {@code null} message or a message whose credit-transfer
     * container or transaction list is absent.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings; empty when the message complies with this rule, never {@code null}
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
        List<CreditTransferTransaction39> txs = cdtTrf.getCdtTrfTxInf();
        if (txs == null) {
            return findings;
        }
        for (int i = 0; i < txs.size(); i++) {
            CreditTransferTransaction39 tx = txs.get(i);
            if (tx == null) {
                continue;
            }
            String base = "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]";
            checkParty(tx.getDbtr(), base + "/Dbtr", findings);
            checkParty(tx.getCdtr(), base + "/Cdtr", findings);
            checkParty(tx.getUltmtDbtr(), base + "/UltmtDbtr", findings);
            checkParty(tx.getUltmtCdtr(), base + "/UltmtCdtr", findings);
            checkParty(tx.getInitgPty(), base + "/InitgPty", findings);
        }
        return findings;
    }

    /**
     * Applies the rule to a single party occurrence, appending a {@link Finding} to {@code findings}
     * when the party carries a postal address but no (non-blank) name.
     *
     * <p>The check is fully null-safe: a {@code null} party is skipped (the party is simply not
     * present), and a party without a postal address never triggers the rule regardless of its name.
     * The trigger is the presence of the {@link PostalAddress24} object alone; its structural quality
     * is not inspected here.
     *
     * @param party the party to inspect; may be {@code null}
     * @param path the human-readable element path of the party (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/Cdtr}); the emitted finding points at {@code path/Nm}
     * @param findings the mutable collector to which any finding is appended
     */
    private void checkParty(PartyIdentification135 party, String path, List<Finding> findings) {
        if (party == null) {
            return;
        }
        PostalAddress24 postalAddress = party.getPstlAdr();
        if (postalAddress != null && StringUtils.isBlank(party.getNm())) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, path + "/Nm", MESSAGE));
        }
    }
}
