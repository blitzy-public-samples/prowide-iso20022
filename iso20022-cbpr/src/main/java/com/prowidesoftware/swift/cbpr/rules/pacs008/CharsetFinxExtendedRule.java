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
import com.prowidesoftware.swift.cbpr.FinXCharset;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.Contact4;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import com.prowidesoftware.swift.model.mx.dic.RemittanceInformation16;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code charset-finx-extended-for-name-address}</strong>
 * for <strong>pacs.008.001.08</strong> (FI-to-FI Customer Credit Transfer).
 *
 * <p>The requirement is preserved verbatim from the authoritative CBPR+ rule inventory (severity
 * {@link Severity#ERROR}):
 *
 * <blockquote><em>"FIN-X charset; Name/Address/Remittance/proxy-email additionally permit an extended
 * punctuation set."</em></blockquote>
 *
 * <p>Every free-text business string on a pacs.008 message must be expressed in the SWIFT FIN-X ("X")
 * character set. Four families of fields &mdash; party/agent <em>Name</em>, postal <em>Address</em>
 * components, unstructured <em>Remittance</em> lines and the contact <em>proxy-email</em> &mdash; are
 * additionally allowed an extended punctuation set. This rule verifies that <strong>every</strong> such
 * value contains only characters drawn from that base-plus-extended set; any character outside it (for
 * example an accented letter such as {@code é}, which is not part of FIN-X) is a violation.
 *
 * <h2>Fields inspected</h2>
 *
 * <p>The rule sweeps the whole pacs.008 object graph, applying the extended-charset predicate to every
 * reachable Name / Address / Remittance / proxy-email string, namely:
 *
 * <ul>
 *   <li><strong>Parties</strong> &mdash; {@code Dbtr}, {@code Cdtr}, {@code UltmtDbtr}, {@code UltmtCdtr}
 *       and {@code InitgPty} on each {@code CdtTrfTxInf}: the party {@code Nm}, the contact
 *       {@code CtctDtls/EmailAdr} (the reachable proxy-email analog) and every component of the party
 *       {@code PstlAdr};
 *   <li><strong>Agents</strong> &mdash; {@code InstgAgt}, {@code InstdAgt}, {@code DbtrAgt},
 *       {@code CdtrAgt}, {@code IntrmyAgt1..3} and {@code PrvsInstgAgt1..3} on each {@code CdtTrfTxInf},
 *       plus the group-header {@code InstgAgt}/{@code InstdAgt}: the financial-institution {@code Nm} and
 *       every component of its {@code PstlAdr};
 *   <li><strong>Remittance</strong> &mdash; each unstructured line of {@code CdtTrfTxInf/RmtInf/Ustrd}.
 * </ul>
 *
 * <p>Postal-address components covered are {@code Dept}, {@code SubDept}, {@code StrtNm}, {@code BldgNb},
 * {@code BldgNm}, {@code Flr}, {@code PstBx}, {@code Room}, {@code PstCd}, {@code TwnNm}, {@code TwnLctnNm},
 * {@code DstrctNm}, {@code CtrySubDvsn}, {@code Ctry} and every {@code AdrLine} entry.
 *
 * <h2>Design notes</h2>
 *
 * <ul>
 *   <li><strong>Single source of truth for the character set.</strong> The base-plus-extended membership
 *       test is delegated entirely to {@link FinXCharset#isValidExtended(String)}; this rule never
 *       re-declares the character set. Because that predicate treats {@code null} and empty input as
 *       valid (an absent value is a presence concern enforced by other rules, not a charset violation),
 *       this rule needs no explicit "field present?" gate: every candidate string is simply passed
 *       through the predicate.
 *   <li><strong>Read-only, typed navigation.</strong> The message object graph is traversed exclusively
 *       through generated typed getters (approach A1); no setters, reflection or {@code MxNode} path
 *       navigation is used, and no generated model is modified.
 *   <li><strong>Never throws.</strong> Per the {@link CbprRule} contract, every navigation hop is
 *       null-guarded and the two list-valued fields ({@code AdrLine}, {@code Ustrd}) are lazily
 *       initialized non-null lists that are iterated directly. A sparse or partial (for example migrated)
 *       message therefore yields findings or a clean pass, never an exception.
 *   <li><strong>Collect all findings.</strong> One {@link Finding} is emitted per offending value; the
 *       rule reports every violation it detects rather than stopping at the first.
 * </ul>
 *
 * <p><strong>Proxy-email gap note.</strong> The CBPR+ inventory names "proxy-email" but does not pin a
 * single element path. {@link Contact4#getEmailAdr()}, reached via {@code PartyIdentification135}, is the
 * faithful reachable email field and is the one exercised here. Should the definitive CBPR+ proxy-email
 * element prove to reside elsewhere, that is recorded as a documented gap under {@code docs/} rather than
 * addressed by inventing a new element path or a new rule.
 *
 * <p>The rule is stateless and therefore safe to reuse across threads; instances are created through the
 * implicit no-argument constructor.
 *
 * @see FinXCharset#isValidExtended(String)
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @since SRU2026
 */
public final class CharsetFinxExtendedRule implements CbprRule<MxPacs00800108> {

    /**
     * The CBPR+ rule identifier, in kebab-case, taken verbatim from the authoritative rule inventory.
     */
    private static final String RULE_ID = "charset-finx-extended-for-name-address";

    /**
     * The human-readable explanation attached to every finding this rule emits.
     */
    private static final String MESSAGE =
            "Value contains characters outside the FIN-X extended set permitted for Name/Address/Remittance.";

    /**
     * Evaluates the {@code charset-finx-extended-for-name-address} rule against the supplied
     * pacs.008.001.08 message.
     *
     * <p>Walks every party, agent and remittance surface of the message and validates each free-text
     * Name / Address / Remittance / proxy-email value against the FIN-X extended character set. A
     * {@link Finding} of severity {@link Severity#ERROR} is added for every value that contains a
     * character outside that set.
     *
     * <p>The method never throws and never returns {@code null}: an empty list means the message complies,
     * while every element of a non-empty list describes a distinct offending value. A {@code null} message
     * or a {@code null} document root yields an empty list.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partial
     * @return the list of charset findings, empty when every inspected value is FIN-X-extended compliant
     */
    @Override
    public List<Finding> check(MxPacs00800108 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }
        FIToFICustomerCreditTransferV08 root = message.getFIToFICstmrCdtTrf();
        if (root == null) {
            return findings;
        }

        // Group-header agents (InstgAgt / InstdAgt) also carry free-text Name and Address; sweep once.
        GroupHeader93 groupHeader = root.getGrpHdr();
        if (groupHeader != null) {
            checkAgent(groupHeader.getInstgAgt(), "FIToFICstmrCdtTrf/GrpHdr/InstgAgt", findings);
            checkAgent(groupHeader.getInstdAgt(), "FIToFICstmrCdtTrf/GrpHdr/InstdAgt", findings);
        }

        List<CreditTransferTransaction39> transactions = root.getCdtTrfTxInf();
        if (transactions == null) {
            return findings;
        }
        for (int i = 0; i < transactions.size(); i++) {
            CreditTransferTransaction39 tx = transactions.get(i);
            if (tx == null) {
                continue;
            }
            String base = "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]";

            // Parties: Name, contact proxy-email and postal address.
            checkParty(tx.getDbtr(), base + "/Dbtr", findings);
            checkParty(tx.getCdtr(), base + "/Cdtr", findings);
            checkParty(tx.getUltmtDbtr(), base + "/UltmtDbtr", findings);
            checkParty(tx.getUltmtCdtr(), base + "/UltmtCdtr", findings);
            checkParty(tx.getInitgPty(), base + "/InitgPty", findings);

            // Agents: financial-institution Name and postal address.
            checkAgent(tx.getInstgAgt(), base + "/InstgAgt", findings);
            checkAgent(tx.getInstdAgt(), base + "/InstdAgt", findings);
            checkAgent(tx.getDbtrAgt(), base + "/DbtrAgt", findings);
            checkAgent(tx.getCdtrAgt(), base + "/CdtrAgt", findings);
            checkAgent(tx.getIntrmyAgt1(), base + "/IntrmyAgt1", findings);
            checkAgent(tx.getIntrmyAgt2(), base + "/IntrmyAgt2", findings);
            checkAgent(tx.getIntrmyAgt3(), base + "/IntrmyAgt3", findings);
            checkAgent(tx.getPrvsInstgAgt1(), base + "/PrvsInstgAgt1", findings);
            checkAgent(tx.getPrvsInstgAgt2(), base + "/PrvsInstgAgt2", findings);
            checkAgent(tx.getPrvsInstgAgt3(), base + "/PrvsInstgAgt3", findings);

            // Remittance: unstructured lines.
            checkRemittance(tx.getRmtInf(), base + "/RmtInf", findings);
        }
        return findings;
    }

    /**
     * Validates a single string value against the FIN-X extended character set, adding an
     * {@link Severity#ERROR} finding at {@code path} when it contains a disallowed character.
     *
     * <p>Delegates membership to {@link FinXCharset#isValidExtended(String)}, which returns {@code true}
     * for {@code null} or empty input; absent values therefore never produce a finding.
     *
     * @param value the candidate string; may be {@code null}
     * @param path the human-readable element path recorded on any finding
     * @param findings the accumulator to which a finding is appended on violation
     */
    private void checkValue(String value, String path, List<Finding> findings) {
        if (!FinXCharset.isValidExtended(value)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, path, MESSAGE));
        }
    }

    /**
     * Sweeps a party's free-text surfaces: its {@code Nm}, its contact {@code EmailAdr} (proxy-email
     * analog) and every component of its {@code PstlAdr}.
     *
     * @param party the party to inspect; ignored when {@code null}
     * @param path the element path prefix for this party (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/Dbtr})
     * @param findings the accumulator for any findings produced
     */
    private void checkParty(PartyIdentification135 party, String path, List<Finding> findings) {
        if (party == null) {
            return;
        }
        checkValue(party.getNm(), path + "/Nm", findings);
        Contact4 contact = party.getCtctDtls();
        if (contact != null) {
            checkValue(contact.getEmailAdr(), path + "/CtctDtls/EmailAdr", findings);
        }
        PostalAddress24 address = party.getPstlAdr();
        if (address != null) {
            checkAddress(address, path + "/PstlAdr", findings);
        }
    }

    /**
     * Sweeps an agent's free-text surfaces: the financial-institution {@code Nm} and every component of
     * its {@code PstlAdr}. Agents identified by BICFI only (no name or address) yield no findings.
     *
     * @param agent the agent to inspect; ignored when {@code null} or when it carries no
     *     {@code FinInstnId}
     * @param path the element path prefix for this agent (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/DbtrAgt})
     * @param findings the accumulator for any findings produced
     */
    private void checkAgent(BranchAndFinancialInstitutionIdentification6 agent, String path, List<Finding> findings) {
        if (agent == null) {
            return;
        }
        FinancialInstitutionIdentification18 fi = agent.getFinInstnId();
        if (fi == null) {
            return;
        }
        checkValue(fi.getNm(), path + "/FinInstnId/Nm", findings);
        PostalAddress24 address = fi.getPstlAdr();
        if (address != null) {
            checkAddress(address, path + "/FinInstnId/PstlAdr", findings);
        }
    }

    /**
     * Sweeps every component of a {@link PostalAddress24}: each structured sub-element and each entry of
     * the {@code AdrLine} list.
     *
     * <p>{@code getAdrLine()} is a lazily-initialized, never-{@code null} list and is iterated directly.
     *
     * @param address the postal address to inspect; ignored when {@code null}
     * @param path the element path prefix for this address (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/Dbtr/PstlAdr})
     * @param findings the accumulator for any findings produced
     */
    private void checkAddress(PostalAddress24 address, String path, List<Finding> findings) {
        if (address == null) {
            return;
        }
        checkValue(address.getDept(), path + "/Dept", findings);
        checkValue(address.getSubDept(), path + "/SubDept", findings);
        checkValue(address.getStrtNm(), path + "/StrtNm", findings);
        checkValue(address.getBldgNb(), path + "/BldgNb", findings);
        checkValue(address.getBldgNm(), path + "/BldgNm", findings);
        checkValue(address.getFlr(), path + "/Flr", findings);
        checkValue(address.getPstBx(), path + "/PstBx", findings);
        checkValue(address.getRoom(), path + "/Room", findings);
        checkValue(address.getPstCd(), path + "/PstCd", findings);
        checkValue(address.getTwnNm(), path + "/TwnNm", findings);
        checkValue(address.getTwnLctnNm(), path + "/TwnLctnNm", findings);
        checkValue(address.getDstrctNm(), path + "/DstrctNm", findings);
        checkValue(address.getCtrySubDvsn(), path + "/CtrySubDvsn", findings);
        checkValue(address.getCtry(), path + "/Ctry", findings);
        for (String line : address.getAdrLine()) {
            checkValue(line, path + "/AdrLine", findings);
        }
    }

    /**
     * Sweeps the unstructured remittance lines of a {@link RemittanceInformation16}.
     *
     * <p>{@code getUstrd()} is a lazily-initialized, never-{@code null} list and is iterated directly.
     *
     * @param remittance the remittance information to inspect; ignored when {@code null}
     * @param path the element path prefix for this remittance block (for example
     *     {@code FIToFICstmrCdtTrf/CdtTrfTxInf[0]/RmtInf})
     * @param findings the accumulator for any findings produced
     */
    private void checkRemittance(RemittanceInformation16 remittance, String path, List<Finding> findings) {
        if (remittance == null) {
            return;
        }
        for (String line : remittance.getUstrd()) {
            checkValue(line, path + "/Ustrd", findings);
        }
    }
}
