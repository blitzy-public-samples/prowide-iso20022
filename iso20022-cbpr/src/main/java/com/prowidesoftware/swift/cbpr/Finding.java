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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Immutable value object describing a single CBPR+ validation finding.
 *
 * <p>A {@code Finding} is the atomic unit of feedback produced by the CBPR+ Standards Release 2026
 * (SR2026) validator. Every rule check emits zero or more findings, which are aggregated (in order of
 * discovery) into a {@link ValidationResult}. A finding records <em>which</em> rule was evaluated,
 * <em>how</em> severe the outcome is, <em>where</em> in the message the condition was detected, and a
 * human-readable <em>explanation</em>.
 *
 * <p>The four attributes correspond exactly to the mandated contract:
 *
 * <ul>
 *   <li>{@link #getRuleId() ruleId} &mdash; the CBPR+ rule identifier in kebab-case, taken verbatim
 *       from the authoritative rule inventory (for example
 *       {@code structured-address-min-town-country});
 *   <li>{@link #getSeverity() severity} &mdash; {@link Severity#ERROR} or {@link Severity#WARNING};
 *   <li>{@link #getElementPath() elementPath} &mdash; a human-readable path to the offending element
 *       (for example {@code FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/PstlAdr});
 *   <li>{@link #getMessage() message} &mdash; a concise, human-readable explanation of the finding.
 * </ul>
 *
 * <p><strong>This class is a pure data carrier.</strong> It performs no validation of its own, holds
 * no reference to the validated message, and never throws: every attribute is optional and may be
 * {@code null}. This mirrors the validator's core contract that non-compliant or absent branches
 * yield findings rather than exceptions, so a finding can legitimately be constructed with, for
 * example, a {@code null} {@code elementPath} (a message-level condition) without any special
 * handling. Consequently {@link #equals(Object)}, {@link #hashCode()} and {@link #toString()} are all
 * null-safe.
 *
 * <p>Instances are immutable &mdash; all attributes are assigned once at construction and never change
 * &mdash; and are therefore safe to share freely across threads.
 *
 * @since 10.3.14
 */
public final class Finding {

    /**
     * The CBPR+ rule identifier this finding relates to, in kebab-case (for example
     * {@code structured-address-min-town-country}); may be {@code null}.
     */
    private final String ruleId;

    /**
     * The severity of this finding: {@link Severity#ERROR} or {@link Severity#WARNING}; may be
     * {@code null}.
     */
    private final Severity severity;

    /**
     * A human-readable path to the offending element (for example
     * {@code FIToFICstmrCdtTrf/CdtTrfTxInf/Cdtr/PstlAdr}); may be {@code null} for message-level
     * conditions.
     */
    private final String elementPath;

    /** A concise, human-readable explanation of the finding; may be {@code null}. */
    private final String message;

    /**
     * Creates an immutable finding from the supplied attributes.
     *
     * <p>All arguments are stored as-is with no validation, copying or normalization, and any of them
     * may be {@code null}: this constructor never throws.
     *
     * @param ruleId the CBPR+ rule identifier (kebab-case), for example
     *     {@code structured-address-min-town-country}; may be {@code null}
     * @param severity the finding severity ({@link Severity#ERROR} or {@link Severity#WARNING}); may
     *     be {@code null}
     * @param elementPath a human-readable path to the offending element; may be {@code null}
     * @param message a concise, human-readable explanation of the finding; may be {@code null}
     */
    public Finding(String ruleId, Severity severity, String elementPath, String message) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.elementPath = elementPath;
        this.message = message;
    }

    /**
     * Returns the CBPR+ rule identifier this finding relates to.
     *
     * @return the rule identifier in kebab-case, or {@code null} if none was supplied
     */
    public String getRuleId() {
        return ruleId;
    }

    /**
     * Returns the severity of this finding.
     *
     * @return {@link Severity#ERROR} or {@link Severity#WARNING}, or {@code null} if none was supplied
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Returns the human-readable path to the offending element.
     *
     * @return the element path, or {@code null} if none was supplied (for example a message-level
     *     condition)
     */
    public String getElementPath() {
        return elementPath;
    }

    /**
     * Returns the concise, human-readable explanation of this finding.
     *
     * @return the message, or {@code null} if none was supplied
     */
    public String getMessage() {
        return message;
    }

    /**
     * Compares this finding with another object for value equality.
     *
     * <p>Two findings are equal when all four attributes ({@code ruleId}, {@code severity},
     * {@code elementPath} and {@code message}) are equal. The comparison is reflection-based and
     * null-safe, so findings that carry {@code null} attributes compare correctly. Value equality
     * matters because tests assert on collections of findings.
     *
     * @param other the object to compare with; may be {@code null}
     * @return {@code true} if {@code other} is a {@code Finding} with equal attributes; {@code false}
     *     otherwise
     */
    @Override
    public boolean equals(Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * <p>The hash code is derived reflectively from all four attributes and is null-safe.
     *
     * @return the hash code for this finding
     */
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    /**
     * Returns a compact, human-readable representation of this finding, intended to make test failures
     * easy to read.
     *
     * <p>The rendering is null-safe: {@code null} attributes are printed as {@code <null>}.
     *
     * @return a string containing all four attributes
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("ruleId", ruleId)
                .append("severity", severity)
                .append("elementPath", elementPath)
                .append("message", message)
                .toString();
    }
}
