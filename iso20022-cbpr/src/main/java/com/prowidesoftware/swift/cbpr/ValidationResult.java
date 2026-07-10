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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Immutable, aggregated outcome of a single CBPR+ validation run.
 *
 * <p>A {@code ValidationResult} is what {@code CbprValidator.validate(AbstractMX)} returns after
 * evaluating every rule applicable to a message. It bundles together the complete, ordered list of
 * {@link Finding} objects produced during validation together with a single derived
 * {@link #isValid() valid} flag that summarises whether the message is compliant.
 *
 * <p><strong>Validity is strictly derived, never set independently.</strong> The result is
 * {@link #isValid() valid} if and only if it carries <em>no</em> {@link Severity#ERROR}-severity
 * finding. A result that contains only {@link Severity#WARNING}-severity findings (for example the
 * soft preferences {@code fi-party-bicfi-preferred}, {@code agent-national-clearing-code-only} or
 * {@code related-present-when-copydupl}) is still valid. Because the flag is computed from the
 * findings at construction time and can never be mutated afterwards, a result can never
 * inconsistently claim to be valid while carrying an error.
 *
 * <p><strong>This class performs no validation of its own and never throws.</strong> In keeping with
 * the validator's core contract that non-compliance is reported as findings rather than exceptions,
 * the constructor is fully null-safe: a {@code null} findings list yields an empty, valid result, and
 * any {@code null} elements within the list are tolerated and ignored when deriving validity. The
 * exposed findings list is an unmodifiable, defensive copy and is never {@code null}.
 *
 * <p>Instances are immutable &mdash; both attributes are assigned once at construction and never
 * change &mdash; and are therefore safe to share freely across threads.
 *
 * @since 10.3.14
 */
public final class ValidationResult {

    /**
     * Whether the validated message is compliant: {@code true} if and only if {@link #findings}
     * contains no {@link Severity#ERROR}-severity finding. Derived once at construction from the
     * findings and never mutated.
     */
    private final boolean valid;

    /**
     * The complete, ordered list of findings produced during validation. Always an unmodifiable,
     * defensive copy; never {@code null}; empty when the message is clean.
     */
    private final List<Finding> findings;

    /**
     * Creates a result from the supplied findings, deriving {@link #isValid() validity} from them.
     *
     * <p>The incoming list is defensively copied into an unmodifiable list, so subsequent changes to
     * the caller's list never affect this result and callers cannot mutate the exposed list. This
     * constructor is fully null-safe and never throws:
     *
     * <ul>
     *   <li>a {@code null} {@code findings} argument is treated as an empty list, yielding an empty,
     *       valid result;
     *   <li>{@code null} elements inside the list are tolerated and are ignored when deriving
     *       validity.
     * </ul>
     *
     * @param findings the findings produced by the validator; may be {@code null} or contain
     *     {@code null} elements
     */
    public ValidationResult(List<Finding> findings) {
        this.findings =
                Collections.unmodifiableList(new ArrayList<>(findings == null ? Collections.emptyList() : findings));
        this.valid = this.findings.stream().noneMatch(f -> f != null && f.getSeverity() == Severity.ERROR);
    }

    /**
     * Returns a clean, valid result carrying no findings.
     *
     * <p>Convenience factory equivalent to {@code new ValidationResult(Collections.emptyList())},
     * useful when a message passes every applicable rule.
     *
     * @return an empty, valid {@code ValidationResult} (never {@code null})
     */
    public static ValidationResult clean() {
        return new ValidationResult(Collections.emptyList());
    }

    /**
     * Indicates whether the validated message is compliant.
     *
     * @return {@code true} if there is no {@link Severity#ERROR}-severity finding (including when
     *     there are no findings at all, or only {@link Severity#WARNING}-severity findings);
     *     {@code false} if at least one {@link Severity#ERROR}-severity finding is present
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Indicates whether the result carries at least one {@link Severity#ERROR}-severity finding.
     *
     * <p>This is the exact complement of {@link #isValid()} and is provided for readability at call
     * sites and in tests.
     *
     * @return {@code true} if at least one {@link Severity#ERROR}-severity finding is present;
     *     {@code false} otherwise
     */
    public boolean hasErrors() {
        return !valid;
    }

    /**
     * Returns the complete, ordered list of findings produced during validation.
     *
     * <p>The returned list is unmodifiable and never {@code null}: it is empty when the message is
     * clean. Attempting to mutate it throws {@link UnsupportedOperationException}; this never occurs
     * during validation itself.
     *
     * @return the unmodifiable list of findings (never {@code null})
     */
    public List<Finding> getFindings() {
        return findings;
    }

    /**
     * Returns the findings that relate to the given rule identifier, preserving their discovery
     * order.
     *
     * <p>The match is null-safe on both sides: passing a {@code null} {@code ruleId} returns the
     * findings whose own {@code ruleId} is {@code null}, and any {@code null} finding in the list is
     * ignored. The returned list is a new, unmodifiable list and is never {@code null}.
     *
     * @param ruleId the CBPR+ rule identifier to filter by (kebab-case); may be {@code null}
     * @return an unmodifiable list of the matching findings, in discovery order (never {@code null};
     *     empty when there is no match)
     */
    public List<Finding> findingsForRule(String ruleId) {
        List<Finding> matches = new ArrayList<>();
        for (Finding f : findings) {
            if (f != null && Objects.equals(f.getRuleId(), ruleId)) {
                matches.add(f);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    /**
     * Compares this result with another object for value equality.
     *
     * <p>Two results are equal when they carry equal findings (and, consequently, the same derived
     * validity). The comparison is reflection-based and null-safe.
     *
     * @param other the object to compare with; may be {@code null}
     * @return {@code true} if {@code other} is a {@code ValidationResult} with equal state;
     *     {@code false} otherwise
     */
    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * <p>The hash code is derived reflectively from all attributes.
     *
     * @return the hash code for this result
     */
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Returns a compact, human-readable representation intended to make test failures easy to read.
     *
     * <p>The rendering includes the derived validity, the number of findings and the findings
     * themselves.
     *
     * @return a string describing this result
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("valid", valid)
                .append("findingCount", findings.size())
                .append("findings", findings)
                .toString();
    }
}
