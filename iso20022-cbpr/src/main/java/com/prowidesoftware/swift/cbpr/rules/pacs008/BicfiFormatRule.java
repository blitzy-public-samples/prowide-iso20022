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

import com.prowidesoftware.swift.cbpr.BicValidator;
import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader93;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code bicfi-format}</strong> for
 * <em>pacs.008.001.08</em> (FI&nbsp;to&nbsp;FI Customer Credit Transfer).
 *
 * <p><strong>Rule (verbatim from the authoritative CBPR+ inventory):</strong> <em>"All agents'
 * FinInstnId/BICFI must match ISO 9362 (8 or 11 chars)."</em> &mdash; severity {@link Severity#ERROR}.
 *
 * <p>This rule inspects every agent that a pacs.008.001.08 message can carry and, whenever an agent
 * presents a BICFI value, verifies that the value conforms to the ISO 9362 Business Identifier Code
 * format. The complete set of agents examined is:
 *
 * <ul>
 *   <li>the two group-header agents &mdash; {@code GrpHdr/InstgAgt} and {@code GrpHdr/InstdAgt};
 *   <li>the ten transaction agents on <em>every</em> {@code CdtTrfTxInf} occurrence &mdash;
 *       {@code InstgAgt}, {@code InstdAgt}, {@code DbtrAgt}, {@code CdtrAgt},
 *       {@code IntrmyAgt1}&ndash;{@code IntrmyAgt3} and
 *       {@code PrvsInstgAgt1}&ndash;{@code PrvsInstgAgt3}.
 * </ul>
 *
 * <p><strong>Format-only, not presence.</strong> The rule raises a {@link Finding} only when a BICFI
 * is actually present (non-blank) <em>and</em> malformed. An absent or blank BICFI is deliberately
 * <em>not</em> reported here: mandatory presence of the point-to-point {@code InstgAgt}/{@code InstdAgt}
 * BICFI is enforced by the separate {@code AgentPointToPointBicfiMandatoryRule}, and other agents may
 * legitimately be identified by a national clearing code or other means. Keeping the two
 * responsibilities cleanly separated prevents the same agent from being flagged twice.
 *
 * <p>The actual ISO 9362 pattern match is delegated to {@link BicValidator#isValid(String)} &mdash;
 * which anchors the match with {@link java.util.regex.Matcher#matches()} so that only whole-string
 * values of length exactly 8 or 11 with the correct character classes are accepted &mdash; and the
 * pattern is intentionally not duplicated here.
 *
 * <p>Consistent with the {@link CbprRule} contract, this rule <strong>never throws</strong>: it
 * null-guards every hop of the navigation and collects <em>every</em> violation it finds (one
 * {@link Finding} per malformed BICFI) rather than stopping at the first. The message object graph is
 * navigated exclusively through read-only typed getters; the generated model is never mutated.
 *
 * <p>The class is stateless and therefore safe for concurrent reuse across threads.
 *
 * @see BicValidator
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @since 10.3.14
 */
public final class BicfiFormatRule implements CbprRule<MxPacs00800108> {

    /**
     * The CBPR+ rule identifier, in kebab-case, taken verbatim from the authoritative rule inventory.
     */
    private static final String RULE_ID = "bicfi-format";

    /**
     * Evaluates the {@code bicfi-format} rule against the supplied pacs.008.001.08 message.
     *
     * <p>Walks the group header and every credit-transfer transaction, checking the BICFI of each
     * agent that carries one. A malformed BICFI yields one {@link Severity#ERROR} finding; an absent
     * or blank BICFI yields none (presence is out of scope for this rule). The returned list is empty
     * when the message satisfies the rule and is never {@code null}.
     *
     * <p>The method tolerates partial and {@code null} branches without throwing, in keeping with the
     * {@link CbprRule} resilience contract.
     *
     * @param message the parsed pacs.008.001.08 message to validate; may be {@code null} or partially
     *     populated
     * @return the list of BICFI-format violations, empty when compliant and never {@code null}
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

        // Group-header agents: the point-to-point instructing / instructed agents at message level.
        GroupHeader93 grpHdr = cdtTrf.getGrpHdr();
        if (grpHdr != null) {
            checkAgent(grpHdr.getInstgAgt(), "FIToFICstmrCdtTrf/GrpHdr/InstgAgt", findings);
            checkAgent(grpHdr.getInstdAgt(), "FIToFICstmrCdtTrf/GrpHdr/InstdAgt", findings);
        }

        // Transaction-level agents on every credit-transfer transaction occurrence.
        List<CreditTransferTransaction39> transactions = cdtTrf.getCdtTrfTxInf();
        if (transactions != null) {
            for (int i = 0; i < transactions.size(); i++) {
                CreditTransferTransaction39 tx = transactions.get(i);
                if (tx == null) {
                    continue;
                }
                String base = "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]";
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
            }
        }

        return findings;
    }

    /**
     * Validates the BICFI format of a single agent and appends a finding when the value is present but
     * malformed.
     *
     * <p>The check is strictly format-only and fully null-safe:
     *
     * <ul>
     *   <li>a {@code null} agent or a {@code null} {@code FinInstnId} is skipped silently (nothing to
     *       validate);
     *   <li>an absent or blank BICFI is skipped &mdash; presence is enforced by a separate rule;
     *   <li>a present, non-blank BICFI that fails {@link BicValidator#isValid(String)} produces exactly
     *       one {@link Severity#ERROR} finding.
     * </ul>
     *
     * @param agent the agent to inspect; may be {@code null}
     * @param path the human-readable element path of {@code agent} (the {@code /FinInstnId/BICFI}
     *     suffix is appended when a finding is raised)
     * @param findings the accumulator to which any finding is added
     */
    private void checkAgent(
            BranchAndFinancialInstitutionIdentification6 agent, String path, List<Finding> findings) {
        if (agent == null) {
            return;
        }
        FinancialInstitutionIdentification18 fi = agent.getFinInstnId();
        if (fi == null) {
            return;
        }
        String bicfi = fi.getBICFI();
        if (StringUtils.isNotBlank(bicfi) && !BicValidator.isValid(bicfi)) {
            findings.add(new Finding(
                    RULE_ID,
                    Severity.ERROR,
                    path + "/FinInstnId/BICFI",
                    "BICFI '" + bicfi + "' does not match ISO 9362 (must be 8 or 11 chars: "
                            + "[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?)."));
        }
    }
}
