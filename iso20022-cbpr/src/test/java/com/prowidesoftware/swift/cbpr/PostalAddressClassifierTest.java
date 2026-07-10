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

import com.prowidesoftware.swift.cbpr.PostalAddressClassifier.AddressType;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostalAddressClassifier}, the shared engine behind the CBPR+ hero rule
 * {@code structured-address-min-town-country} (Algorithm A3, SWIFT Standards Release 2026
 * structured-address mandate effective 14 November 2026).
 *
 * <p>Each test constructs a {@link PostalAddress24} entirely in code (no fixture files) and pins
 * down one row of the A3 truth table over the triple (town name, country, address line):
 *
 * <ul>
 *   <li>(present, present, absent) &rarr; {@link AddressType#STRUCTURED} (passes / compliant)</li>
 *   <li>(present, present, present) &rarr; {@link AddressType#HYBRID} (passes / compliant)</li>
 *   <li>any address that is missing town or country, or otherwise fails the town+country minimum,
 *       &rarr; {@link AddressType#UNSTRUCTURED} (fails / non-compliant)</li>
 *   <li>a {@code null} address &rarr; {@link AddressType#UNSTRUCTURED} without throwing</li>
 * </ul>
 *
 * <p>Two subtle edge cases are asserted explicitly to guard against regressions: a blank-only
 * address line must not flip a structured address to hybrid, and a blank (whitespace-only) town or
 * country must count as absent.
 */
class PostalAddressClassifierTest {

    /** (Twn, Ctry, no AdrLine) is a fully structured address that passes the hero rule. */
    @Test
    void structuredAddressPasses() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("London");
        a.setCtry("GB");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.STRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isTrue();
    }

    /** (Twn, Ctry, one AdrLine) is a hybrid address that passes the hero rule. */
    @Test
    void hybridAddressPasses() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("Buenos Aires");
        a.setCtry("AR");
        a.getAdrLine().add("Av. Corrientes 1234");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.HYBRID);
        assertThat(PostalAddressClassifier.isCompliant(a)).isTrue();
    }

    /** (Twn, Ctry, several AdrLines) is still hybrid: multiple address lines also pass. */
    @Test
    void hybridWithMultipleAdrLinesPasses() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("Zurich");
        a.setCtry("CH");
        a.getAdrLine().add("Bahnhofstrasse 1");
        a.getAdrLine().add("Floor 3");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.HYBRID);
        assertThat(PostalAddressClassifier.isCompliant(a)).isTrue();
    }

    /** A blank-only address line does not count, so a Twn+Ctry address stays structured. */
    @Test
    void structuredWithOnlyBlankAdrLineStillStructured() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("Madrid");
        a.setCtry("ES");
        a.getAdrLine().add("   ");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.STRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isTrue();
    }

    /** An address line with neither town nor country is fully unstructured and fails. */
    @Test
    void adrLineOnlyIsUnstructured() {
        PostalAddress24 a = new PostalAddress24();
        a.getAdrLine().add("123 Main Road");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** Town present but country missing (with an address line) fails the town+country minimum. */
    @Test
    void missingCountryIsUnstructured() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("Paris");
        a.getAdrLine().add("10 Rue X");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** Country present but town missing (with an address line) fails the town+country minimum. */
    @Test
    void missingTownIsUnstructured() {
        PostalAddress24 a = new PostalAddress24();
        a.setCtry("FR");
        a.getAdrLine().add("10 Rue X");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** A whitespace-only town counts as absent, so a town-blank address is unstructured. */
    @Test
    void blankTownCountsAsAbsent() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("   ");
        a.setCtry("US");
        a.getAdrLine().add("5th Ave");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** An empty address with nothing set is unstructured. */
    @Test
    void emptyAddressIsUnstructured() {
        PostalAddress24 a = new PostalAddress24();

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** Town only (no country and no address line) does not meet the town+country minimum. */
    @Test
    void townAndCountryMissingAdrLineAbsentIsUnstructured() {
        PostalAddress24 a = new PostalAddress24();
        a.setTwnNm("Rome");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** Country only (no town and no address line) does not meet the town+country minimum. */
    @Test
    void countryOnlyIsUnstructured() {
        PostalAddress24 a = new PostalAddress24();
        a.setCtry("IT");

        assertThat(PostalAddressClassifier.classify(a)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(a)).isFalse();
    }

    /** A null address is treated defensively as unstructured and must never throw. */
    @Test
    void nullAddressIsUnstructuredAndDoesNotThrow() {
        assertThatCode(() -> PostalAddressClassifier.classify(null)).doesNotThrowAnyException();
        assertThat(PostalAddressClassifier.classify(null)).isEqualTo(AddressType.UNSTRUCTURED);
        assertThat(PostalAddressClassifier.isCompliant(null)).isFalse();
    }
}
