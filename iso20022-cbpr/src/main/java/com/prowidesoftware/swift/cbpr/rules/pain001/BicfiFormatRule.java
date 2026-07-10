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

import com.prowidesoftware.swift.cbpr.BicValidator;
import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction34;
import com.prowidesoftware.swift.model.mx.dic.CustomerCreditTransferInitiationV09;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import com.prowidesoftware.swift.model.mx.dic.GroupHeader85;
import com.prowidesoftware.swift.model.mx.dic.PaymentInstruction30;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code bicfi-format}</strong> for
 * <strong>pain.001.001.09</strong> (Customer Credit Transfer Initiation, {@link MxPain00100109}).
 *
 * <p>The authoritative requirement from the CBPR+ rule inventory is: <em>"All FinInstnId/BICFI must
 * match ISO 9362 (8 or 11 chars)."</em> &mdash; every Business Identifier Code carried by a
 * financial-institution identification (i.e. by an <em>agent</em>) anywhere in the message must be a
 * well-formed ISO 9362 BIC of exactly 8 or 11 characters. The severity of a violation is
 * {@link Severity#ERROR}.
 *
 * <h2>Scope of inspection &mdash; the complete set of pain.001 agents</h2>
 *
 * <p>In pain.001 the settlement participants are modelled as
 * {@link BranchAndFinancialInstitutionIdentification6}. This rule inspects <em>every</em> such
 * occurrence the message can carry, so that "all FinInstnId/BICFI" is honored with zero omissions:
 *
 * <ul>
 *   <li>the group-header forwarding agent &mdash; {@code CstmrCdtTrfInitn/GrpHdr/FwdgAgt};
 *   <li>for each {@code PaymentInformation} (a {@link PaymentInstruction30}), the debtor agent and the
 *       charges-account agent &mdash; {@code DbtrAgt} and {@code ChrgsAcctAgt};
 *   <li>for each transaction (a {@link CreditTransferTransaction34}) within a payment information, the
 *       creditor agent and the three intermediary agents &mdash; {@code CdtrAgt},
 *       {@code IntrmyAgt1}, {@code IntrmyAgt2} and {@code IntrmyAgt3}.
 * </ul>
 *
 * <p>Every occurrence is reached through read-only typed getter chains (no {@code MxNode} navigation
 * and no casts), keeping the traversal compile-safe and refactor-safe; the generated model is never
 * mutated.
 *
 * <h2>What this rule checks &mdash; and what it deliberately does not</h2>
 *
 * <p>This rule validates <strong>only the format of a BICFI value that is actually present</strong>.
 * It does <strong>not</strong> require a BICFI to exist: pain.001 agents may legitimately be
 * identified by a clearing-system member identification or by name together with a postal address
 * instead of a BICFI, and pain.001 carries <em>no</em> "agent-bicfi-mandatory" rule. Flagging an
 * absent BICFI would therefore <em>strengthen</em> the rule beyond the authoritative inventory, which
 * is forbidden. Consequently, when a {@code BICFI} is {@code null} this rule stays <em>silent</em> and
 * emits no finding.
 *
 * <p>A BICFI that <em>is</em> present is validated exactly as supplied &mdash; even a blank value is a
 * present value and, failing the ISO 9362 pattern, produces a finding. The value is never trimmed nor
 * upper-cased before validation: ISO 9362 BICs are upper-case, so a lower-case or whitespace-padded
 * value legitimately fails the format rule. The actual pattern match is delegated to
 * {@link BicValidator#isValid(String)} &mdash; which anchors the match with
 * {@link java.util.regex.Matcher#matches()} so only whole-string values of length exactly 8 or 11 with
 * the correct character classes are accepted &mdash; and the single authoritative pattern is
 * intentionally not duplicated here.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>In line with the {@link CbprRule} contract this rule <strong>never throws</strong>: a
 * {@code null} message, a missing {@code CstmrCdtTrfInitn} body, an absent group header, a
 * {@code null} payment information, a {@code null} transaction, a {@code null} agent, a {@code null}
 * {@code FinInstnId} or an absent {@code BICFI} are all treated as ordinary, expected inputs. It
 * <strong>collects every</strong> violation rather than stopping at the first, returning one
 * {@link Severity#ERROR} finding per malformed BICFI, each carrying a distinct {@code elementPath}. A
 * returned empty list means the message complies with this rule; the returned list is never
 * {@code null}.
 *
 * <p>The class is stateless and therefore safe for concurrent reuse across threads.
 *
 * @see BicValidator
 * @see CbprRule
 * @see MxPain00100109
 * @see Finding
 * @see Severity
 * @since 10.3.14
 */
public class BicfiFormatRule implements CbprRule<MxPain00100109> {

    /**
     * The CBPR+ rule identifier, preserved verbatim from the authoritative rule inventory. Emitted on
     * every {@link Finding} this rule produces.
     */
    private static final String RULE_ID = "bicfi-format";

    /**
     * Creates a new, stateless {@code bicfi-format} rule for pain.001.001.09.
     *
     * <p>A public no-argument constructor is provided so the validator can instantiate the rule
     * reflection-free with {@code new BicfiFormatRule()}. The rule holds no state and is safe to reuse
     * across messages.
     */
    public BicfiFormatRule() {
        // Stateless rule; nothing to initialize.
    }

    /**
     * Validates that every BICFI present on any agent in the supplied pain.001.001.09 message is
     * well-formed per ISO 9362.
     *
     * <p>The method walks the group-header forwarding agent, every payment information's debtor and
     * charges-account agents, and every transaction's creditor and intermediary agents, delegating the
     * per-value format decision to {@link #checkAgent}. It never throws and always returns a
     * non-{@code null} list: an empty list signals compliance, while each element describes a distinct
     * malformed BICFI (a present value that fails {@link BicValidator#isValid(String)}).
     *
     * @param message the parsed pain.001.001.09 message to validate; may be {@code null} or partial,
     *     in which case only the branches that are present are inspected
     * @return the list of {@link Severity#ERROR} findings, one per malformed BICFI, or an empty list
     *     when the message complies with this rule; never {@code null}
     */
    @Override
    public List<Finding> check(MxPain00100109 message) {
        List<Finding> findings = new ArrayList<>();

        // A null message is a legitimate, expected input: report nothing and never throw.
        if (message == null) {
            return findings;
        }

        CustomerCreditTransferInitiationV09 root = message.getCstmrCdtTrfInitn();
        if (root == null) {
            // No initiation body means there are no agents to inspect.
            return findings;
        }

        // (1) Group-header level: the forwarding agent that frames the message.
        GroupHeader85 grpHdr = root.getGrpHdr();
        if (grpHdr != null) {
            checkAgent(grpHdr.getFwdgAgt(), "CstmrCdtTrfInitn/GrpHdr/FwdgAgt", findings);
        }

        // (2) PaymentInformation level and, nested within, the transaction level. getPmtInf() is a
        //     lazily-initialized list and never returns null.
        List<PaymentInstruction30> pmtInfs = root.getPmtInf();
        for (int i = 0; i < pmtInfs.size(); i++) {
            PaymentInstruction30 pi = pmtInfs.get(i);
            if (pi == null) {
                continue;
            }
            // A 0-based index keeps each finding's elementPath distinct across payment informations.
            String piPath = "CstmrCdtTrfInitn/PmtInf[" + i + "]";
            checkAgent(pi.getDbtrAgt(), piPath + "/DbtrAgt", findings);
            checkAgent(pi.getChrgsAcctAgt(), piPath + "/ChrgsAcctAgt", findings);

            // getCdtTrfTxInf() is a lazily-initialized list and never returns null.
            List<CreditTransferTransaction34> txs = pi.getCdtTrfTxInf();
            for (int j = 0; j < txs.size(); j++) {
                CreditTransferTransaction34 tx = txs.get(j);
                if (tx == null) {
                    continue;
                }
                String txPath = piPath + "/CdtTrfTxInf[" + j + "]";
                checkAgent(tx.getCdtrAgt(), txPath + "/CdtrAgt", findings);
                checkAgent(tx.getIntrmyAgt1(), txPath + "/IntrmyAgt1", findings);
                checkAgent(tx.getIntrmyAgt2(), txPath + "/IntrmyAgt2", findings);
                checkAgent(tx.getIntrmyAgt3(), txPath + "/IntrmyAgt3", findings);
            }
        }

        return findings;
    }

    /**
     * Validates the BICFI format of a single agent and appends a finding when the value is present but
     * malformed.
     *
     * <p>Every navigation hop is null-guarded so the method is safe for the sparsest partial messages:
     *
     * <ul>
     *   <li>a {@code null} agent or a {@code null} {@code FinInstnId} is skipped silently (nothing to
     *       validate);
     *   <li>a {@code null} (absent) BICFI is skipped &mdash; pain.001 permits clearing-code / name
     *       identification and has no BICFI-mandatory rule, so flagging its absence would strengthen
     *       the rule and is forbidden;
     *   <li>a present BICFI (including a blank value) that fails {@link BicValidator#isValid(String)}
     *       produces exactly one {@link Severity#ERROR} finding at {@code elementPath + "/FinInstnId/BICFI"}.
     * </ul>
     *
     * @param agent the agent to inspect; may be {@code null}
     * @param elementPath the human-readable element path of {@code agent} (the {@code /FinInstnId/BICFI}
     *     suffix is appended when a finding is raised)
     * @param findings the accumulator to which any finding is added
     */
    private void checkAgent(
            BranchAndFinancialInstitutionIdentification6 agent, String elementPath, List<Finding> findings) {
        if (agent == null) {
            return;
        }
        FinancialInstitutionIdentification18 fi = agent.getFinInstnId();
        if (fi == null) {
            return;
        }
        String bicfi = fi.getBICFI();
        if (bicfi == null) {
            // BICFI absent (agent identified by clearing code / name) -> not a format violation.
            return;
        }
        if (!BicValidator.isValid(bicfi)) {
            findings.add(new Finding(
                    RULE_ID,
                    Severity.ERROR,
                    elementPath + "/FinInstnId/BICFI",
                    "BICFI '" + bicfi + "' does not match the ISO 9362 format (8 or 11 characters: 4"
                            + " alpha-numeric institution + 2 alpha country + 2 alpha-numeric"
                            + " location + optional 3 alpha-numeric branch)."));
        }
    }
}
