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

import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * End-to-end CBPR+ SR2026 validation coverage for pacs.009.001.08 (Financial Institution Credit
 * Transfer) driven through the public {@link CbprValidator#validate(com.prowidesoftware.swift.model.mx.AbstractMX)}
 * entry point.
 *
 * <p>Each of the 7 pacs.009 rule applications from the authoritative CBPR+ inventory is exercised: a
 * fully-compliant fixture must pass with no findings, and there is at least one negative fixture per
 * rule. The five {@link Severity#ERROR}-severity rules each render the message
 * {@link ValidationResult#isValid() invalid}; the two {@link Severity#WARNING}-severity rules
 * ({@code fi-party-bicfi-preferred} and {@code agent-national-clearing-code-only}) instead surface a
 * finding while leaving the message <em>valid</em> &mdash; this ERROR-versus-WARNING distinction is
 * the crux of this class, because {@link ValidationResult#isValid()} is {@code true} whenever no
 * {@code ERROR}-severity finding is present.
 *
 * <p>pacs.009 parties are financial institutions: an agent identified by BICFI only carries no postal
 * address and is therefore exempt from the structured-address hero rule. Consistent with the module
 * testing convention, real fixtures are parsed into real generated {@link MxPacs00900108} objects (no
 * mocks) and asserted through the validator rather than by invoking rule classes directly.
 */
class Pacs009ValidationTest {

    /** Compliant BIC-only pacs.009 passes every rule: valid with no findings. */
    @Test
    void structuredAddressIsCompliant() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_structured_address.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFindings()).isEmpty();
    }

    /** A creditor FI whose postal address carries AdrLine only (no TwnNm/Ctry) fails the hero rule. */
    @Test
    void structuredAddressMinTownCountryFires() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_unstructured_address_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "structured-address-min-town-country");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /** A malformed BICFI ({@code INVALID}) on an intermediary agent fails the ISO 9362 format rule. */
    @Test
    void bicfiFormatFires() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_bicfi_format_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "bicfi-format");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /** InstdAgt present in both GrpHdr and the transaction violates R6. */
    @Test
    void instructedAgentGroupVsTxFires() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_instd_agt_group_vs_tx_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "instructed-agent-group-vs-tx");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /** Absent IntrBkSttlmDt at both group and transaction level violates R11. */
    @Test
    void interbankSettlementDatePresenceFires() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_intrbksttlmdt_missing_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "interbank-settlement-date-presence");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /** A debtor FI name carrying a non-FIN-X character ({@code JOSÉ BANCO}) fails the charset rule. */
    @Test
    void charsetFinxExtendedFires() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_charset_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "charset-finx-extended-for-name-address");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.isValid()).isFalse();
    }

    /**
     * An FI identified by clearing-code + name + structured address instead of a BICFI is a soft
     * preference violation: the WARNING is reported but the message stays valid.
     */
    @Test
    void fiPartyBicfiPreferredIsWarningAndStaysValid() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_fi_party_bicfi_preferred_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "fi-party-bicfi-preferred");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(result.isValid()).isTrue(); // WARNING does NOT invalidate
    }

    /**
     * When all agents share one country and one uses a clearing code only, the national-clearing-code
     * preference emits a WARNING (co-firing with {@code fi-party-bicfi-preferred}) yet the message
     * stays valid.
     */
    @Test
    void agentNationalClearingCodeOnlyIsWarningAndStaysValid() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_national_clearing_code_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> f = findingFor(result, "agent-national-clearing-code-only");
        assertThat(f).isPresent();
        assertThat(f.get().getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(result.isValid()).isTrue();
        // This fixture co-fires the other pacs.009 preference warning (also WARNING).
        assertThat(findingFor(result, "fi-party-bicfi-preferred")).isPresent();
    }

    /**
     * Returns the first finding carrying the given rule identifier, if any.
     *
     * @param result the validation result to search
     * @param ruleId the CBPR+ rule identifier (kebab-case) to look for
     * @return the first matching finding, or an empty {@link Optional} when none is present
     */
    private static Optional<Finding> findingFor(ValidationResult result, String ruleId) {
        return result.getFindings().stream()
                .filter(f -> ruleId.equals(f.getRuleId()))
                .findFirst();
    }
}
