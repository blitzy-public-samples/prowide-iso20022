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
import com.prowidesoftware.swift.cbpr.FinXCharset;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule {@code charset-finx-extended-for-name-address} for
 * <strong>pacs.009.001.08</strong> (Financial Institution Credit Transfer, {@link MxPacs00900108}).
 *
 * <p>This is one rule application from the authoritative CBPR+ rule inventory (AAP &sect;0.1.1, row:
 * <em>pacs.009.001.08 / charset-finx-extended-for-name-address / error / "FIN-X extended charset"</em>).
 * As mandated, exactly one rule is implemented by this class, exposing a single public
 * {@link #check(MxPacs00900108) check} method; there is no runtime rules-interpreter, no configuration
 * DSL and no reflection.
 *
 * <h2>What this rule enforces</h2>
 *
 * <p>Every free-text <em>Name</em> and <em>postal-address</em> string carried by a financial
 * institution party or agent must be composed solely of characters belonging to the CBPR+ FIN-X
 * <em>extended</em> character set &mdash; the base FIN-X ("X") set plus the additional punctuation
 * permitted for Name / Address / Remittance fields. A string that contains any character outside that
 * set is a non-compliant condition and produces one {@link Severity#ERROR ERROR}-severity
 * {@link Finding} pointing at the offending element.
 *
 * <p>The character-set membership decision is delegated <strong>entirely</strong> to the shared
 * {@link FinXCharset#isValidExtended(String)} predicate (reused by the pacs.008, pacs.009 and pain.001
 * charset rules); this class defines no character set or regular expression of its own. Because
 * {@link FinXCharset#isValidExtended(String)} treats a {@code null} or empty value as valid (absence is
 * not a character-set violation), this rule never flags missing fields &mdash; presence and
 * mandatory-ness are the concern of other CBPR+ rules, keeping the charset check orthogonal.
 *
 * <h2>Scope of inspection</h2>
 *
 * <p>The rule scans the same financial-institution party and agent occurrences as the pacs.009
 * structured-address rule, and for each one inspects the textual {@code Nm} plus every free-text
 * component of the postal address rather than their structural completeness:
 *
 * <ul>
 *   <li>group-header agents &mdash; {@code InstgAgt}, {@code InstdAgt};
 *   <li>per credit-transfer-transaction financial-institution parties &mdash; {@code UltmtDbtr},
 *       {@code Dbtr}, {@code Cdtr}, {@code UltmtCdtr};
 *   <li>per credit-transfer-transaction agents &mdash; {@code InstgAgt}, {@code InstdAgt},
 *       {@code DbtrAgt}, {@code CdtrAgt}, {@code IntrmyAgt1}, {@code IntrmyAgt2}, {@code IntrmyAgt3},
 *       {@code PrvsInstgAgt1}, {@code PrvsInstgAgt2} and {@code PrvsInstgAgt3}.
 * </ul>
 *
 * <p>For every occurrence carrying a {@link PostalAddress24}, the rule sweeps the <strong>complete</strong>
 * set of address free-text components &mdash; matching the pacs.008 charset rule &mdash; namely
 * {@code Dept}, {@code SubDept}, {@code StrtNm}, {@code BldgNb}, {@code BldgNm}, {@code Flr},
 * {@code PstBx}, {@code Room}, {@code PstCd}, {@code TwnNm}, {@code TwnLctnNm}, {@code DstrctNm},
 * {@code CtrySubDvsn}, {@code Ctry} and every {@code AdrLine} entry, so that a disallowed character in
 * any address field is detected rather than only the town/country/address-line subset.
 *
 * <h2>Resilience contract</h2>
 *
 * <p>Consistent with {@link CbprRule}, {@link #check(MxPacs00900108) check} <strong>never
 * throws</strong>: every navigation hop is null-guarded, so a sparse or partial message simply yields
 * fewer inspected strings. When every present Name/Address string is within the extended set, the
 * method returns an empty list. The generated model is consumed strictly read-only through typed
 * getters.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see FinXCharset
 * @see MxPacs00900108
 * @since 10.3.14
 */
public class CharsetFinxExtendedRule implements CbprRule<MxPacs00900108> {

    /**
     * The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory. Emitted on every
     * {@link Finding} this rule produces.
     */
    private static final String RULE_ID = "charset-finx-extended-for-name-address";

    /** Human-readable explanation attached to every finding this rule produces. */
    private static final String MESSAGE_TEXT =
            "Value contains characters outside the permitted FIN-X extended character set for Name/Address fields";

    /**
     * Evaluates the {@code charset-finx-extended-for-name-address} rule against the given
     * pacs.009.001.08 message.
     *
     * <p>Walks the group-header agents and every credit-transfer transaction's financial-institution
     * parties and agents, testing each present Name and postal-address free-text string against the
     * FIN-X extended character set via {@link FinXCharset#isValidExtended(String)}. One
     * {@link Severity#ERROR} {@link Finding} is added per offending string, each carrying a distinct
     * element path.
     *
     * <p>This method never throws: a {@code null} message, a {@code null} {@code FICdtTrf}, or any
     * absent party/agent/sub-element simply contributes no findings.
     *
     * @param message the parsed pacs.009.001.08 message to validate; may be {@code null} or partial
     * @return the list of findings, empty when every present Name/Address string is within the FIN-X
     *     extended set; never {@code null}
     */
    @Override
    public List<Finding> check(MxPacs00900108 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }
        FinancialInstitutionCreditTransferV08 fiCdtTrf = message.getFICdtTrf();
        if (fiCdtTrf == null) {
            return findings;
        }

        // Group-header level agents.
        GroupHeader93 grpHdr = fiCdtTrf.getGrpHdr();
        if (grpHdr != null) {
            evaluateFi(grpHdr.getInstgAgt(), "FICdtTrf/GrpHdr/InstgAgt", findings);
            evaluateFi(grpHdr.getInstdAgt(), "FICdtTrf/GrpHdr/InstdAgt", findings);
        }

        // Per-transaction financial-institution parties and agents.
        List<CreditTransferTransaction36> transactions = fiCdtTrf.getCdtTrfTxInf();
        if (transactions != null) {
            for (int i = 0; i < transactions.size(); i++) {
                CreditTransferTransaction36 tx = transactions.get(i);
                if (tx == null) {
                    continue;
                }
                String base = "FICdtTrf/CdtTrfTxInf[" + i + "]";
                // Financial-institution parties.
                evaluateFi(tx.getUltmtDbtr(), base + "/UltmtDbtr", findings);
                evaluateFi(tx.getDbtr(), base + "/Dbtr", findings);
                evaluateFi(tx.getCdtr(), base + "/Cdtr", findings);
                evaluateFi(tx.getUltmtCdtr(), base + "/UltmtCdtr", findings);
                // Agents.
                evaluateFi(tx.getInstgAgt(), base + "/InstgAgt", findings);
                evaluateFi(tx.getInstdAgt(), base + "/InstdAgt", findings);
                evaluateFi(tx.getDbtrAgt(), base + "/DbtrAgt", findings);
                evaluateFi(tx.getCdtrAgt(), base + "/CdtrAgt", findings);
                evaluateFi(tx.getIntrmyAgt1(), base + "/IntrmyAgt1", findings);
                evaluateFi(tx.getIntrmyAgt2(), base + "/IntrmyAgt2", findings);
                evaluateFi(tx.getIntrmyAgt3(), base + "/IntrmyAgt3", findings);
                // Previous instructing agents are financial-institution agents too; their Name and
                // postal-address free-text must be swept for disallowed characters like every other agent.
                evaluateFi(tx.getPrvsInstgAgt1(), base + "/PrvsInstgAgt1", findings);
                evaluateFi(tx.getPrvsInstgAgt2(), base + "/PrvsInstgAgt2", findings);
                evaluateFi(tx.getPrvsInstgAgt3(), base + "/PrvsInstgAgt3", findings);
            }
        }

        return findings;
    }

    /**
     * Inspects a single financial-institution party/agent occurrence, testing its Name and postal
     * address free-text values against the FIN-X extended character set.
     *
     * <p>A {@code null} party, or a party without a {@code FinInstnId}, contributes no findings. When a
     * postal address is present, every free-text component of that address is checked (delegated to
     * {@link #checkAddress}) in addition to the party {@code Nm}.
     *
     * @param fiParty the financial-institution identification to inspect; may be {@code null}
     * @param path the element path prefix identifying this occurrence (for example
     *     {@code FICdtTrf/CdtTrfTxInf[0]/Dbtr}); the sub-element suffix is appended per checked value
     * @param findings the accumulator to which any findings are added
     */
    private void evaluateFi(
            BranchAndFinancialInstitutionIdentification6 fiParty, String path, List<Finding> findings) {
        if (fiParty == null) {
            return;
        }
        FinancialInstitutionIdentification18 id = fiParty.getFinInstnId();
        if (id == null) {
            return;
        }

        // Party name.
        checkString(id.getNm(), path + "/FinInstnId/Nm", findings);

        // Postal-address free-text values (complete component sweep).
        PostalAddress24 addr = id.getPstlAdr();
        if (addr != null) {
            checkAddress(addr, path + "/FinInstnId/PstlAdr", findings);
        }
    }

    /**
     * Sweeps every free-text component of a {@link PostalAddress24}, testing each present value against
     * the FIN-X extended character set.
     *
     * <p>All structured address string sub-elements ({@code Dept}, {@code SubDept}, {@code StrtNm},
     * {@code BldgNb}, {@code BldgNm}, {@code Flr}, {@code PstBx}, {@code Room}, {@code PstCd},
     * {@code TwnNm}, {@code TwnLctnNm}, {@code DstrctNm}, {@code CtrySubDvsn}, {@code Ctry}) plus every
     * {@code AdrLine} entry are inspected, mirroring the pacs.008 charset rule so a disallowed character
     * anywhere in the address is reported. {@code getAdrLine()} is a lazily-initialised, never-{@code
     * null} list and is iterated directly.
     *
     * @param addr the postal address to inspect; ignored when {@code null}
     * @param path the element path prefix for this address (for example
     *     {@code FICdtTrf/CdtTrfTxInf[0]/Dbtr/FinInstnId/PstlAdr})
     * @param findings the accumulator to which any findings are added
     */
    private void checkAddress(PostalAddress24 addr, String path, List<Finding> findings) {
        if (addr == null) {
            return;
        }
        checkString(addr.getDept(), path + "/Dept", findings);
        checkString(addr.getSubDept(), path + "/SubDept", findings);
        checkString(addr.getStrtNm(), path + "/StrtNm", findings);
        checkString(addr.getBldgNb(), path + "/BldgNb", findings);
        checkString(addr.getBldgNm(), path + "/BldgNm", findings);
        checkString(addr.getFlr(), path + "/Flr", findings);
        checkString(addr.getPstBx(), path + "/PstBx", findings);
        checkString(addr.getRoom(), path + "/Room", findings);
        checkString(addr.getPstCd(), path + "/PstCd", findings);
        checkString(addr.getTwnNm(), path + "/TwnNm", findings);
        checkString(addr.getTwnLctnNm(), path + "/TwnLctnNm", findings);
        checkString(addr.getDstrctNm(), path + "/DstrctNm", findings);
        checkString(addr.getCtrySubDvsn(), path + "/CtrySubDvsn", findings);
        checkString(addr.getCtry(), path + "/Ctry", findings);
        List<String> adrLines = addr.getAdrLine();
        for (int j = 0; j < adrLines.size(); j++) {
            checkString(adrLines.get(j), path + "/AdrLine[" + j + "]", findings);
        }
    }

    /**
     * Tests one Name/Address string against the FIN-X extended character set and, when it contains a
     * disallowed character, appends an {@link Severity#ERROR} finding at the given path.
     *
     * <p>A {@code null} value is never flagged: absence is not a character-set violation (and
     * {@link FinXCharset#isValidExtended(String)} itself returns {@code true} for {@code null}/empty).
     * The explicit {@code null} check simply avoids constructing a needless finding.
     *
     * @param value the Name/Address string to test; may be {@code null}
     * @param path the element path of the value under test
     * @param findings the accumulator to which a finding is added when the value is non-compliant
     */
    private void checkString(String value, String path, List<Finding> findings) {
        if (value != null && !FinXCharset.isValidExtended(value)) {
            findings.add(new Finding(RULE_ID, Severity.ERROR, path, MESSAGE_TEXT));
        }
    }
}
