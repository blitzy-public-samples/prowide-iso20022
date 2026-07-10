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

import com.prowidesoftware.swift.cbpr.BicValidator;
import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule {@code agent-national-clearing-code-only} for
 * pacs.009.001.08 ({@link MxPacs00900108}, Financial Institution Credit Transfer).
 *
 * <p>Rule text from the authoritative CBPR+ inventory: <em>"If all agents share one country,
 * clearing-code-only may be used"</em>, classified as a <strong>warning</strong>.
 *
 * <h2>Nature of the rule &mdash; permissive and informational</h2>
 *
 * <p>Unlike the ERROR-severity constraints in this message type, this is a <strong>permissive,
 * informational note rather than a prohibition</strong>. It never renders a message invalid. It
 * fires precisely when the CBPR+ allowance is being exercised: when every agent in the message
 * resolves to a single shared country <em>and</em> at least one agent is identified by a national
 * clearing-system member code only (no BICFI). In that domestic-leg scenario, CBPR+ permits agents
 * to be identified by their national clearing code alone, and this rule surfaces a single
 * {@link Severity#WARNING} finding to record that the permission is in effect.
 *
 * <p>Because the finding is a {@code WARNING}, it does <em>not</em> flip
 * {@code ValidationResult.valid}: a message that carries only warnings remains valid. When the
 * permissive condition does not hold &mdash; the agents span more than one country, no agent uses
 * clearing-code-only identification, or a common country cannot be determined &mdash; the rule
 * reports nothing and returns an empty list. This rule never produces an {@code ERROR} and never
 * fails a message.
 *
 * <h2>Agent scope and country derivation</h2>
 *
 * <p>Only <strong>agents</strong> (financial institutions) participate in this rule; debtor,
 * creditor and ultimate parties are ignored. The rule collects, skipping absent branches, the
 * group-header instructing and instructed agents and, from every transaction, the instructing,
 * instructed, debtor, creditor, three intermediary and three previous-instructing agents &mdash; all
 * of type {@link BranchAndFinancialInstitutionIdentification6}.
 *
 * <p>For each collected agent a country is derived, preferring the BICFI and falling back to the
 * postal address:
 *
 * <ul>
 *   <li>if {@link FinancialInstitutionIdentification18#getBICFI() BICFI} is a well-formed ISO 9362
 *       code (validated via {@link BicValidator#isValid(String)}), the ISO 3166-1 country code is
 *       read from its positions 5&ndash;6 (zero-based indices {@code [4, 6)});
 *   <li>otherwise, if a {@link PostalAddress24 postal address} with a non-blank
 *       {@link PostalAddress24#getCtry() country} is present, that country is used;
 *   <li>otherwise the agent contributes no country (it is simply not counted).
 * </ul>
 *
 * <p>An agent is considered <em>clearing-code-only</em> when it carries a
 * {@link ClearingSystemMemberIdentification2 clearing-system member id} with a non-blank
 * {@link ClearingSystemMemberIdentification2#getMmbId() member id} while its BICFI is blank.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>Consistent with the {@link CbprRule} contract, this implementation <strong>never throws</strong>:
 * every navigation hop is null-guarded and the BICFI {@code substring} is length-guarded, so a sparse
 * or partial message yields an empty result rather than an exception. It emits <strong>at most one</strong>
 * finding, always {@code WARNING} severity, anchored at element path {@code FICdtTrf}.
 *
 * <p>The class is stateless and holds no reference to the validated message; a single shared instance is
 * safe for concurrent use. A public no-argument constructor is provided so the validator can instantiate
 * it reflection-free via {@code new AgentNationalClearingCodeOnlyRule()}.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see BicValidator
 * @since SRU2026
 */
public class AgentNationalClearingCodeOnlyRule implements CbprRule<MxPacs00900108> {

    /** The CBPR+ rule identifier, preserved verbatim from the authoritative inventory. */
    private static final String RULE_ID = "agent-national-clearing-code-only";

    /** Human-readable element path anchoring the informational finding at the message root branch. */
    private static final String ELEMENT_PATH = "FICdtTrf";

    /** Zero-based start index of the ISO 3166-1 country code within an ISO 9362 BIC (position 5). */
    private static final int BIC_COUNTRY_BEGIN_INDEX = 4;

    /** Zero-based end index (exclusive) of the ISO 3166-1 country code within an ISO 9362 BIC (position 6). */
    private static final int BIC_COUNTRY_END_INDEX = 6;

    /**
     * Evaluates the permissive {@code agent-national-clearing-code-only} rule against the supplied
     * pacs.009.001.08 message.
     *
     * <p>Collects every agent occurrence, derives the set of countries they resolve to and detects
     * whether any agent is identified by a national clearing code only. When (and only when) all
     * resolvable agents share exactly one country and at least one agent is clearing-code-only, a
     * single informational {@link Severity#WARNING} finding is returned; in every other case the
     * result is empty. The method never throws and never returns {@code null}.
     *
     * @param message the parsed pacs.009.001.08 message; may be {@code null} or partial
     * @return a list containing a single {@code WARNING} finding when the CBPR+ clearing-code-only
     *     allowance applies, or an empty list otherwise; never {@code null}
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

        List<BranchAndFinancialInstitutionIdentification6> agents = collectAgents(fiCdtTrf);
        if (agents.isEmpty()) {
            return findings;
        }

        Set<String> countries = new HashSet<>();
        for (BranchAndFinancialInstitutionIdentification6 agent : agents) {
            String country = countryOf(agent);
            if (country != null) {
                countries.add(country);
            }
        }

        boolean anyClearingOnly = agents.stream().anyMatch(this::isClearingCodeOnly);

        if (countries.size() == 1 && anyClearingOnly) {
            String country = countries.iterator().next();
            findings.add(new Finding(
                    RULE_ID,
                    Severity.WARNING,
                    ELEMENT_PATH,
                    "All agents share a single country (" + country
                            + "); national clearing-code-only agent identification is permitted under CBPR+ "
                            + "for this domestic-leg scenario"));
        }
        return findings;
    }

    /**
     * Collects every agent participating in the message: the group-header instructing/instructed
     * agents plus, for each transaction, the instructing, instructed, debtor, creditor, three
     * intermediary and three previous-instructing agents. Absent ({@code null}) branches and
     * {@code null} transactions are skipped.
     *
     * @param fiCdtTrf the financial institution credit transfer body; never {@code null} here
     * @return the ordered list of present agents, possibly empty; never {@code null}
     */
    private List<BranchAndFinancialInstitutionIdentification6> collectAgents(
            FinancialInstitutionCreditTransferV08 fiCdtTrf) {
        List<BranchAndFinancialInstitutionIdentification6> agents = new ArrayList<>();

        GroupHeader93 grpHdr = fiCdtTrf.getGrpHdr();
        if (grpHdr != null) {
            addIfPresent(agents, grpHdr.getInstgAgt());
            addIfPresent(agents, grpHdr.getInstdAgt());
        }

        List<CreditTransferTransaction36> transactions = fiCdtTrf.getCdtTrfTxInf();
        if (transactions != null) {
            for (CreditTransferTransaction36 tx : transactions) {
                if (tx == null) {
                    continue;
                }
                addIfPresent(agents, tx.getInstgAgt());
                addIfPresent(agents, tx.getInstdAgt());
                addIfPresent(agents, tx.getDbtrAgt());
                addIfPresent(agents, tx.getCdtrAgt());
                addIfPresent(agents, tx.getIntrmyAgt1());
                addIfPresent(agents, tx.getIntrmyAgt2());
                addIfPresent(agents, tx.getIntrmyAgt3());
                addIfPresent(agents, tx.getPrvsInstgAgt1());
                addIfPresent(agents, tx.getPrvsInstgAgt2());
                addIfPresent(agents, tx.getPrvsInstgAgt3());
            }
        }
        return agents;
    }

    /**
     * Adds the given agent to the accumulator when it is present ({@code non-null}).
     *
     * @param agents the accumulator collecting present agents
     * @param agent the candidate agent; ignored when {@code null}
     */
    private void addIfPresent(
            List<BranchAndFinancialInstitutionIdentification6> agents,
            BranchAndFinancialInstitutionIdentification6 agent) {
        if (agent != null) {
            agents.add(agent);
        }
    }

    /**
     * Derives the ISO 3166-1 country of an agent, preferring the BICFI over the postal address.
     *
     * <p>When the agent's {@link FinancialInstitutionIdentification18#getBICFI() BICFI} is a
     * well-formed ISO 9362 code the country is taken from positions 5&ndash;6 (zero-based indices
     * {@code [4, 6)}); {@link BicValidator#isValid(String)} already guarantees a length of 8 or 11, and
     * the {@code substring} bounds are additionally guarded defensively. Otherwise the non-blank
     * {@link PostalAddress24#getCtry() postal-address country} is used, if any.
     *
     * @param agent the agent to inspect; may be {@code null}
     * @return the two-letter country code, or {@code null} when neither source is available
     */
    private String countryOf(BranchAndFinancialInstitutionIdentification6 agent) {
        if (agent == null) {
            return null;
        }
        FinancialInstitutionIdentification18 id = agent.getFinInstnId();
        if (id == null) {
            return null;
        }

        String bic = id.getBICFI();
        if (BicValidator.isValid(bic) && bic.length() >= BIC_COUNTRY_END_INDEX) {
            return bic.substring(BIC_COUNTRY_BEGIN_INDEX, BIC_COUNTRY_END_INDEX);
        }

        PostalAddress24 pstlAdr = id.getPstlAdr();
        if (pstlAdr != null && StringUtils.isNotBlank(pstlAdr.getCtry())) {
            return pstlAdr.getCtry();
        }
        return null;
    }

    /**
     * Determines whether an agent is identified by a national clearing-system member code only.
     *
     * <p>An agent qualifies when it carries a {@link ClearingSystemMemberIdentification2} with a
     * non-blank {@link ClearingSystemMemberIdentification2#getMmbId() member id} while its
     * {@link FinancialInstitutionIdentification18#getBICFI() BICFI} is blank.
     *
     * @param agent the agent to inspect; may be {@code null}
     * @return {@code true} when the agent is clearing-code-only, {@code false} otherwise
     */
    private boolean isClearingCodeOnly(BranchAndFinancialInstitutionIdentification6 agent) {
        if (agent == null) {
            return false;
        }
        FinancialInstitutionIdentification18 id = agent.getFinInstnId();
        if (id == null) {
            return false;
        }
        if (StringUtils.isNotBlank(id.getBICFI())) {
            return false;
        }
        ClearingSystemMemberIdentification2 clrSysMmbId = id.getClrSysMmbId();
        return clrSysMmbId != null && StringUtils.isNotBlank(clrSysMmbId.getMmbId());
    }
}
