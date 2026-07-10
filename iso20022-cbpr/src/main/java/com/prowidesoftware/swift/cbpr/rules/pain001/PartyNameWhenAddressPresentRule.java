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
package com.prowidesoftware.swift.cbpr.rules.pain001;

import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction34;
import com.prowidesoftware.swift.model.mx.dic.CustomerCreditTransferInitiationV09;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader85;
import com.prowidesoftware.swift.model.mx.dic.PaymentInstruction30;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule
 * <strong>{@code party-name-mandatory-when-address-present}</strong> for
 * <strong>pain.001.001.09</strong> (Customer Credit Transfer Initiation).
 *
 * <p><em>Rule (verbatim, severity {@link Severity#ERROR}):</em> &ldquo;If a party postal address is
 * present, party Name must be present.&rdquo;
 *
 * <h2>Scope and interpretation</h2>
 *
 * <p>The rule is scoped to the <strong>parties</strong> (never the agents) of the message, each
 * modelled as a {@link PartyIdentification135}. Unlike a flat FI-to-FI message, pain.001 carries its
 * parties at three distinct structural levels, and this rule inspects each occurrence at its canonical
 * location:
 *
 * <ul>
 *   <li>{@code InitgPty} &mdash; the initiating party, carried once at group-header level
 *       ({@link GroupHeader85#getInitgPty()});
 *   <li>{@code Dbtr} and {@code UltmtDbtr} &mdash; the debtor and ultimate debtor, carried per
 *       payment instruction at the {@code PaymentInformation} level
 *       ({@link PaymentInstruction30#getDbtr()} and {@link PaymentInstruction30#getUltmtDbtr()});
 *   <li>{@code UltmtDbtr}, {@code Cdtr} and {@code UltmtCdtr} &mdash; the transaction-level ultimate
 *       debtor, creditor and ultimate creditor, carried per transaction
 *       ({@link CreditTransferTransaction34#getUltmtDbtr()},
 *       {@link CreditTransferTransaction34#getCdtr()} and
 *       {@link CreditTransferTransaction34#getUltmtCdtr()}).
 * </ul>
 *
 * <p>The authoritative CBPR+ inventory scopes this rule to {@code Debtor}, {@code Creditor},
 * {@code UltmtDbtr}, {@code UltmtCdtr} and {@code InitgPty} without confining {@code UltmtDbtr} to a
 * single structural level. Because the generated pain.001 model carries an ultimate debtor at
 * <em>both</em> the {@code PaymentInformation} level ({@link PaymentInstruction30#getUltmtDbtr()}) and
 * the transaction level ({@link CreditTransferTransaction34#getUltmtDbtr()}), this rule inspects
 * {@code UltmtDbtr} at both. That is intentionally broader than the narrower
 * {@code StructuredAddressRule}, whose inventory entry explicitly confines the ultimate debtor to the
 * {@code PaymentInformation} level.
 *
 * <p>The trigger is strictly the <em>presence of the postal-address object</em>: a party postal
 * address is considered present when {@link PartyIdentification135#getPstlAdr()} is non-{@code null}.
 * This is intentionally independent of whether that address is structurally compliant &mdash; the
 * Structured / Hybrid / Unstructured quality of an address is the sole concern of the separate
 * {@code StructuredAddressRule}, so this rule performs <em>no</em> structural inspection. When (and
 * only when) an address is present, the party {@code Name} must also be present. A name is treated as
 * present only when {@link PartyIdentification135#getNm()} is non-blank:
 * {@link StringUtils#isBlank(CharSequence)} is used deliberately so that a {@code null}, empty
 * ({@code <Nm></Nm>}) or whitespace-only name all count as absent and therefore fail the rule. A party
 * identified purely by an organisation/BIC identifier and carrying no postal address neither triggers
 * nor fails this rule.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>Consistent with the {@link CbprRule} contract, this rule <strong>never throws</strong>: every
 * navigation step is null-guarded, absent optional branches yield a clean pass, and each distinct
 * violation is reported as its own {@link Finding} (severity {@link Severity#ERROR}). The rule
 * collects every violation across the initiating party, all payment instructions and all transactions
 * rather than stopping at the first, and an empty result signals full compliance.
 *
 * <p>The generated {@code Mx*} / {@code dic} model is consumed strictly read-only through typed getter
 * chains; the rule holds no state and is safe to share across threads.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPain00100109
 * @since 10.3.14
 */
public class PartyNameWhenAddressPresentRule implements CbprRule<MxPain00100109> {

    /**
     * The CBPR+ rule identifier, in kebab-case, taken verbatim from the authoritative SR2026 rule
     * inventory for pain.001.001.09.
     */
    private static final String RULE_ID = "party-name-mandatory-when-address-present";

    /** Human-readable explanation attached to every finding this rule emits. */
    private static final String MESSAGE =
            "Party Name (Nm) is mandatory when a postal address (PstlAdr) is present.";

    /**
     * Evaluates the {@code party-name-mandatory-when-address-present} rule against the supplied
     * pain.001.001.09 message.
     *
     * <p>Inspects the initiating party at group-header level, then walks every
     * {@link PaymentInstruction30} in {@code CstmrCdtTrfInitn/PmtInf} to inspect its {@code Dbtr} and
     * {@code UltmtDbtr}, and every {@link CreditTransferTransaction34} in each instruction's
     * {@code CdtTrfTxInf} to inspect its {@code UltmtDbtr}, {@code Cdtr} and {@code UltmtCdtr}. A
     * {@link Finding} is emitted for every party that carries a postal address but no (non-blank)
     * name. Every branch is null-guarded, so the method returns a (possibly empty) list and never
     * throws &mdash; even for a {@code null} message or a message whose credit-transfer-initiation
     * container is absent.
     *
     * @param message the parsed pain.001.001.09 message to validate; may be {@code null} or partial
     * @return the list of findings; empty when the message complies with this rule, never {@code null}
     */
    @Override
    public List<Finding> check(MxPain00100109 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }
        CustomerCreditTransferInitiationV09 root = message.getCstmrCdtTrfInitn();
        if (root == null) {
            return findings;
        }
        GroupHeader85 grpHdr = root.getGrpHdr();
        if (grpHdr != null) {
            checkParty(grpHdr.getInitgPty(), "CstmrCdtTrfInitn/GrpHdr/InitgPty", findings);
        }
        // getPmtInf() lazily initializes and never returns null.
        List<PaymentInstruction30> pmtInfs = root.getPmtInf();
        for (int i = 0; i < pmtInfs.size(); i++) {
            PaymentInstruction30 pi = pmtInfs.get(i);
            if (pi == null) {
                continue;
            }
            String piPath = "CstmrCdtTrfInitn/PmtInf[" + i + "]";
            checkParty(pi.getDbtr(), piPath + "/Dbtr", findings);
            checkParty(pi.getUltmtDbtr(), piPath + "/UltmtDbtr", findings);
            // getCdtTrfTxInf() lazily initializes and never returns null.
            List<CreditTransferTransaction34> txs = pi.getCdtTrfTxInf();
            for (int j = 0; j < txs.size(); j++) {
                CreditTransferTransaction34 tx = txs.get(j);
                if (tx == null) {
                    continue;
                }
                String txPath = piPath + "/CdtTrfTxInf[" + j + "]";
                checkParty(tx.getUltmtDbtr(), txPath + "/UltmtDbtr", findings);
                checkParty(tx.getCdtr(), txPath + "/Cdtr", findings);
                checkParty(tx.getUltmtCdtr(), txPath + "/UltmtCdtr", findings);
            }
        }
        return findings;
    }

    /**
     * Applies the rule to a single party occurrence, appending a {@link Finding} to {@code findings}
     * when the party carries a postal address but no (non-blank) name.
     *
     * <p>The check is fully null-safe: a {@code null} party is skipped (the party is simply not
     * present), and a party without a postal address never triggers the rule regardless of its name.
     * The trigger is the presence of the postal-address object alone; its structural quality is not
     * inspected here.
     *
     * @param party the party to inspect; may be {@code null}
     * @param elementPath the human-readable element path of the party (for example
     *     {@code CstmrCdtTrfInitn/PmtInf[0]/Dbtr}); the emitted finding points at
     *     {@code elementPath/Nm}
     * @param findings the mutable collector to which any finding is appended
     */
    private void checkParty(PartyIdentification135 party, String elementPath, List<Finding> findings) {
        if (party == null) {
            return;
        }
        if (party.getPstlAdr() == null) {
            return; // no postal address -> rule not applicable
        }
        if (StringUtils.isBlank(party.getNm())) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, elementPath + "/Nm", MESSAGE));
        }
    }
}
