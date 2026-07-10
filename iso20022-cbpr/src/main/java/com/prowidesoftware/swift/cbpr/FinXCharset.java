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
 * Character-set membership predicates backing the CBPR+ {@code charset-finx-extended-for-name-address} rule.
 *
 * <p>The CBPR+ Standards Release 2026 rule set states the requirement verbatim: <em>"FIN-X charset;
 * Name/Address/Remittance/proxy-email additionally permit an extended punctuation set"</em>. This class encodes
 * those two character sets as reusable, precompiled predicates so that the three
 * {@code CharsetFinxExtendedRule} applications (for pacs.008.001.08, pacs.009.001.08 and pain.001.001.09) can test
 * payment string values against a single, auditable definition rather than duplicating the character logic.
 *
 * <h2>Base FIN-X (SWIFT "X") character set</h2>
 *
 * <p>The base set accepted by {@link #isValidFinX(String)} comprises exactly:
 *
 * <ul>
 *   <li>the Latin letters {@code A}-{@code Z} and {@code a}-{@code z};
 *   <li>the digits {@code 0}-{@code 9};
 *   <li>the space character;
 *   <li>the punctuation characters {@code /}, {@code -}, {@code ?}, {@code :}, {@code (}, {@code )}, {@code .},
 *       {@code ,}, {@code '} and {@code +}; and
 *   <li>the carriage-return ({@code \r}) and line-feed ({@code \n}) characters, since Name and Address are
 *       multi-line fields.
 * </ul>
 *
 * <h2>Extended character set</h2>
 *
 * <p>{@link #isValidExtended(String)} accepts every character of the base set <strong>plus</strong> the following
 * additional punctuation permitted for Name / Address / Remittance / proxy-email fields:
 *
 * <pre>    ! " # % &amp; * ; &lt; &gt; = @ _ { } | ~ [ ] \ $</pre>
 *
 * <h2>Null / empty handling</h2>
 *
 * <p>Both predicates return {@code true} for a {@code null} or empty input. An absent value is not a character-set
 * <em>violation</em>: presence and mandatory-ness are enforced by the other CBPR+ rules, never by this helper. This
 * choice keeps the charset rules orthogonal to the presence rules.
 *
 * <h2>Scope and fidelity note</h2>
 *
 * <p>This class is a character-set predicate <em>helper</em>, not a CBPR+ rule from the authoritative rule
 * inventory. It only encodes the character-set membership that the existing {@code charset-*} rule tests against.
 * Should the definitive SWIFT CBPR+ extended character-set specification later prove to differ from the extended
 * set enumerated above, that difference is recorded as a documented gap under {@code docs/} — it is never applied
 * as a silent change to a CBPR+ rule.
 *
 * <p>This is a stateless, thread-safe utility: the two {@link Pattern} constants are immutable and compiled once,
 * and neither predicate ever throws for any input.
 *
 * @since 10.3.10
 */
public final class FinXCharset {

    /**
     * Precompiled predicate for the base FIN-X (SWIFT "X") character set.
     *
     * <p>Character class (regex): {@code [A-Za-z0-9/\-?:().,'+\r\n ]} — letters, digits, space, the punctuation
     * {@code / - ? : ( ) . , ' +}, and the CR/LF line breaks. The class is anchored and quantified with {@code *}
     * so that an empty sequence matches as well.
     */
    private static final Pattern FIN_X_BASE_PATTERN = Pattern.compile("^[A-Za-z0-9/\\-?:().,'+\\r\\n ]*$");

    /**
     * Precompiled predicate for the extended character set: the base FIN-X set plus the additional punctuation
     * permitted for Name / Address / Remittance / proxy-email fields.
     *
     * <p>Character class (regex): the base class above with the additional literals
     * {@code ! " # % & * ; < > = @ _ { } | ~ [ ] \ $} appended, each escaped as required for membership inside a
     * character class (for example {@code \[}, {@code \]}, {@code \\} and {@code \&}).
     */
    private static final Pattern FIN_X_EXTENDED_PATTERN =
            Pattern.compile("^[A-Za-z0-9/\\-?:().,'+\\r\\n !\"#%\\&*;<>=@_{}|~\\[\\]\\\\$]*$");

    /**
     * Non-instantiable static utility class.
     */
    private FinXCharset() {}

    /**
     * Tests whether every character of the given value belongs to the base FIN-X (SWIFT "X") character set.
     *
     * <p>The base set is: {@code A}-{@code Z}, {@code a}-{@code z}, {@code 0}-{@code 9}, the space character, the
     * punctuation {@code / - ? : ( ) . , ' +}, and the CR/LF line breaks.
     *
     * <p>A {@code null} or empty value returns {@code true}: an absent value is not a character-set violation
     * (presence is enforced by other rules). This method never throws for any input.
     *
     * @param value the string to test; may be {@code null}
     * @return {@code true} if {@code value} is {@code null}, empty, or composed solely of base FIN-X characters;
     *     {@code false} otherwise
     */
    public static boolean isValidFinX(final String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return FIN_X_BASE_PATTERN.matcher(value).matches();
    }

    /**
     * Tests whether every character of the given value belongs to the extended character set: the base FIN-X set
     * plus the additional punctuation permitted for Name / Address / Remittance / proxy-email fields
     * ({@code ! " # % & * ; < > = @ _ { } | ~ [ ] \ $}).
     *
     * <p>This is the predicate invoked by the three {@code charset-finx-extended-for-name-address} rule
     * applications on party/agent Name, postal-address, remittance and proxy-email string values.
     *
     * <p>A {@code null} or empty value returns {@code true}: an absent value is not a character-set violation
     * (presence is enforced by other rules). This method never throws for any input.
     *
     * @param value the string to test; may be {@code null}
     * @return {@code true} if {@code value} is {@code null}, empty, or composed solely of base-plus-extended
     *     characters; {@code false} otherwise
     */
    public static boolean isValidExtended(final String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return FIN_X_EXTENDED_PATTERN.matcher(value).matches();
    }
}
