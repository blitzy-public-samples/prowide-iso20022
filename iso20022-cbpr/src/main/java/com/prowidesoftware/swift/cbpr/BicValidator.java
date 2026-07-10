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

import java.util.regex.Pattern;

/**
 * Stateless predicate that validates a Business Identifier Code (BIC / BICFI) against the ISO 9362 format.
 *
 * <p>This helper backs the CBPR+ Standards Release 2026 {@code bicfi-format} rule as it applies to the
 * pacs.008.001.08, pacs.009.001.08 and pain.001.001.09 message types, whose shared requirement is:
 * <em>"All agents' FinInstnId/BICFI must match ISO 9362 (8 or 11 chars)"</em>.
 *
 * <p>The single authoritative pattern, preserved verbatim from the CBPR+ specification, is:
 *
 * <pre>{@code [A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?}</pre>
 *
 * which decomposes into the four ISO 9362 components:
 *
 * <ul>
 *   <li>a 4-character business party prefix &mdash; {@code [A-Z0-9]{4}};
 *   <li>a 2-character ISO 3166-1 country code &mdash; {@code [A-Z]{2}} (letters only);
 *   <li>a 2-character location code &mdash; {@code [A-Z0-9]{2}};
 *   <li>an optional 3-character branch code &mdash; {@code ([A-Z0-9]{3})?}.
 * </ul>
 *
 * Consequently a conforming value has a length of <strong>exactly 8 or 11</strong> characters; any other
 * length is rejected.
 *
 * <p>Matching is performed with {@link java.util.regex.Matcher#matches()} (not
 * {@link java.util.regex.Matcher#find()}) so that the <em>entire</em> input must conform. Using
 * {@code find()} would incorrectly accept a valid BIC embedded inside a longer, otherwise invalid string.
 *
 * <p>The check is deliberately strict about case and padding: ISO 9362 BICs are upper-case, so a lower-case
 * or whitespace-padded value legitimately fails the format rule. The input is matched exactly as supplied
 * &mdash; it is never trimmed nor upper-cased.
 *
 * <p><strong>This class never throws.</strong> A {@code null} input simply yields {@code false}, allowing
 * the calling {@code BicfiFormatRule} implementations to raise a {@code Finding} for an absent or malformed
 * BICFI rather than propagating an exception.
 *
 * <p>The type is a stateless utility: it is {@code final}, exposes only static members and cannot be
 * instantiated. The compiled {@link Pattern} is immutable and therefore safe for concurrent use by multiple
 * threads.
 *
 * @since SRU2026
 */
public final class BicValidator {

    /**
     * The ISO 9362 BICFI pattern, compiled once and reused for every check.
     *
     * <p>Preserved verbatim from the CBPR+ specification:
     * {@code [A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?} &mdash; an 8-character BIC (a 4-character party
     * prefix, a 2-character country code and a 2-character location code) with an optional 3-character
     * branch code, i.e. an overall length of 8 or 11 characters.
     */
    private static final Pattern BICFI = Pattern.compile("[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?");

    /**
     * Not instantiable; this is a stateless utility that exposes only static members.
     */
    private BicValidator() {
        // utility class - no instances
    }

    /**
     * Tests whether the supplied value is a well-formed ISO 9362 BIC (BICFI).
     *
     * <p>Implements the CBPR+ {@code bicfi-format} rule: <em>"All agents' FinInstnId/BICFI must match ISO
     * 9362 (8 or 11 chars)"</em>. The whole string must match the pattern
     * {@code [A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?}, so only upper-case values of length exactly 8
     * or 11 with the correct character classes are accepted.
     *
     * <p>The method is null-safe and never throws: a {@code null} argument returns {@code false}. The value
     * is matched exactly as provided (no trimming and no case folding).
     *
     * @param bic the BICFI value to validate, typically obtained from
     *     {@code FinancialInstitutionIdentification18.getBICFI()}; may be {@code null}
     * @return {@code true} if {@code bic} is non-null and matches the ISO 9362 BICFI format (length 8 or
     *     11); {@code false} otherwise
     */
    public static boolean isValid(String bic) {
        if (bic == null) {
            return false;
        }
        return BICFI.matcher(bic).matches();
    }
}
