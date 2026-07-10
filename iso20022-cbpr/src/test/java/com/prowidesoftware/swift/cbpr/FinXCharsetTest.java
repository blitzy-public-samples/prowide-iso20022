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
 * Unit tests for {@link FinXCharset}, the shared character-set predicate backing the three CBPR+
 * {@code charset-finx-extended-for-name-address} rule applications (pacs.008.001.08, pacs.009.001.08 and
 * pain.001.001.09).
 *
 * <p>The two predicates differ only by the extended punctuation set, so these tests deliberately exercise the
 * boundary between them: values built from extended-only punctuation are rejected by the base
 * {@link FinXCharset#isValidFinX(String)} yet accepted by {@link FinXCharset#isValidExtended(String)}, whereas
 * accented characters are rejected by both. Absent values ({@code null} or empty) are always accepted, and neither
 * predicate ever throws.
 *
 * <p>Accented characters are written as Unicode escapes (for example {@code \u00e9}, {@code \u00c9}, {@code \u00cd})
 * so the source file is pure ASCII and the exact code points under test are unambiguous.
 */
class FinXCharsetTest {

    // ------------------------------------------------------------------
    // Base FIN-X positives (isValidFinX -> true)
    // ------------------------------------------------------------------

    /** Letters, digits and the space character form a valid base FIN-X value. */
    @Test
    void baseAlphanumericAndSpaceValid() {
        assertThat(FinXCharset.isValidFinX("ACME BANK LONDON 123")).isTrue();
    }

    /** The base punctuation set (slash, parentheses, hyphen, colon, comma, dot) is accepted. */
    @Test
    void basePunctuationValid() {
        assertThat(FinXCharset.isValidFinX("ACME BANK / LONDON (UK) - REF:123, CO.")).isTrue();
    }

    /** The apostrophe and plus sign belong to the base FIN-X set. */
    @Test
    void basePlusAndApostropheValid() {
        assertThat(FinXCharset.isValidFinX("O'BRIEN + SON")).isTrue();
    }

    /** CR/LF line breaks are valid because Name and Address are multi-line fields. */
    @Test
    void baseCrLfValid() {
        assertThat(FinXCharset.isValidFinX("LINE1\r\nLINE2")).isTrue();
    }

    // ------------------------------------------------------------------
    // Base FIN-X negatives (isValidFinX -> false)
    // ------------------------------------------------------------------

    /** Accented characters are outside the base FIN-X set and must be rejected. */
    @Test
    void accentedRejectedByBase() {
        assertThat(FinXCharset.isValidFinX("Caf\u00e9")).isFalse();
        assertThat(FinXCharset.isValidFinX("JOS\u00c9 GARC\u00cdA")).isFalse();
    }

    /** Extended-only punctuation (ampersand, underscore) is rejected by the base predicate. */
    @Test
    void extendedPunctuationRejectedByBase() {
        assertThat(FinXCharset.isValidFinX("R&D")).isFalse();
        assertThat(FinXCharset.isValidFinX("A_B")).isFalse();
    }

    // ------------------------------------------------------------------
    // Extended positives (isValidExtended -> true)
    // ------------------------------------------------------------------

    /**
     * The ampersand and underscore are accepted by the extended predicate; the same value is rejected by the base
     * predicate, locking the boundary between the two character sets in both directions.
     */
    @Test
    void extendedAmpersandUnderscoreValid() {
        assertThat(FinXCharset.isValidExtended("R&D _dept_")).isTrue();
        assertThat(FinXCharset.isValidFinX("R&D _dept_")).isFalse();
    }

    /** Proxy-email values are valid under the extended set (the at-sign is extended, the dot is base). */
    @Test
    void extendedEmailValid() {
        assertThat(FinXCharset.isValidExtended("john.doe@acme.com")).isTrue();
    }

    /** A value containing the full extended-additional punctuation set is accepted. */
    @Test
    void extendedFullPunctuationValid() {
        assertThat(FinXCharset.isValidExtended("A!\"#%&*;<>=@_{}|~[]\\$B")).isTrue();
    }

    /** Every base value is also a valid extended value (base is a subset of extended). */
    @Test
    void baseStringIsAlsoExtendedValid() {
        assertThat(FinXCharset.isValidExtended("ACME BANK / LONDON")).isTrue();
    }

    // ------------------------------------------------------------------
    // Extended negatives, null/empty and never-throws
    // ------------------------------------------------------------------

    /** Accented characters are rejected even by the extended predicate. */
    @Test
    void extendedStillRejectsAccents() {
        assertThat(FinXCharset.isValidExtended("Caf\u00e9")).isFalse();
    }

    /** A {@code null} or empty value is valid for both predicates (an absent value is not a charset violation). */
    @Test
    void nullAndEmptyAreValidForBoth() {
        assertThat(FinXCharset.isValidFinX(null)).isTrue();
        assertThat(FinXCharset.isValidFinX("")).isTrue();
        assertThat(FinXCharset.isValidExtended(null)).isTrue();
        assertThat(FinXCharset.isValidExtended("")).isTrue();
    }

    /** Neither predicate throws for any input, including {@code null} and rejected values. */
    @Test
    void neverThrows() {
        assertThatCode(() -> FinXCharset.isValidFinX(null)).doesNotThrowAnyException();
        assertThatCode(() -> FinXCharset.isValidExtended("Caf\u00e9")).doesNotThrowAnyException();
    }
}
