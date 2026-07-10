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
package com.prowidesoftware.swift.cbpr;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single public entry point for CBPR+ Standards Release 2026 (SR2026) validation of ISO 20022
 * payment messages.
 *
 * <p>This validator implements <em>Capability A</em> of the {@code iso20022-cbpr} module: it checks a
 * parsed MX message against the CBPR+ SR2026 rule set &mdash; centred on the SWIFT 14&nbsp;November
 * 2026 structured-address mandate &mdash; for exactly three message types:
 *
 * <ul>
 *   <li>{@link MxPacs00800108} (pacs.008.001.08, FI-to-FI Customer Credit Transfer) &mdash; 9 rules;
 *   <li>{@link MxPacs00900108} (pacs.009.001.08, Financial Institution Credit Transfer) &mdash;
 *       7 rules;
 *   <li>{@link MxPain00100109} (pain.001.001.09, Customer Credit Transfer Initiation) &mdash;
 *       5 rules.
 * </ul>
 *
 * <p>The total of 9&nbsp;+&nbsp;7&nbsp;+&nbsp;5&nbsp;=&nbsp;21 rule applications maps one-to-one to
 * the authoritative CBPR+ rule inventory. There is intentionally <strong>no runtime
 * rules-interpreter</strong>: each rule is an explicit {@link CbprRule} implementation, and this
 * class merely selects the correct rule set for the concrete message type and aggregates the
 * findings those rules emit.
 *
 * <h2>Dispatch</h2>
 *
 * <p>The concrete message type is resolved with a single {@code instanceof} check (and the
 * corresponding cast), which is what lets each rule be invoked through its strongly-typed
 * {@link CbprRule#check(AbstractMX) check} method without any cast of its own. The message
 * namespaces ({@code ...pacs.008.001.08}, {@code ...pacs.009.001.08}, {@code ...pain.001.001.09})
 * available via {@link AbstractMX#getNamespace()} are the documented alternative discriminator, but
 * {@code instanceof} is used here because the typed cast is required regardless. Any other
 * {@link AbstractMX} subtype falls through to a clean, valid result &mdash; no CBPR+ rule set applies
 * to it, and no exception is raised.
 *
 * <h2>Core behavioural contract &mdash; never throw, collect all</h2>
 *
 * <p>{@link #validate(AbstractMX)} <strong>never throws</strong>. A {@code null}, malformed, partial
 * or non-compliant message is reported through the returned {@link ValidationResult} as zero or more
 * {@link Finding} objects &mdash; never as an exception. Every applicable rule is executed and its
 * findings are aggregated in discovery order; the validator does <strong>not</strong> stop at the
 * first violation. Although each individual rule is itself written to be defensive and non-throwing,
 * every rule invocation is additionally wrapped in a safety net (see {@link #runRules}) so that a
 * defect in any single rule can neither propagate out of {@code validate} nor prevent the remaining
 * rules from running. Partial messages &mdash; for example the address-scoped output of the MT&rarr;MX
 * migrators &mdash; are therefore validated safely despite their many {@code null} branches.
 *
 * <p>The returned {@link ValidationResult} is {@link ValidationResult#isValid() valid} if and only if
 * it carries no {@link Severity#ERROR}-severity finding; a result carrying only
 * {@link Severity#WARNING}-severity findings is still valid. Validity is derived inside
 * {@code ValidationResult}: this validator only aggregates findings and never computes or sets the
 * flag itself.
 *
 * <h2>Statelessness and thread-safety</h2>
 *
 * <p>Instances hold no mutable state; a fresh findings accumulator is created per
 * {@link #validate(AbstractMX)} call and the rule instances are stateless. A single
 * {@code CbprValidator} may therefore be created once and reused freely, including concurrently from
 * multiple threads.
 *
 * @see CbprRule
 * @see ValidationResult
 * @see Finding
 * @see Severity
 * @since 10.3.14
 */
public class CbprValidator {

    /**
     * Logger used only to record, at {@link Level#WARNING}, the (unexpected) event of a rule throwing
     * a {@link RuntimeException}. Consistent with the repository convention of using
     * {@code java.util.logging}. Logging never alters the never-throw contract.
     */
    private static final Logger log = Logger.getLogger(CbprValidator.class.getName());

    /**
     * Validates the supplied MX message against the CBPR+ SR2026 rule set that applies to its
     * concrete type and returns the aggregated outcome.
     *
     * <p>This method embodies the module's core contract: it <strong>never throws</strong> and it
     * <strong>collects every finding</strong> from every applicable rule (it does not short-circuit
     * on the first violation). Dispatch is by concrete subtype:
     *
     * <ul>
     *   <li>{@link MxPacs00800108} &rarr; the 9 pacs.008 rules;
     *   <li>{@link MxPacs00900108} &rarr; the 7 pacs.009 rules;
     *   <li>{@link MxPain00100109} &rarr; the 5 pain.001 rules;
     *   <li>{@code null} or any other {@link AbstractMX} subtype &rarr; an empty, valid result (no
     *       CBPR+ rule set applies).
     * </ul>
     *
     * @param message the parsed MX message to validate; may be {@code null}, partial or malformed
     *     without causing an exception
     * @return a non-{@code null} {@link ValidationResult} bundling all findings produced during
     *     validation; {@link ValidationResult#isValid() valid} when no {@link Severity#ERROR}-severity
     *     finding is present
     */
    public ValidationResult validate(AbstractMX message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            // Nothing to validate: return a clean, valid result rather than throwing.
            return new ValidationResult(findings);
        }
        if (message instanceof MxPacs00800108) {
            runRules(pacs008Rules(), (MxPacs00800108) message, findings);
        } else if (message instanceof MxPacs00900108) {
            runRules(pacs009Rules(), (MxPacs00900108) message, findings);
        } else if (message instanceof MxPain00100109) {
            runRules(pain001Rules(), (MxPain00100109) message, findings);
        }
        // Any other MX type carries no CBPR+ rule set here -> the findings list stays empty (valid).
        return new ValidationResult(findings);
    }

    /**
     * Runs every rule in the supplied ordered list against the message and appends their findings to
     * the accumulator, guaranteeing the never-throw contract.
     *
     * <p>Each rule invocation is wrapped individually: a {@link RuntimeException} escaping any single
     * rule is caught, logged at {@link Level#WARNING} and swallowed, so it neither propagates out of
     * {@link #validate(AbstractMX)} nor prevents the remaining rules from running. This is a
     * safety net layered on top of the rules' own defensive coding, ensuring findings are collected
     * from every rule even in the presence of a rule defect. A {@code null} list returned by a rule
     * is tolerated and contributes no findings.
     *
     * @param <T> the concrete {@link AbstractMX} message subtype the rules are bound to
     * @param rules the ordered rule set to execute; never {@code null}
     * @param message the strongly-typed message to pass to each rule; never {@code null} here
     * @param acc the accumulator collecting findings across all rules; never {@code null}
     */
    private <T extends AbstractMX> void runRules(List<CbprRule<T>> rules, T message, List<Finding> acc) {
        for (CbprRule<T> rule : rules) {
            try {
                List<Finding> ruleFindings = rule.check(message);
                if (ruleFindings != null) {
                    acc.addAll(ruleFindings);
                }
            } catch (RuntimeException ex) {
                // Never propagate: the validator's contract is to never throw. Log the unexpected
                // rule failure so it is diagnosable, then continue with the remaining rules so that
                // all findings are still collected.
                log.log(
                        Level.WARNING,
                        ex,
                        () -> "CBPR+ rule " + rule.getClass().getName() + " threw "
                                + ex.getClass().getSimpleName()
                                + " and was skipped; validation continues per the never-throw contract");
            }
        }
    }

    /**
     * Returns the ordered set of the 9 rules that apply to pacs.008.001.08 messages.
     *
     * <p>The membership and order match the authoritative CBPR+ inventory exactly &mdash; no rule is
     * added, dropped, merged or duplicated. Each rule is a fresh, stateless instance. Fully-qualified
     * class names are used deliberately because several rule simple names (for example
     * {@code StructuredAddressRule}, {@code BicfiFormatRule}, {@code CharsetFinxExtendedRule}) recur
     * across the per-message subpackages and therefore cannot all be imported by simple name.
     *
     * @return an ordered list of the 9 pacs.008 rules (never {@code null})
     */
    private List<CbprRule<MxPacs00800108>> pacs008Rules() {
        return Arrays.asList(
                new com.prowidesoftware.swift.cbpr.rules.pacs008.StructuredAddressRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.PartyNameWhenAddressPresentRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.CreditorNameWhenNoAnyBicRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.PartyAnyBicExcludesNameAddressRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.BicfiFormatRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.AgentPointToPointBicfiMandatoryRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.CharsetFinxExtendedRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.InstructedAgentGroupVsTxRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs008.InterbankSettlementDatePresenceRule());
    }

    /**
     * Returns the ordered set of the 7 rules that apply to pacs.009.001.08 messages.
     *
     * <p>The membership and order match the authoritative CBPR+ inventory exactly &mdash; no rule is
     * added, dropped, merged or duplicated. Each rule is a fresh, stateless instance, instantiated by
     * fully-qualified class name to avoid the simple-name collisions across subpackages.
     *
     * @return an ordered list of the 7 pacs.009 rules (never {@code null})
     */
    private List<CbprRule<MxPacs00900108>> pacs009Rules() {
        return Arrays.asList(
                new com.prowidesoftware.swift.cbpr.rules.pacs009.StructuredAddressRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs009.FiPartyBicfiPreferredRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs009.BicfiFormatRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs009.AgentNationalClearingCodeOnlyRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs009.InstructedAgentGroupVsTxRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs009.InterbankSettlementDatePresenceRule(),
                new com.prowidesoftware.swift.cbpr.rules.pacs009.CharsetFinxExtendedRule());
    }

    /**
     * Returns the ordered set of the 5 rules that apply to pain.001.001.09 messages.
     *
     * <p>The membership and order match the authoritative CBPR+ inventory exactly &mdash; no rule is
     * added, dropped, merged or duplicated. Each rule is a fresh, stateless instance, instantiated by
     * fully-qualified class name to avoid the simple-name collisions across subpackages.
     *
     * @return an ordered list of the 5 pain.001 rules (never {@code null})
     */
    private List<CbprRule<MxPain00100109>> pain001Rules() {
        return Arrays.asList(
                new com.prowidesoftware.swift.cbpr.rules.pain001.StructuredAddressRule(),
                new com.prowidesoftware.swift.cbpr.rules.pain001.PartyNameWhenAddressPresentRule(),
                new com.prowidesoftware.swift.cbpr.rules.pain001.BicfiFormatRule(),
                new com.prowidesoftware.swift.cbpr.rules.pain001.RelatedPresentWhenCopyDuplicateRule(),
                new com.prowidesoftware.swift.cbpr.rules.pain001.CharsetFinxExtendedRule());
    }
}
