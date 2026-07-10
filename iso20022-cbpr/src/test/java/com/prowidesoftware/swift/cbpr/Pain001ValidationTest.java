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

import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of the CBPR+ Standards Release 2026 (SR2026) rule set for
 * pain.001.001.09 (Customer Credit Transfer Initiation), exercised exclusively through the
 * public entry point {@link CbprValidator#validate(com.prowidesoftware.swift.model.mx.AbstractMX)}.
 *
 * <p>The five pain.001 rule applications are each proven with at least one negative fixture, plus a
 * single fully-compliant positive fixture:
 *
 * <ol>
 *   <li>{@code structured-address-min-town-country} (ERROR) &mdash; a creditor postal address that
 *       carries only an {@code AdrLine} (no {@code TwnNm}/{@code Ctry}) is fully unstructured and must
 *       be flagged;
 *   <li>{@code party-name-mandatory-when-address-present} (ERROR) &mdash; a debtor with a postal
 *       address but no {@code Nm} must be flagged;
 *   <li>{@code bicfi-format} (ERROR) &mdash; a malformed agent BICFI ({@code INVALID}) must be
 *       flagged;
 *   <li>{@code related-present-when-copydupl} (WARNING) &mdash; a Business Application Header that
 *       carries a CopyDuplicate indicator without an accompanying Related reference must raise a
 *       <em>warning</em> that does <strong>not</strong> invalidate the message;
 *   <li>{@code charset-finx-extended-for-name-address} (ERROR) &mdash; a name carrying characters
 *       outside the FIN-X extended set ({@code JOSÉ GARCÍA}) must be flagged.
 * </ol>
 *
 * <p>Three further negatives prove the full scope of rules&nbsp;2 and&nbsp;5, which cover the
 * transaction-level ultimate debtor and every free-text postal-address component (not only
 * {@code Nm}/{@code TwnNm}/{@code AdrLine}): a <em>transaction-level</em> {@code UltmtDbtr} with a
 * postal address but no {@code Nm} fires {@code party-name-mandatory-when-address-present}; a
 * transaction-level {@code UltmtDbtr} whose {@code Nm} is non-FIN-X fires
 * {@code charset-finx-extended-for-name-address}; and a non-{@code Nm} address component
 * ({@code PstlAdr/StrtNm}) that is non-FIN-X also fires the charset rule.
 *
 * <p>Rule&nbsp;4 in particular demonstrates that the module <em>reuses</em> the existing header
 * handling rather than reimplementing it: the header is parsed as part of
 * {@link MxPain00100109#parse(String)} and inspected by the rule through {@code getAppHdr()}, so the
 * warning fires while overall validity is preserved.
 *
 * <p>Consistent with the module's testing convention, real fixtures are parsed into real generated
 * {@link MxPain00100109} objects (no mocks) and driven through the real {@link CbprValidator}. Every
 * assertion is routed through the validator and the {@link Finding#getRuleId() ruleId} contract; the
 * individual rule classes are never referenced directly.
 */
class Pain001ValidationTest {

    /**
     * A fully-compliant message (structured debtor and creditor addresses, no CopyDuplicate header)
     * must validate cleanly with no findings at all.
     */
    @Test
    void structuredAddressIsCompliant() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_structured_address.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFindings()).isEmpty();
    }

    /**
     * A creditor postal address consisting solely of an {@code AdrLine} (no town/country) is fully
     * unstructured and must fire the hero rule as an ERROR, rendering the message invalid.
     */
    @Test
    void structuredAddressMinTownCountryFires() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_unstructured_address_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "structured-address-min-town-country");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /**
     * A debtor that carries a postal address but no {@code Nm} must fire the
     * name-mandatory-when-address-present rule as an ERROR, rendering the message invalid.
     */
    @Test
    void partyNameMandatoryWhenAddressPresentFires() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_party_name_missing_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "party-name-mandatory-when-address-present");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /**
     * A malformed agent BICFI ({@code INVALID}, which does not match ISO&nbsp;9362) must fire the
     * bicfi-format rule as an ERROR, rendering the message invalid.
     */
    @Test
    void bicfiFormatFires() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_bicfi_format_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "bicfi-format");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /**
     * A name carrying characters outside the FIN-X extended set ({@code JOSÉ GARCÍA}) must fire the
     * charset rule as an ERROR, rendering the message invalid.
     */
    @Test
    void charsetFinxExtendedFires() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_charset_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "charset-finx-extended-for-name-address");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /**
     * A Business Application Header (head.001.001.02) carrying a CopyDuplicate indicator without an
     * accompanying Related reference must fire the R1 rule as a <strong>WARNING</strong>. Because the
     * finding is a warning rather than an error, the overall message remains {@code valid}. This also
     * proves the rule reads the header parsed by {@link MxPain00100109#parse(String)} rather than
     * reparsing it.
     */
    @Test
    void relatedPresentWhenCopyDuplicateIsWarningAndStaysValid() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_related_copydupl_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "related-present-when-copydupl");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(result.isValid()).isTrue(); // WARNING (R1) does NOT invalidate
    }

    /**
     * A <em>transaction-level</em> ultimate debtor ({@code CdtTrfTxInf/UltmtDbtr}) that carries a postal
     * address but no {@code Nm} must fire the name-mandatory-when-address-present rule as an ERROR,
     * rendering the message invalid. This proves the rule covers the transaction-level ultimate debtor in
     * addition to the PaymentInformation-level parties, per the CBPR+ inventory which lists
     * {@code UltmtDbtr} generally for this rule.
     */
    @Test
    void partyNameMandatoryFiresForTransactionUltimateDebtor() throws IOException {
        MxPain00100109 mx =
                MxPain00100109.parse(Lib.readResource("pain001_tx_ultmtdbtr_name_missing_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "party-name-mandatory-when-address-present");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(f.get().getElementPath()).contains("CdtTrfTxInf[0]/UltmtDbtr");
        assertThat(result.isValid()).isFalse();
    }

    /**
     * A <em>transaction-level</em> ultimate debtor ({@code CdtTrfTxInf/UltmtDbtr}) whose {@code Nm} carries
     * characters outside the FIN-X extended set ({@code JOSÉ GARCÍA}) must fire the charset rule as an
     * ERROR, rendering the message invalid. This proves the charset rule traverses the transaction-level
     * ultimate debtor.
     */
    @Test
    void charsetFinxExtendedFiresForTransactionUltimateDebtor() throws IOException {
        MxPain00100109 mx =
                MxPain00100109.parse(Lib.readResource("pain001_tx_ultmtdbtr_charset_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "charset-finx-extended-for-name-address");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(f.get().getElementPath()).contains("CdtTrfTxInf[0]/UltmtDbtr");
        assertThat(result.isValid()).isFalse();
    }

    /**
     * A charset violation in a <em>non-{@code Nm}</em> postal address component &mdash; here the debtor's
     * {@code PstlAdr/StrtNm} ({@code Rue José}) &mdash; must fire the charset rule as an ERROR, rendering
     * the message invalid. This proves the rule sweeps every free-text address component, not only
     * {@code Nm}, {@code TwnNm} and {@code AdrLine}.
     */
    @Test
    void charsetFinxExtendedFiresForNonNameAddressComponent() throws IOException {
        MxPain00100109 mx =
                MxPain00100109.parse(Lib.readResource("pain001_address_component_charset_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "charset-finx-extended-for-name-address");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(f.get().getElementPath()).contains("PstlAdr/StrtNm");
        assertThat(result.isValid()).isFalse();
    }

    /**
     * Returns the first {@link Finding} produced under the given rule identifier, if any.
     *
     * @param result the validation result to search; never {@code null}
     * @param ruleId the CBPR+ rule identifier to look for (kebab-case, exact match)
     * @return the first matching finding, or {@link Optional#empty()} if none was produced
     */
    private static Optional<Finding> findingFor(ValidationResult result, String ruleId) {
        return result.getFindings().stream()
                .filter(f -> ruleId.equals(f.getRuleId()))
                .findFirst();
    }
}
