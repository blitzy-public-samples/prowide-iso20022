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
package com.prowidesoftware.swift.cbpr.migration;

import com.prowidesoftware.swift.model.mx.dic.AccountIdentification4Choice;
import com.prowidesoftware.swift.model.mx.dic.CashAccount38;
import com.prowidesoftware.swift.model.mx.dic.GenericAccountIdentification1;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared, {@code pw-swift-core}-free helper that translates MT name-and-address field lines into a freshly
 * constructed ISO 20022 {@link PostalAddress24} (Algorithm A4 of the CBPR+ MT&rarr;MX migration effort).
 *
 * <p>This helper is the foundational building block reused by the address-bearing migrators
 * ({@code Mt103ToPacs008} and {@code Mt101ToPain001}). By design it is decoupled from the Prowide Core MT API:
 * it consumes only plain {@link List} instances of {@link String} lines that the migrators extract from the MT
 * fields. Keeping this class free of any {@code com.prowidesoftware.swift.model.mt} /
 * {@code com.prowidesoftware.swift.model.field} dependency confines all MT-API usage to the three migrator
 * classes and makes the address-assembly logic here trivially unit-testable.
 *
 * <p><b>A4 line-semantic mapping.</b> Structured SWIFT "F" fields (for example Field 50F / 59F) encode their
 * content using numbered lines of the form {@code N/detail}:
 *
 * <ul>
 *   <li>line {@code 1} = Name;
 *   <li>line {@code 2} = Address Line;
 *   <li>line {@code 3} = Country and Town, formatted {@code CC/Town} where {@code CC} is the ISO 3166 two-letter
 *       country code.
 * </ul>
 *
 * <p>A properly formatted structured "F" field therefore yields a Structured {@code PstlAdr} (Town + Country
 * with no address line) or a Hybrid {@code PstlAdr} (Town + Country plus one or more address lines).
 * Unstructured name-and-address fields (Field 59 no-option / Field 50K) instead yield an address-line-only
 * {@code PstlAdr} that deliberately fails the CBPR+ hero rule {@code structured-address-min-town-country}.
 *
 * <p><b>Accompanying account/party identifier.</b> Structured and unstructured MT name-and-address fields may be
 * preceded by a leading account/party-identifier line of the form {@code /34x} (for example {@code /12345678}).
 * Per the CBPR+ migration scope, this identifier is migrated <em>alongside</em> the party name and postal
 * address. This helper therefore also exposes {@link #accountIdentifierFromLines(List)} (which returns the raw
 * identifier so it is never lost) and {@link #accountFromLines(List)} (which projects that identifier onto a
 * freshly built ISO 20022 {@link CashAccount38}). The identifier is still excluded from the name and from the
 * postal address itself; the migrators attach the returned {@link CashAccount38} to the appropriate target
 * account branch ({@code DbtrAcct} / {@code CdtrAcct}).
 *
 * <p>This helper only <em>builds</em> addresses and account identifiers; it never classifies addresses and
 * never weakens a rule. The Structured / Hybrid / Unstructured classification is performed later by the
 * validator's {@code PostalAddressClassifier}. Every method is null-safe and never throws: {@code null}, empty,
 * blank or otherwise malformed input yields an empty (but non-{@code null}) {@link PostalAddress24}, a
 * {@code null} name, or a {@code null} {@link CashAccount38}, as documented per method. The returned
 * {@link PostalAddress24} and {@link CashAccount38} are always brand-new objects; no input is ever mutated.
 *
 * <p>This is a pure static utility class and cannot be instantiated.
 */
public final class AddressMigrationSupport {

    /**
     * Matches a SWIFT structured line-number prefix of the form {@code N/detail}, where {@code N} is a single
     * digit {@code 1}-{@code 9}. Group {@code 1} captures the line number and group {@code 2} captures the
     * remaining detail. A leading account/party identifier such as {@code /12345678} does not match because it
     * carries no leading digit before the slash.
     */
    private static final Pattern NUMBERED_LINE = Pattern.compile("^\\s*([1-9])\\s*/(.*)$");

    /** Line number ({@code 1}) that carries the party name in a structured "F" field. */
    private static final String LINE_NAME = "1";

    /** Line number ({@code 2}) that carries an address line in a structured "F" field. */
    private static final String LINE_ADDRESS = "2";

    /**
     * Line number ({@code 3}) that carries the {@code CC/Town} country-and-town detail in a structured "F"
     * field.
     */
    private static final String LINE_COUNTRY_TOWN = "3";

    /**
     * Shape of an IBAN as defined by ISO 13616: two alphabetic country characters, two numeric check digits and
     * an 11-to-30 character alphanumeric BBAN (total length 15&ndash;34). This is a bounded structural check used
     * only to decide whether a migrated account identifier is projected as {@code IBAN} rather than as a generic
     * {@code Othr} identifier; it deliberately performs no check-digit (mod-97) verification.
     */
    private static final Pattern IBAN_PATTERN = Pattern.compile("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}");

