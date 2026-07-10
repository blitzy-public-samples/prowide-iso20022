/*
 * Copyright 2006-2026 Prowide
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BicValidator}, the shared ISO 9362 BICFI format predicate that backs the CBPR+
 * {@code bicfi-format} rule as it applies to pacs.008.001.08, pacs.009.001.08 and pain.001.001.09.
 *
 * <p>The production pattern is {@code [A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?} applied with
 * {@link java.util.regex.Matcher#matches()} (whole-string, anchored), so only upper-case values of length
 * exactly 8 or 11 with the correct positional character classes are accepted. The positional classes are:
 * institution (1-4) {@code [A-Z0-9]}, country (5-6) {@code [A-Z]} letters only, location (7-8)
 * {@code [A-Z0-9]}, and an optional branch (9-11) {@code [A-Z0-9]}.
 *
 * <p>These tests are pure and stateless: they exercise the sole public method
 * {@link BicValidator#isValid(String)} with string literals only, using no XML fixtures and no mocking.
 */
class BicValidatorTest {

    // --- Phase 1: valid BICs (8 and 11 characters) ---

    /** A well-formed 8-character BIC (party prefix + country + location) is accepted. */
    @Test
    void valid8CharBic() {
        assertThat(BicValidator.isValid("DEUTDEFF")).as("8-char BIC DEUTDEFF").isTrue();
        assertThat(BicValidator.isValid("CITTGB2L"))
                .as("8-char BIC with digit location CITTGB2L")
                .isTrue();
    }

    /** A well-formed 11-character BIC (8-char core plus a 3-char branch code) is accepted. */
    @Test
    void valid11CharBic() {
        assertThat(BicValidator.isValid("DEUTDEFF500"))
                .as("11-char BIC DEUTDEFF500")
                .isTrue();
        assertThat(BicValidator.isValid("ICBCUS4CXXX"))
                .as("11-char BIC ICBCUS4CXXX")
                .isTrue();
        assertThat(BicValidator.isValid("BANKESMMXXX"))
                .as("11-char BIC BANKESMMXXX")
                .isTrue();
    }

    /** Digits are permitted in the institution (1-4) and location (7-8) positions; country stays letters. */
    @Test
    void digitsAllowedInInstitutionAndLocation() {
        assertThat(BicValidator.isValid("0000GB22"))
                .as("digits in positions 1-4 and 7-8 with letter country GB")
                .isTrue();
    }

    // --- Phase 2: invalid length (only 8 or 11 are accepted) ---

    /** A 7-character value is too short and is rejected. */
    @Test
    void sevenCharsRejected() {
        assertThat(BicValidator.isValid("AAAABBC")).as("7 characters").isFalse();
    }

    /** A 9-character value lies between 8 and 11 and is rejected (off-by-one guard against "8 or 11"). */
    @Test
    void nineCharsRejected() {
        assertThat(BicValidator.isValid("AAAABBCCD")).as("9 characters").isFalse();
    }

    /** A 10-character value lies between 8 and 11 and is rejected. */
    @Test
    void tenCharsRejected() {
        assertThat(BicValidator.isValid("AAAABBCCDD")).as("10 characters").isFalse();
    }

    /** A 12-character value is too long and is rejected. */
    @Test
    void twelveCharsRejected() {
        assertThat(BicValidator.isValid("AAAABBCCDDDD")).as("12 characters").isFalse();
    }

    // --- Phase 3: invalid characters / positions ---

    /** Lower-case input is rejected; the value is matched exactly as supplied, with no upper-casing. */
    @Test
    void lowercaseRejected() {
        assertThat(BicValidator.isValid("deutdeff")).as("lower-case input").isFalse();
    }

    /** Digits in the country positions (5-6) are rejected; those positions accept letters only. */
    @Test
    void digitsInCountryPositionRejected() {
        assertThat(BicValidator.isValid("AAAA11CC"))
                .as("digits in country positions 5-6")
                .isFalse();
    }

    /** Punctuation is outside every character class and is rejected. */
    @Test
    void punctuationRejected() {
        assertThat(BicValidator.isValid("AAAA-BCC"))
                .as("hyphen not in any class")
                .isFalse();
    }

    /** The literal {@code INVALID} token used by the *_bicfi_format_negative.xml fixtures (7 chars) is rejected. */
    @Test
    void literalInvalidTokenRejected() {
        assertThat(BicValidator.isValid("INVALID")).as("INVALID literal token").isFalse();
    }

    // --- Phase 4: null / empty / never-throws ---

    /** A {@code null} argument yields {@code false} and never throws. */
    @Test
    void nullRejectedAndDoesNotThrow() {
        assertThatCode(() -> BicValidator.isValid(null)).doesNotThrowAnyException();
        assertThat(BicValidator.isValid(null)).isFalse();
    }

    /** The empty string is rejected. */
    @Test
    void emptyRejected() {
        assertThat(BicValidator.isValid("")).as("empty string").isFalse();
    }

    // --- Key insight: matches() is anchored, not a substring search ---

    /**
     * A valid BIC embedded inside a longer string is rejected: the predicate uses
     * {@link java.util.regex.Matcher#matches()} against the whole input rather than
     * {@link java.util.regex.Matcher#find()}, so surrounding junk cannot be ignored.
     */
    @Test
    void matchesIsAnchoredNotSubstring() {
        assertThat(BicValidator.isValid("xxDEUTDEFFxx"))
                .as("anchored whole-string match, not a substring search")
                .isFalse();
    }
}
