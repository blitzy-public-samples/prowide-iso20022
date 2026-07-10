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
import com.prowidesoftware.swift.cbpr.PostalAddressClassifier;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.ClearingSystemMemberIdentification2;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <b>{@code fi-party-bicfi-preferred}</b> for
 * <b>pacs.009.001.08</b> (Financial Institution Credit Transfer, {@link MxPacs00900108}).
 *
 * <p><b>Severity: {@link Severity#WARNING}.</b> This rule encodes a CBPR+ <em>preference</em>, not a
 * hard requirement. The authoritative rule inventory states: &ldquo;Identify by BICFI (optionally
 * +LEI) preferred; alternative is (Clearing Code OR LEI) + Name + structured/hybrid address.&rdquo;
 * Because it is a soft recommendation, every {@link Finding} it produces carries
 * {@link Severity#WARNING} and therefore <strong>never</strong> flips {@code ValidationResult.valid}
 * to {@code false}: message validity is governed solely by {@code ERROR}-severity rules. A pacs.009
 * message whose only imperfection is a non-BICFI financial-institution identification remains
 * {@code valid}.
 *
 * <h2>What is checked</h2>
 *
 * <p>In pacs.009 the debtor, creditor and their ultimate counterparts are themselves financial
 * institutions, so every party and agent occurrence is a
 * {@link BranchAndFinancialInstitutionIdentification6}. For each such occurrence that is present, the
 * rule inspects the {@link FinancialInstitutionIdentification18} obtained via
 * {@link BranchAndFinancialInstitutionIdentification6#getFinInstnId()}:
 *
 * <ul>
 *   <li><b>Preferred (no finding).</b> When a {@code BICFI} value is present
 *       (see {@link FinancialInstitutionIdentification18#getBICFI()}), the institution uses the
 *       preferred CBPR+ identification (BICFI, optionally accompanied by an LEI). Nothing is
 *       reported. Note that &ldquo;preferred identification present&rdquo; means a BICFI
 *       <em>value</em> is present; the ISO&nbsp;9362 <em>format</em> of that BICFI is a separate
 *       concern enforced (as an {@code ERROR}) by {@code BicfiFormatRule}, so this rule does not
 *       reject a malformed BICFI &mdash; it merely treats any present BICFI as satisfying the
 *       preference.</li>
 *   <li><b>Not preferred (one WARNING).</b> When no {@code BICFI} value is present, a single
 *       {@link Severity#WARNING} finding is raised for that occurrence.</li>
 * </ul>
 *
 * <p>The human-readable message of the warning is enriched (but never escalated) by inspecting the
 * documented acceptable alternative &mdash; {@code (Clearing Code OR LEI) + Name + structured/hybrid
 * address}. When that alternative is fully satisfied the message says so; otherwise it notes the
 * alternative is incomplete. This enrichment is purely informational: even an incomplete alternative
 * remains a {@code WARNING} here, because the hard structured-address requirement is enforced
 * independently by {@code StructuredAddressRule}.
 *
 * <h2>Navigation (typed getters, read-only)</h2>
 *
 * <p>Following algorithm A1, the message object graph is traversed with typed getter chains only
 * (no {@code MxNode}), consuming the generated model strictly read-only:
 * {@link MxPacs00900108#getFICdtTrf()} &rarr; {@link FinancialInstitutionCreditTransferV08} &rarr;
 * the group header agents ({@link GroupHeader93#getInstgAgt()} / {@link GroupHeader93#getInstdAgt()})
 * plus every transaction in {@link FinancialInstitutionCreditTransferV08#getCdtTrfTxInf()}
 * ({@link CreditTransferTransaction36}) with its FI parties ({@code UltmtDbtr}, {@code Dbtr},
 * {@code Cdtr}, {@code UltmtCdtr}) and agents ({@code InstgAgt}, {@code InstdAgt}, {@code DbtrAgt},
 * {@code CdtrAgt}, {@code IntrmyAgt1..3}).
 *
 * <h2>Resilience</h2>
 *
 * <p>Consistent with the module-wide contract, {@link #check(MxPacs00900108)} <strong>never
 * throws</strong>: every navigation hop is null-guarded and a {@code null} or empty message yields an
 * empty finding list. The method returns an empty list when every present financial institution is
 * identified by BICFI.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see PostalAddressClassifier
 * @since 10.3.14
 */
public final class FiPartyBicfiPreferredRule implements CbprRule<MxPacs00900108> {

    /** The CBPR+ rule identifier, taken verbatim from the authoritative rule inventory. */
    private static final String RULE_ID = "fi-party-bicfi-preferred";

    /** Root element name of the pacs.009.001.08 document, used to build human-readable element paths. */
    private static final String ROOT = "FICdtTrf";

    /** Base explanation shared by every warning this rule emits. */
    private static final String BASE_MESSAGE =
            "Preferred CBPR+ FI identification is BICFI (optionally +LEI); this financial institution is "
                    + "identified without a BICFI";

    /** Suffix appended when the documented acceptable alternative identification is fully present. */
    private static final String ALTERNATIVE_PRESENT_SUFFIX =
            " (acceptable alternative identification present: (ClrSysMmbId or LEI) + Name + structured/hybrid"
                    + " address)";

    /** Suffix appended when the documented acceptable alternative identification is incomplete. */
    private static final String ALTERNATIVE_INCOMPLETE_SUFFIX =
            " (and the acceptable alternative identification is incomplete)";

    /**
     * Evaluates the {@code fi-party-bicfi-preferred} preference across every financial-institution
     * party and agent occurrence of the supplied pacs.009.001.08 message.
     *
     * <p>Returns an empty (never {@code null}) list when every present institution is identified by a
     * BICFI, and one {@link Severity#WARNING} {@link Finding} per occurrence that is identified without
     * a BICFI. The method never throws: a {@code null} message, an absent {@code FICdtTrf}, absent
     * parties/agents and absent identification sub-elements all yield no finding for the missing
     * branch rather than an exception.
     *
     * @param message the parsed pacs.009.001.08 message to inspect; may be {@code null} or partial
     * @return the list of {@link Severity#WARNING} findings, empty when the preference is satisfied
     *     everywhere; never {@code null}
     */
    @Override
    public List<Finding> check(final MxPacs00900108 message) {
        final List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }

        final FinancialInstitutionCreditTransferV08 fiCdtTrf = message.getFICdtTrf();
        if (fiCdtTrf == null) {
            return findings;
        }

        // Group-header level agents (instructing / instructed).
        final GroupHeader93 grpHdr = fiCdtTrf.getGrpHdr();
        if (grpHdr != null) {
            evaluateFi(grpHdr.getInstgAgt(), ROOT + "/GrpHdr/InstgAgt", findings);
            evaluateFi(grpHdr.getInstdAgt(), ROOT + "/GrpHdr/InstdAgt", findings);
        }

        // Per-transaction financial-institution parties and agents. The list is lazily initialised by
        // the generated model (never null), but it is still guarded defensively.
        final List<CreditTransferTransaction36> transactions = fiCdtTrf.getCdtTrfTxInf();
        if (transactions != null) {
            int index = 1;
            for (final CreditTransferTransaction36 tx : transactions) {
                if (tx != null) {
                    final String txPath = ROOT + "/CdtTrfTxInf[" + index + "]";

                    // FI parties: in pacs.009 the debtor/creditor and their ultimate counterparts are
                    // themselves financial institutions.
                    evaluateFi(tx.getUltmtDbtr(), txPath + "/UltmtDbtr", findings);
                    evaluateFi(tx.getDbtr(), txPath + "/Dbtr", findings);
                    evaluateFi(tx.getCdtr(), txPath + "/Cdtr", findings);
                    evaluateFi(tx.getUltmtCdtr(), txPath + "/UltmtCdtr", findings);

                    // Agents on the settlement chain.
                    evaluateFi(tx.getInstgAgt(), txPath + "/InstgAgt", findings);
                    evaluateFi(tx.getInstdAgt(), txPath + "/InstdAgt", findings);
                    evaluateFi(tx.getDbtrAgt(), txPath + "/DbtrAgt", findings);
                    evaluateFi(tx.getCdtrAgt(), txPath + "/CdtrAgt", findings);
                    evaluateFi(tx.getIntrmyAgt1(), txPath + "/IntrmyAgt1", findings);
                    evaluateFi(tx.getIntrmyAgt2(), txPath + "/IntrmyAgt2", findings);
                    evaluateFi(tx.getIntrmyAgt3(), txPath + "/IntrmyAgt3", findings);
                }
                index++;
            }
        }

        return findings;
    }

    /**
     * Evaluates the BICFI preference for a single financial-institution occurrence and, when the
     * preferred identification is absent, appends a {@link Severity#WARNING} finding to {@code
     * findings}.
     *
     * <p>The evaluation is intentionally lenient about BICFI validity: a present (non-blank) BICFI
     * value satisfies the preference regardless of its ISO&nbsp;9362 format, since malformed BICFIs
     * are reported separately as an {@code ERROR} by {@code BicfiFormatRule}. When no BICFI value is
     * present the finding text is enriched by testing the documented acceptable alternative
     * &mdash; {@code (Clearing Code OR LEI) + Name + structured/hybrid address} &mdash; but the
     * severity always remains {@link Severity#WARNING} and is never escalated to {@code ERROR}.
     *
     * @param fiParty the financial-institution party/agent to evaluate; a {@code null} occurrence is a
     *     no-op
     * @param path the human-readable element path of this occurrence (the finding path appends {@code
     *     /FinInstnId})
     * @param findings the accumulator to which any warning is added
     */
    private void evaluateFi(
            final BranchAndFinancialInstitutionIdentification6 fiParty,
            final String path,
            final List<Finding> findings) {
        if (fiParty == null) {
            return;
        }

        final FinancialInstitutionIdentification18 id = fiParty.getFinInstnId();
        if (id == null) {
            return;
        }

        final boolean hasBicfi = StringUtils.isNotBlank(id.getBICFI());
        if (hasBicfi) {
            // Preferred CBPR+ identification present (BICFI, optionally +LEI): nothing to warn about.
            return;
        }

        // BICFI absent: not the preferred identification. Compute whether the documented acceptable
        // alternative is satisfied purely to enrich the human-readable message; this never affects
        // severity or validity.
        final ClearingSystemMemberIdentification2 clearing = id.getClrSysMmbId();
        final boolean hasClearing = clearing != null && StringUtils.isNotBlank(clearing.getMmbId());
        final boolean hasLei = StringUtils.isNotBlank(id.getLEI());
        final boolean hasName = StringUtils.isNotBlank(id.getNm());
        final PostalAddress24 address = id.getPstlAdr();
        final boolean addressOk = address != null && PostalAddressClassifier.isCompliant(address);

        final String message;
        if ((hasClearing || hasLei) && hasName && addressOk) {
            message = BASE_MESSAGE + ALTERNATIVE_PRESENT_SUFFIX;
        } else {
            message = BASE_MESSAGE + ALTERNATIVE_INCOMPLETE_SUFFIX;
        }

        findings.add(new Finding(RULE_ID, Severity.WARNING, path + "/FinInstnId", message));
    }
}
