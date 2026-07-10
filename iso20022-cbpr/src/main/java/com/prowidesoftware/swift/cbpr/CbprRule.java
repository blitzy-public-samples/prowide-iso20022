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
import java.util.List;

/**
 * Contract implemented by every CBPR+ Standards Release 2026 (SR2026) validation rule.
 *
 * <p>A {@code CbprRule} encapsulates <strong>exactly one</strong> rule application from the
 * authoritative CBPR+ rule inventory: a single, self-contained check performed against an
 * already-parsed MX message. The design deliberately favors one concrete class per rule (each
 * exposing a single public {@link #check(AbstractMX) check} method) over a data-driven engine: the
 * rules are encoded at generation time as explicit Java code, and there is intentionally
 * <em>no</em> runtime rules-interpreter, no configuration DSL, no reflection and no rule registry
 * behind this contract. This keeps every rule individually readable, testable and traceable back to
 * a single line of the specification.
 *
 * <h2>Type parameter and message binding</h2>
 *
 * <p>The interface is parameterized by the concrete message type it validates, bounded by
 * {@link AbstractMX} &mdash; the common base of every generated {@code Mx*} message. Binding a rule
 * to its exact message type (for example {@code CbprRule<MxPacs00800108>}) lets each implementation
 * navigate the message object graph through typed getter chains with no casts of its own, keeping
 * the whole approach compile-safe and refactor-safe. The validator resolves the concrete message
 * type once (a single {@code instanceof}/cast per message), then hands the strongly-typed message to
 * every rule registered for that type. Each message type therefore owns an ordered list of
 * {@code CbprRule} instances that the validator iterates in turn, aggregating the findings they
 * return.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>Every implementation of this interface must honor the following contract:
 *
 * <ul>
 *   <li><strong>Return, never throw.</strong> {@link #check(AbstractMX) check} must never raise an
 *       exception, regardless of how sparse, partial or malformed the supplied message is. A missing
 *       optional branch, a {@code null} party or a {@code null} sub-element is a normal, expected
 *       input: the implementation translates such conditions into {@link Finding} objects (or, when
 *       the rule simply does not apply, into a clean pass) rather than propagating an error. This is
 *       the module's core resilience guarantee &mdash; non-compliance is reported data, not control
 *       flow.
 *   <li><strong>Empty list means compliant.</strong> A returned {@link List} that is empty signals
 *       that the message satisfies this rule. The list must never be {@code null}; a rule with
 *       nothing to report returns an empty list (for example {@link java.util.Collections#emptyList()}).
 *   <li><strong>One-or-more findings means non-compliant.</strong> Each element of the returned list
 *       describes a distinct violation, carrying the rule identifier, the {@link Severity} declared
 *       for that rule in the inventory ({@link Severity#ERROR} or {@link Severity#WARNING}), the path
 *       to the offending element and a human-readable explanation. A single rule may legitimately
 *       report several findings when it inspects several parties or agents within one message; it
 *       reports all of them rather than stopping at the first.
 *   <li><strong>One rule per implementation.</strong> Each implementing class corresponds to exactly
 *       one rule application from the CBPR+ inventory. Rules are never merged, and no implementation
 *       interprets external rule data; the mapping from specification rule to Java class is strictly
 *       one-to-one.
 * </ul>
 *
 * <p>The interface is annotated {@link FunctionalInterface} because it declares a single abstract
 * method, which both documents the "one public check method per rule" intent and permits lightweight
 * lambda implementations where convenient. Production rules are nonetheless realized as concrete,
 * individually named classes (one per rule) so that each carries its own Javadoc, is unit-testable in
 * isolation and is easy to locate; the annotation does not force the use of lambdas.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * public final class BicfiFormatRule implements CbprRule<MxPacs00800108> {
 *     @Override
 *     public List<Finding> check(MxPacs00800108 message) {
 *         List<Finding> findings = new ArrayList<>();
 *         // inspect message via typed getters; add a Finding per violation, never throw
 *         return findings; // empty == compliant
 *     }
 * }
 * }</pre>
 *
 * @param <T> the concrete {@link AbstractMX} message subtype this rule validates
 * @see Finding
 * @see Severity
 * @see ValidationResult
 * @see AbstractMX
 * @since 10.3.14
 */
@FunctionalInterface
public interface CbprRule<T extends AbstractMX> {

    /**
     * Evaluates this CBPR+ rule against the given (already parsed) MX message and returns the
     * findings it produces.
     *
     * <p>The returned list is <strong>empty</strong> when the message satisfies this rule, and
     * contains <strong>one or more</strong> {@link Finding} objects otherwise &mdash; one per distinct
     * violation the rule detects. The method never returns {@code null}.
     *
     * <p>Implementations <strong>must not throw</strong>. Absent, {@code null} or malformed branches
     * of the message must be handled gracefully: they either yield a {@link Finding} (with the
     * severity declared for this rule) or, when the rule does not apply to the message as supplied, a
     * clean pass (an empty list). Callers &mdash; principally the validator that aggregates findings
     * across every rule for a message type &mdash; rely on this guarantee to collect a complete
     * result without any per-rule error handling.
     *
     * @param message the parsed MX message to validate; a fully-formed or partial message of the
     *     concrete type {@code T}. Implementations tolerate partial messages and {@code null}
     *     sub-branches without throwing.
     * @return the list of findings, empty when the message complies with this rule and never
     *     {@code null}
     */
    List<Finding> check(T message);
}
