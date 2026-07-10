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

/**
 * Classifies the severity of a CBPR+ validation {@link Finding}.
 *
 * <p>Every CBPR+ Standards Release 2026 (SR2026) rule application carries exactly one of the two
 * severities defined here, and no others: a rule is either an {@code error} or a {@code warning}.
 * These map one-to-one to {@link #ERROR} and {@link #WARNING} respectively.
 *
 * <ul>
 *   <li>{@link #ERROR} &mdash; a non-compliant condition that makes the validated message
 *       <em>invalid</em>. The presence of at least one {@code ERROR}-severity finding is what
 *       renders a message non-compliant.
 *   <li>{@link #WARNING} &mdash; a preference or soft recommendation that does <em>not</em> by
 *       itself make the message invalid. A message that carries only {@code WARNING}-severity
 *       findings is still considered valid.
 * </ul>
 *
 * <p>Accordingly, a {@link ValidationResult} is {@code valid} only when it contains no
 * {@code ERROR}-severity finding.
 *
 * @since 10.3.14
 */
public enum Severity {

    /** A non-compliant condition that makes the validated message invalid. */
    ERROR("Error"),

    /** A preference or soft recommendation that does not by itself make the message invalid. */
    WARNING("Warning");

    private final String description;

    Severity(String description) {
        this.description = description;
    }

    /**
     * Returns the human-readable description of this severity.
     *
     * @return the description, e.g. {@code "Error"} for {@link #ERROR}
     */
    public String description() {
        return description;
    }
}