    private AddressMigrationSupport() {
        // Pure static utility; not instantiable.
    }

    /**
     * Extracts the party name from a set of MT name-and-address field lines.
     *
     * <p>When the lines follow the structured "F" field convention, the name is taken from the {@code 1/} line.
     * Otherwise the first non-blank line that is not an account/party identifier (a line starting with
     * {@code /}) is treated as the name.
     *
     * @param lines the MT field lines as extracted by a migrator; may be {@code null} or empty
     * @return the party name trimmed of surrounding whitespace, or {@code null} when no name can be determined;
     *     never throws
     */
    public static String nameFromLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        // Structured "F" field: the name is the detail of the "1/" line.
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher matcher = NUMBERED_LINE.matcher(line);
            if (matcher.matches() && LINE_NAME.equals(matcher.group(1))) {
                return StringUtils.trimToNull(matcher.group(2));
            }
        }
        // Unstructured fallback: first non-blank line that is not an account/party identifier.
        for (String line : lines) {
            if (StringUtils.isBlank(line) || isPartyIdentifierLine(line)) {
                continue;
            }
            return line.trim();
        }
        return null;
    }

    /**
     * Builds a Structured or Hybrid {@link PostalAddress24} from the numbered lines of a structured SWIFT "F"
     * field (Field 50F / 59F).
     *
     * <p>The A4 line semantics are applied as follows: line {@code 1} (Name) is ignored here (use
     * {@link #nameFromLines(List)}); each line {@code 2} (Address Line) is appended as an address line; line
     * {@code 3} (Country and Town, formatted {@code CC/Town}) is split on the first {@code /} into the ISO 3166
     * country code and the town name. Lines {@code 4}-{@code 8}, when present, are ignored because only town and
     * country are relevant to the CBPR+ structured-address mandate; no other mapping is invented.
     *
     * <p>The resulting content lets the validator classify the address correctly: Town + Country with no address
     * line is Structured (passes); Town + Country with one or more address lines is Hybrid (passes). The
     * presence or absence of {@code 2/} lines therefore naturally selects Structured versus Hybrid.
     *
     * @param lines the numbered "F" field lines; may be {@code null} or empty
     * @return a new, never-{@code null} {@link PostalAddress24}; an empty address is returned for {@code null} or
     *     empty input; never throws
     */
    public static PostalAddress24 structuredAddressFromFFieldLines(List<String> lines) {
        PostalAddress24 address = new PostalAddress24();
        if (lines == null || lines.isEmpty()) {
            return address;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            Matcher matcher = NUMBERED_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String number = matcher.group(1);
            String detail = matcher.group(2);
            if (LINE_ADDRESS.equals(number)) {
                String addressLine = StringUtils.trimToNull(detail);
                if (addressLine != null) {
                    address.getAdrLine().add(addressLine);
                }
            } else if (LINE_COUNTRY_TOWN.equals(number)) {
                applyCountryAndTown(address, detail);
            }
            // Line 1 (Name) and lines 4-8 are intentionally ignored.
        }
        return address;
    }

    /**
     * Builds an address-line-only {@link PostalAddress24} from an unstructured MT name-and-address field (Field
     * 59 no-option / Field 50K).
     *
     * <p>A leading account/party identifier line (starting with {@code /}) is skipped here because it is neither
     * a name nor an address line; it is <em>not</em> discarded from the migration, however &mdash; the migrator
     * projects it onto the target account branch via {@link #accountFromLines(List)}. The first remaining
     * non-blank line is the party name and is <em>not</em> added to the address (the migrator sets it as the
     * party {@code Nm} via {@link #nameFromLines(List)}); every subsequent non-blank line is appended as an
     * address line (any accidental {@code N/} prefix is stripped defensively).
     *
     * <p><b>By design the town and country are left unset.</b> The produced address is therefore address-line
     * only and is <em>expected</em> to fail the CBPR+ hero rule {@code structured-address-min-town-country}.
     * This is the intentional negative path proving the validator flags fully unstructured addresses; no town or
     * country is ever inferred here.
     *
     * @param lines the free-format name-and-address lines; may be {@code null} or empty
     * @return a new, never-{@code null} {@link PostalAddress24} carrying only address lines; an empty address is
     *     returned for {@code null} or empty input; never throws
     */
    public static PostalAddress24 unstructuredAddressFromLines(List<String> lines) {
        PostalAddress24 address = new PostalAddress24();
        if (lines == null || lines.isEmpty()) {
            return address;
        }
        boolean nameConsumed = false;
        for (String line : lines) {
            if (StringUtils.isBlank(line)) {
                continue;
            }
            if (!nameConsumed) {
                if (isPartyIdentifierLine(line)) {
                    // Leading account/party identifier: skip without consuming the name slot.
                    continue;
                }
                // First meaningful line is the party name; it is not part of the postal address.
                nameConsumed = true;
                continue;
            }
            String addressLine = StringUtils.trimToNull(stripNumberPrefix(line));
            if (addressLine != null) {
                address.getAdrLine().add(addressLine);
            }
        }
        return address;
    }

    /**
     * Extracts the leading account/party identifier carried by an MT name-and-address field, that is, the
     * content of the first line whose first non-whitespace character is {@code /} (for example the {@code 12345678}
     * of a {@code /12345678} line).
     *
     * <p>Both the structured "F" fields (Field 50F / 59F) and the unstructured fields (Field 59 no-option /
     * Field 50K) may carry such an identifier as their first line, ahead of the name-and-address content. This
     * method returns that identifier so the migration never silently discards it; the numbered address lines
     * ({@code 1/}, {@code 2/}, {@code 3/}&hellip;) are never mistaken for it because they carry a leading digit
     * before the slash rather than a leading slash.
     *
     * @param lines the MT field lines as extracted by a migrator; may be {@code null} or empty
     * @return the account/party identifier trimmed of the leading {@code /} and surrounding whitespace, or
     *     {@code null} when no identifier line is present or it is blank; never throws
     */
    public static String accountIdentifierFromLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        for (String line : lines) {
            if (!isPartyIdentifierLine(line)) {
                continue;
            }
            // Strip the leading '/' and return the remaining identifier (for example "/12345678" -> "12345678").
            return StringUtils.trimToNull(line.trim().substring(1));
        }
        return null;
    }

    /**
     * Builds an ISO 20022 {@link CashAccount38} from the leading account/party identifier of an MT
     * name-and-address field, projecting the accompanying account identifier that the CBPR+ migration scope
     * requires alongside the party name and postal address.
     *
     * <p>The identifier is resolved with {@link #accountIdentifierFromLines(List)}. When it is structurally an
     * IBAN (per {@link #IBAN_PATTERN}) it is placed on {@code Id/IBAN}; otherwise it is placed on a generic
     * {@code Id/Othr/Id} ({@link GenericAccountIdentification1}). No other {@link CashAccount38} facet (currency,
     * name, type, proxy) is populated: only the account identifier is in migration scope, and none is invented.
     *
     * @param lines the MT field lines as extracted by a migrator; may be {@code null} or empty
     * @return a new {@link CashAccount38} carrying only the account identifier, or {@code null} when the field
     *     carries no account/party identifier line; never throws
     */
    public static CashAccount38 accountFromLines(List<String> lines) {
        String identifier = accountIdentifierFromLines(lines);
        if (identifier == null) {
            return null;
        }
        AccountIdentification4Choice accountId = new AccountIdentification4Choice();
        if (IBAN_PATTERN.matcher(identifier).matches()) {
            accountId.setIBAN(identifier);
        } else {
            accountId.setOthr(new GenericAccountIdentification1().setId(identifier));
        }
        return new CashAccount38().setId(accountId);
    }

    /**
     * Parses a line {@code 3} ({@code CC/Town}) detail and populates the country and town of the given address.
     * The detail is split on the first {@code /}: the portion before it is the ISO 3166 country code and the
     * portion after it is the town name. When the detail carries no {@code /} the whole token is treated
     * defensively as the town, leaving the country unset (which the validator will classify as Unstructured).
     *
     * @param address the address being assembled; never {@code null}
     * @param detail the raw {@code 3/} detail; may be {@code null} or blank
     */
    private static void applyCountryAndTown(PostalAddress24 address, String detail) {
        String value = StringUtils.trimToNull(detail);
        if (value == null) {
            return;
        }
        int separator = value.indexOf('/');
        if (separator < 0) {
            // No separator present: defensively treat the entire token as the town name.
            address.setTwnNm(value);
            return;
        }
        String country = StringUtils.trimToNull(value.substring(0, separator));
        String town = StringUtils.trimToNull(value.substring(separator + 1));
        if (country != null) {
            address.setCtry(country);
        }
        if (town != null) {
            address.setTwnNm(town);
        }
    }

    /**
     * Returns the detail portion of a numbered line ({@code N/detail}), or the trimmed line as-is when it
     * carries no numbered prefix.
     *
     * @param line the raw line; may be {@code null}
     * @return the stripped detail, the trimmed line, or {@code null} when {@code line} is {@code null}
     */
    private static String stripNumberPrefix(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        Matcher matcher = NUMBERED_LINE.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(2).trim();
        }
        return trimmed;
    }

    /**
     * Indicates whether the given line is an account/party identifier line, that is, a non-blank line whose
     * first non-whitespace character is {@code /} (for example {@code /12345678}).
     *
     * @param line the raw line; may be {@code null}
     * @return {@code true} when the trimmed line starts with {@code /}; {@code false} otherwise
     */
    private static boolean isPartyIdentifierLine(String line) {
        if (StringUtils.isBlank(line)) {
            return false;
        }
        return line.trim().startsWith("/");
    }
}
