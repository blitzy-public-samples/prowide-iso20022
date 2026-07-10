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

import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared, stateless engine for the CBPR+ hero rule {@code structured-address-min-town-country}
 * (SWIFT Standards Release 2026, structured-address mandate effective 14 November 2026).
 *
 * <p>This classifier is the single, authoritative implementation of the structured-address
 * detection algorithm (Algorithm A3). It is reused by the {@code StructuredAddressRule} in all
 * three CBPR+ message subpackages (pacs.008, pacs.009 and pain.001) and across every party and
 * agent postal-address occurrence, so that the Structured / Hybrid / Unstructured decision is made
 * identically everywhere.
 *
 * <p>The algorithm inspects the typed sub-elements of {@link PostalAddress24} directly (the primary
 * A3 approach), consuming the model <b>read-only</b> through exactly three getters:
 * {@link PostalAddress24#getTwnNm()}, {@link PostalAddress24#getCtry()} and
 * {@link PostalAddress24#getAdrLine()}. It deliberately does not use generic {@code MxNode} path
 * probing (the documented A3 alternative).
 *
 * <p><b>Resilience contract:</b> the methods of this class never throw for any input, including a
 * {@code null} address. A {@code null} or otherwise non-compliant address is reported as
 * {@link AddressType#UNSTRUCTURED} rather than raising an exception, matching the module-wide
 * "the validator never throws" behavioural contract.
 *
 * <p><b>BIC-only exemption:</b> agents identified by BIC only carry no postal address and are
 * exempt from this rule. That exemption is a <i>caller</i> responsibility: the rule classes simply
 * do not invoke this classifier when the {@code PstlAdr} element is absent. Should a {@code null}
 * address nevertheless reach {@link #classify(PostalAddress24)}, it is treated defensively as
 * {@link AddressType#UNSTRUCTURED}.
 *
 * @since 1.0
 */
public final class PostalAddressClassifier {

    /**
     * This class only exposes static helpers and must never be instantiated.
     */
    private PostalAddressClassifier() {
        // utility class - no instances
    }

    /**
     * The three postal-address categories defined by the CBPR+ structured-address mandate.
     *
     * <p>{@link #STRUCTURED} and {@link #HYBRID} satisfy (pass) the hero rule; {@link #UNSTRUCTURED}
     * violates (fails) it.
     */
    public enum AddressType {

        /**
         * Town name and country are present with no address line: a fully structured address that
         * passes the hero rule.
         */
        STRUCTURED,

        /**
         * Town name and country are present together with one or more address lines: a hybrid
         * address that passes the hero rule.
         */
        HYBRID,

        /**
         * The address does not meet the structured minimum (town name and country): typically an
         * address-line-only address, but also any address missing the mandatory town or country.
         * Fails the hero rule.
         */
        UNSTRUCTURED
    }

    /**
     * Classifies a {@link PostalAddress24} as {@link AddressType#STRUCTURED},
     * {@link AddressType#HYBRID} or {@link AddressType#UNSTRUCTURED} per the authoritative CBPR+
     * SR2026 definitions:
     *
     * <ul>
     *   <li><b>Structured</b> = {@code TwnNm} and {@code Ctry} present, with no {@code AdrLine}
     *       &rarr; passes.</li>
     *   <li><b>Hybrid</b> = {@code TwnNm} and {@code Ctry} present, with one or more {@code AdrLine}
     *       &rarr; passes.</li>
     *   <li><b>Unstructured</b> = {@code AdrLine} present while {@code TwnNm} or {@code Ctry} is
     *       missing &rarr; fails.</li>
     * </ul>
     *
     * <p>A town name or country consisting only of {@code null}, empty or whitespace text is treated
     * as <i>absent</i> (via {@link StringUtils#isNotBlank(CharSequence)}), so that an empty element
     * such as {@code <TwnNm></TwnNm>} does not wrongly satisfy the mandate. Presence of address
     * lines is tested against the live, lazily-initialised (never {@code null}) list returned by
     * {@link PostalAddress24#getAdrLine()}, requiring at least one non-blank entry.
     *
     * <p>The {@code else} branch intentionally captures both the canonical failure case
     * (address-line-only) and the defensive incomplete cases (town or country missing, empty
     * address, or a {@code null} argument), all of which fail to meet the structured minimum.
     *
     * <p>BIC-only parties/agents are exempt and are filtered out by the calling rule before this
     * method is reached; a {@code null} address is therefore handled defensively and never throws.
     *
     * @param addr the postal address to classify; may be {@code null}
     * @return the {@link AddressType} of {@code addr}; {@link AddressType#UNSTRUCTURED} when
     *     {@code addr} is {@code null} or does not meet the structured minimum
     */
    public static AddressType classify(final PostalAddress24 addr) {
        final boolean hasTwn = addr != null && StringUtils.isNotBlank(addr.getTwnNm());
        final boolean hasCtry = addr != null && StringUtils.isNotBlank(addr.getCtry());
        final boolean hasAdrLine = addr != null && addr.getAdrLine().stream().anyMatch(StringUtils::isNotBlank);

        if (hasTwn && hasCtry && !hasAdrLine) {
            return AddressType.STRUCTURED;
        }
        if (hasTwn && hasCtry && hasAdrLine) {
            return AddressType.HYBRID;
        }
        return AddressType.UNSTRUCTURED;
    }

    /**
     * Convenience predicate indicating whether a postal address satisfies the CBPR+ structured
     * address mandate, i.e. whether it classifies as {@link AddressType#STRUCTURED} or
     * {@link AddressType#HYBRID} (both pass) as opposed to {@link AddressType#UNSTRUCTURED} (fails).
     *
     * <p>This keeps the three {@code StructuredAddressRule} classes thin: they call this method for
     * each non-exempt postal address and raise a finding when it returns {@code false}.
     *
     * @param addr the postal address to test; may be {@code null}
     * @return {@code true} if the address is structured or hybrid; {@code false} if it is
     *     unstructured (including when {@code addr} is {@code null})
     */
    public static boolean isCompliant(final PostalAddress24 addr) {
        return classify(addr) != AddressType.UNSTRUCTURED;
    }
}
