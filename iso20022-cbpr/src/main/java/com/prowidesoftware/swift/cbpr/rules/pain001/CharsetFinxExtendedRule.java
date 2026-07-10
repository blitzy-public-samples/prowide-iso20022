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
import com.prowidesoftware.swift.cbpr.FinXCharset;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction34;
import com.prowidesoftware.swift.model.mx.dic.CustomerCreditTransferInitiationV09;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader85;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import com.prowidesoftware.swift.model.mx.dic.PaymentInstruction30;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code charset-finx-extended-for-name-address}</strong>
 * for <strong>pain.001.001.09</strong> (Customer Credit Transfer Initiation).
 *
 * <p>The requirement is preserved verbatim from the authoritative CBPR+ rule inventory (severity
 * {@link Severity#ERROR}):
 *
 * <blockquote><em>"FIN-X charset; Name/Address/Remittance/proxy-email additionally permit an extended
 * punctuation set."</em></blockquote>
 *
 * <p>On a pain.001 message this rule verifies that the payment parties' free-text <em>Name</em> and postal
 * <em>Address</em> components are expressed only in the SWIFT FIN-X ("X") character set, which for these fields
 * additionally permits the extended punctuation set. Any character outside that base-plus-extended set (for
 * example an accented letter such as {@code é}, which is not part of FIN-X, or a control character) is a
 * violation and yields one {@link Finding} of severity {@link Severity#ERROR} identifying the offending element.
 *
 * <h2>Fields inspected</h2>
 *
 * <p>The rule inspects the same five party roles used by the other party-oriented pain.001 rules
 * ({@code StructuredAddressRule} / {@code PartyNameWhenAddressPresentRule}), namely:
 *
 * <ul>
 *   <li><strong>{@code InitgPty}</strong> &mdash; the group-header initiating party
 *       ({@code CstmrCdtTrfInitn/GrpHdr/InitgPty});
 *   <li><strong>{@code Dbtr}</strong> and <strong>{@code UltmtDbtr}</strong> &mdash; the debtor and ultimate
 *       debtor at PaymentInformation level ({@code CstmrCdtTrfInitn/PmtInf[i]});
 *   <li><strong>{@code Cdtr}</strong> and <strong>{@code UltmtCdtr}</strong> &mdash; the creditor and ultimate
 *       creditor at transaction level ({@code CstmrCdtTrfInitn/PmtInf[i]/CdtTrfTxInf[j]}).
 * </ul>
 *
 * <p>For each present party the rule tests the party {@code Nm} and, when a {@code PstlAdr} is present, its
 * free-text address components {@code TwnNm} and every {@code AdrLine} entry. Checking {@code Nm},
 * {@code TwnNm} and the {@code AdrLine} entries is the primary, sufficient scope for this rule application:
 * agent-name charset checking is intentionally <em>out of primary scope</em> (documented as optional in the
 * inventory) and is deliberately <strong>not</strong> performed here, so as never to strengthen the rule
 * beyond the authoritative CBPR+ inventory.
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li><strong>Single source of truth for the character set.</strong> The base-plus-extended membership test
 *       is delegated entirely to {@link FinXCharset#isValidExtended(String)}; this rule never re-declares the
 *       character set. Because that predicate treats {@code null} and empty input as valid (an absent value is
 *       a presence concern enforced by other rules, not a charset violation), this rule needs no explicit
 *       "field present?" gate: every candidate string is simply passed through the predicate.
 *   <li><strong>Read-only, typed navigation.</strong> The message object graph is traversed exclusively
 *       through generated typed getters (approach A1); no setters, reflection or {@code MxNode} path
 *       navigation is used, and no generated model is modified.
 *   <li><strong>Never throws.</strong> Per the {@link CbprRule} contract, every navigation hop is
 *       null-guarded and the list-valued members ({@code PmtInf}, {@code CdtTrfTxInf}, {@code AdrLine}) are
 *       lazily initialized non-null lists that are iterated directly. A sparse or partial (for example
 *       migrated) message therefore yields findings or a clean pass, never an exception.
 *   <li><strong>Collect all findings.</strong> One {@link Finding} is emitted per offending value; the rule
 *       reports every violation it detects rather than stopping at the first.
 * </ul>
 *
 * <p>The rule is stateless and therefore safe to reuse across threads; instances are created through the
 * implicit public no-argument constructor (the validator does {@code new CharsetFinxExtendedRule()}).
 *
 * @see FinXCharset#isValidExtended(String)
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @since SRU2026
 */
public final class CharsetFinxExtendedRule implements CbprRule<MxPain00100109> {

    /**
     * The CBPR+ rule identifier, in kebab-case, taken verbatim from the authoritative rule inventory.
     */
    private static final String RULE_ID = "charset-finx-extended-for-name-address";

    /**
     * The human-readable explanation attached to every finding this rule emits.
     */
    private static final String MESSAGE =
            "Value contains characters outside the permitted FIN-X extended character set.";

    /**
     * Evaluates the {@code charset-finx-extended-for-name-address} rule against the supplied
     * pain.001.001.09 message.
     *
     * <p>Walks the initiating party, the debtor/ultimate-debtor of every {@code PmtInf}, and the
     * creditor/ultimate-creditor of every {@code CdtTrfTxInf}, validating each party's free-text {@code Nm}
     * and postal-address components ({@code TwnNm} and each {@code AdrLine}) against the FIN-X extended
     * character set. A {@link Finding} of severity {@link Severity#ERROR} is added for every value that
     * contains a character outside that set.
     *
     * <p>The method never throws and never returns {@code null}: an empty list means the message complies,
     * while every element of a non-empty list describes a distinct offending value. A {@code null} message or
     * a {@code null} document root yields an empty list.
     *
     * @param message the parsed pain.001.001.09 message to validate; may be {@code null} or partial
     * @return the list of charset findings, empty when every inspected value is FIN-X-extended compliant;
     *     never {@code null}
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

        // Group-header initiating party.
        GroupHeader85 grpHdr = root.getGrpHdr();
        if (grpHdr != null) {
            checkParty(grpHdr.getInitgPty(), "CstmrCdtTrfInitn/GrpHdr/InitgPty", findings);
        }

        // PaymentInformation-level debtor / ultimate debtor, then transaction-level creditor / ultimate creditor.
        List<PaymentInstruction30> pmtInfs = root.getPmtInf(); // lazy, never null
        for (int i = 0; i < pmtInfs.size(); i++) {
            PaymentInstruction30 pi = pmtInfs.get(i);
            if (pi == null) {
                continue;
            }
            String piPath = "CstmrCdtTrfInitn/PmtInf[" + i + "]";
            checkParty(pi.getDbtr(), piPath + "/Dbtr", findings);
            checkParty(pi.getUltmtDbtr(), piPath + "/UltmtDbtr", findings);

            List<CreditTransferTransaction34> txs = pi.getCdtTrfTxInf(); // lazy, never null
            for (int j = 0; j < txs.size(); j++) {
                CreditTransferTransaction34 tx = txs.get(j);
                if (tx == null) {
                    continue;
                }
                String txPath = piPath + "/CdtTrfTxInf[" + j + "]";
                checkParty(tx.getCdtr(), txPath + "/Cdtr", findings);
                checkParty(tx.getUltmtCdtr(), txPath + "/UltmtCdtr", findings);
            }
        }
        return findings;
    }

    /**
     * Inspects a single party's free-text surfaces: its {@code Nm} and, when a {@code PstlAdr} is present,
     * its {@code TwnNm} and every {@code AdrLine} entry.
     *
     * <p>A {@code null} party contributes no findings. Each candidate string is passed through
     * {@link #checkText(String, String, List)}, which delegates to {@link FinXCharset#isValidExtended(String)};
     * absent (null/empty) values therefore never produce a finding.
     *
     * @param party the party to inspect; ignored when {@code null}
     * @param partyPath the element path prefix identifying this party (for example
     *     {@code CstmrCdtTrfInitn/PmtInf[0]/Dbtr}); the sub-element suffix is appended per checked value
     * @param findings the accumulator to which any findings are appended
     */
    private void checkParty(PartyIdentification135 party, String partyPath, List<Finding> findings) {
        if (party == null) {
            return;
        }
        checkText(party.getNm(), partyPath + "/Nm", findings);
        PostalAddress24 addr = party.getPstlAdr();
        if (addr != null) {
            checkText(addr.getTwnNm(), partyPath + "/PstlAdr/TwnNm", findings);
            List<String> adrLines = addr.getAdrLine(); // lazy, never null (entries may be null)
            for (int k = 0; k < adrLines.size(); k++) {
                checkText(adrLines.get(k), partyPath + "/PstlAdr/AdrLine[" + k + "]", findings);
            }
        }
    }

    /**
     * Validates a single string value against the FIN-X extended character set, adding an
     * {@link Severity#ERROR} finding at {@code elementPath} when it contains a disallowed character.
     *
     * <p>Delegates membership to {@link FinXCharset#isValidExtended(String)}, which returns {@code true} for
     * {@code null} or empty input; absent values therefore never produce a finding, and this method never
     * throws.
     *
     * @param value the candidate string; may be {@code null}
     * @param elementPath the human-readable element path recorded on any finding
     * @param findings the accumulator to which a finding is appended on violation
     */
    private void checkText(String value, String elementPath, List<Finding> findings) {
        if (!FinXCharset.isValidExtended(value)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, elementPath, MESSAGE));
        }
    }
}
