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
import com.prowidesoftware.swift.cbpr.PostalAddressClassifier;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction34;
import com.prowidesoftware.swift.model.mx.dic.CustomerCreditTransferInitiationV09;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import com.prowidesoftware.swift.model.mx.dic.PaymentInstruction30;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) <strong>hero rule</strong>
 * {@code structured-address-min-town-country} (severity {@link Severity#ERROR}) for
 * <strong>pain.001.001.09</strong> (Customer Credit Transfer Initiation, {@link MxPain00100109}).
 *
 * <p>The rule enforces the SWIFT structured-address mandate that takes effect on 14 November 2026:
 * every in-scope party postal address carried by a pain.001 message must be either
 * <em>structured</em> or <em>hybrid</em>; a fully-unstructured postal address is no longer permitted
 * on the network. The three authoritative categories (never invented, weakened or strengthened here)
 * are:
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
 * classification is made identically across pacs.008, pacs.009 and pain.001 and across every
 * in-scope party occurrence. This class is merely the pain.001 navigator that locates every postal
 * address subject to the rule and asks the classifier whether each one is compliant.
 *
 * <h2>Addresses inspected (exact scope &mdash; preserved verbatim from the CBPR+ inventory)</h2>
 *
 * <p>For pain.001.001.09 the rule walks, in document order, the postal address of the following
 * parties only:
 *
 * <ul>
 *   <li>at <b>PaymentInformation</b> level, within every {@code PmtInf} block ({@link
 *       PaymentInstruction30}): the {@code Dbtr} (debtor) and {@code UltmtDbtr} (ultimate debtor);
 *   <li>at <b>transaction</b> level, within every {@code CdtTrfTxInf} transaction ({@link
 *       CreditTransferTransaction34}) of every {@code PmtInf} block: the {@code Cdtr} (creditor) and
 *       {@code UltmtCdtr} (ultimate creditor).
 * </ul>
 *
 * <p><b>Scope discipline is a rule-fidelity requirement.</b> This rule deliberately does
 * <em>not</em> inspect {@code InitgPty} (the initiating party is not listed for this rule), the
 * transaction-level {@code UltmtDbtr} (the ultimate debtor is scoped to PaymentInformation, not the
 * transaction), or any <em>agent</em> postal address (this pain.001 rule scopes to parties only).
 * Extending the rule to those elements would <em>strengthen</em> it and is forbidden.
 *
 * <p><b>BIC-only parties are exempt.</b> A party identified by BIC only carries no {@code PstlAdr};
 * such a party is simply skipped &mdash; the classifier is never invoked for a {@code null} address,
 * so no finding is produced.
 *
 * <h2>Behavioural contract</h2>
 *
 * <p>This rule <strong>never throws</strong>. Every navigation step is null-guarded, and the whole
 * generated {@code Mx*}/{@code dic} object graph is consumed <strong>read-only</strong> through
 * typed getters (Algorithm A1); no setter is called and {@code MxNode} is not used. The rule
 * <strong>collects all findings</strong> rather than stopping at the first violation, so a message
 * with several non-compliant parties yields several findings, each carrying an indexed
 * {@code elementPath} (for example {@code CstmrCdtTrfInitn/PmtInf[0]/CdtTrfTxInf[0]/Cdtr/PstlAdr})
 * that unambiguously identifies the offending element. This resilience matters in particular for the
 * partial messages produced by the MT101&rarr;pain.001 migration, whose many absent branches must
 * yield a clean pass or a finding, never an exception. An empty returned list therefore means
 * "compliant (or nothing to check)"; the list is never {@code null}.
 *
 * <p>Instances are stateless and therefore safe to reuse and to share across threads; the validator
 * creates them via the implicit public no-argument constructor.
 *
 * @see PostalAddressClassifier
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPain00100109
 * @since 10.3.14
 */
public class StructuredAddressRule implements CbprRule<MxPain00100109> {

    /** The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory (AAP §0.1.1). */
    private static final String RULE_ID = "structured-address-min-town-country";

    /**
     * Human-readable explanation shared by every finding this rule raises. It describes the single
     * failure mode of the hero rule: a fully-unstructured postal address (address lines present
     * without both a structured town name and country).
     */
    private static final String UNSTRUCTURED_MESSAGE =
            "Postal address must be structured (TwnNm+Ctry, no AdrLine) or hybrid (TwnNm+Ctry + AdrLine); "
                    + "a fully-unstructured address (AdrLine present while TwnNm or Ctry is missing) is not permitted "
                    + "under the CBPR+ SR2026 structured-address mandate.";

    /**
     * Evaluates the structured-address hero rule against a pain.001.001.09 message and returns one
     * {@link Finding} per non-compliant in-scope party postal address.
     *
     * <p>The method is fully null-safe: a {@code null} message, an absent {@code CstmrCdtTrfInitn},
     * an empty {@code PmtInf} list, {@code null} payment instructions, an empty {@code CdtTrfTxInf}
     * list, {@code null} transactions, absent parties and absent postal addresses are all tolerated
     * and simply contribute no findings. An empty returned list therefore means "compliant (or
     * nothing to check)"; the list is never {@code null}.
     *
     * @param message the parsed pain.001.001.09 message to validate; may be {@code null} or partial
     * @return the (possibly empty, never {@code null}) list of structured-address findings
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

        // getPmtInf() is lazily initialised by the generated model and is never null (may be empty).
        List<PaymentInstruction30> pmtInfs = root.getPmtInf();
        for (int i = 0; i < pmtInfs.size(); i++) {
            PaymentInstruction30 pi = pmtInfs.get(i);
            if (pi == null) {
                continue;
            }
            String piPath = "CstmrCdtTrfInitn/PmtInf[" + i + "]";

            // PaymentInformation-level parties: debtor and ultimate debtor.
            classify(pi.getDbtr(), piPath + "/Dbtr/PstlAdr", findings);
            classify(pi.getUltmtDbtr(), piPath + "/UltmtDbtr/PstlAdr", findings);

            // getCdtTrfTxInf() is lazily initialised by the generated model and is never null.
            List<CreditTransferTransaction34> txs = pi.getCdtTrfTxInf();
            for (int j = 0; j < txs.size(); j++) {
                CreditTransferTransaction34 tx = txs.get(j);
                if (tx == null) {
                    continue;
                }
                String txPath = piPath + "/CdtTrfTxInf[" + j + "]";

                // Transaction-level parties: creditor and ultimate creditor.
                classify(tx.getCdtr(), txPath + "/Cdtr/PstlAdr", findings);
                classify(tx.getUltmtCdtr(), txPath + "/UltmtCdtr/PstlAdr", findings);
            }
        }

        return findings;
    }

    /**
     * Inspects a single party's postal address and, when it is present but not compliant, appends a
     * structured-address {@link Severity#ERROR} finding to {@code findings}.
     *
     * <p>A {@code null} party contributes no finding (the party is absent from the message). A party
     * whose {@code PstlAdr} is {@code null} is exempt &mdash; it is identified by BIC (or another
     * non-address identifier) only &mdash; and likewise contributes no finding. Only when a postal
     * address is actually present is the Structured / Hybrid / Unstructured decision made, and that
     * decision is delegated entirely to {@link PostalAddressClassifier#isCompliant(PostalAddress24)}.
     * The method never throws for any input.
     *
     * @param party the party to inspect; may be {@code null}
     * @param elementPath the fully-qualified element path of this party's postal address (already
     *     including the trailing {@code /PstlAdr}), used verbatim as the finding's element path (for
     *     example {@code CstmrCdtTrfInitn/PmtInf[0]/Dbtr/PstlAdr})
     * @param findings the mutable accumulator to which a finding is appended when the address is
     *     unstructured
     */
    private void classify(PartyIdentification135 party, String elementPath, List<Finding> findings) {
        if (party == null) {
            return;
        }
        PostalAddress24 addr = party.getPstlAdr();
        if (addr == null) {
            // BIC-only / no postal address present: the party is exempt from the structured-address rule.
            return;
        }
        if (!PostalAddressClassifier.isCompliant(addr)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, elementPath, UNSTRUCTURED_MESSAGE));
        }
    }
}
