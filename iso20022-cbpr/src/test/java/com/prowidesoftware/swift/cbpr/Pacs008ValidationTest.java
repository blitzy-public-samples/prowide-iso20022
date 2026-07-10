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

import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of the CBPR+ Standards Release 2026 (SR2026) rule set for
 * <strong>pacs.008.001.08</strong> (FI-to-FI Customer Credit Transfer), exercised entirely through
 * the public {@link CbprValidator#validate(com.prowidesoftware.swift.model.mx.AbstractMX) validate}
 * entry point.
 *
 * <p>The authoritative CBPR+ inventory defines exactly nine rules for pacs.008.001.08, every one of
 * them {@link Severity#ERROR}-severity. This class proves each of the nine end-to-end and confirms
 * the two compliant shapes both pass:
 *
 * <ol>
 *   <li>{@code structured-address-min-town-country} &mdash; the hero rule; a fully-unstructured
 *       postal address (AdrLine only, no TwnNm/Ctry) is rejected while both a fully-structured and a
 *       hybrid address pass;
 *   <li>{@code party-name-mandatory-when-address-present};
 *   <li>{@code creditor-name-mandatory-when-no-anybic};
 *   <li>{@code party-anybic-excludes-name-address};
 *   <li>{@code bicfi-format};
 *   <li>{@code agent-point-to-point-bicfi-mandatory};
 *   <li>{@code charset-finx-extended-for-name-address};
 *   <li>{@code instructed-agent-group-vs-tx} (R6);
 *   <li>{@code interbank-settlement-date-presence} (R11).
 * </ol>
 *
 * <p>Every negative fixture is a <em>single perturbation</em> of the clean, compliant
 * {@code pacs008_structured_address.xml}, so each one triggers exactly its one target rule and nothing
 * else. Accordingly every negative test asserts three facts &mdash; the specific rule fired with
 * {@link Severity#ERROR} severity, the overall result is {@link ValidationResult#isValid() invalid},
 * and exactly one finding was produced (pinning the single-perturbation design). Assertions are routed
 * exclusively through the validator and matched on {@link Finding#getRuleId()} using the rule-id
 * strings verbatim from the CBPR+ inventory; the rule classes themselves are never imported (their
 * simple names collide across the per-message subpackages).
 *
 * <p>Uses JUnit 5 and AssertJ only, with no mocking: real {@link MxPacs00800108} messages parsed from
 * real fixtures are driven through the real validator in a single JVM.
 */
class Pacs008ValidationTest {

    /**
     * A clean, fully-structured message (every party/agent postal address carries {@code TwnNm} and
     * {@code Ctry} with no {@code AdrLine}) passes every pacs.008 rule with no findings.
     */
    @Test
    void structuredAddressIsCompliant() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_structured_address.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFindings()).isEmpty();
    }

    /**
     * A hybrid postal address ({@code TwnNm} and {@code Ctry} present <em>plus</em> one or more
     * {@code AdrLine}) still satisfies the hero {@code structured-address-min-town-country} rule, so
     * the message remains compliant with no findings.
     */
    @Test
    void hybridAddressIsCompliant() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_hybrid_address.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getFindings()).isEmpty();
    }

    /**
     * Rule {@code structured-address-min-town-country} (hero rule): a fully-unstructured creditor
     * address ({@code AdrLine} only, with {@code TwnNm}/{@code Ctry} missing) is rejected, rendering
     * the message invalid.
     */
    @Test
    void unstructuredAddressFailsHeroRule() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_unstructured_address.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "structured-address-min-town-country");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code party-name-mandatory-when-address-present}: a party carrying a postal address but no
     * {@code Nm} is rejected.
     */
    @Test
    void partyNameMandatoryWhenAddressPresentFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_party_name_missing_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "party-name-mandatory-when-address-present");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code creditor-name-mandatory-when-no-anybic}: when the creditor has no
     * {@code Id/OrgId/AnyBIC}, a missing creditor {@code Nm} is rejected.
     */
    @Test
    void creditorNameMandatoryWhenNoAnyBicFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_creditor_name_no_anybic_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "creditor-name-mandatory-when-no-anybic");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code party-anybic-excludes-name-address}: when a party carries an {@code AnyBIC}, the
     * simultaneous presence of {@code Nm} and a postal address is rejected.
     */
    @Test
    void partyAnyBicExcludesNameAddressFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_anybic_with_name_address_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "party-anybic-excludes-name-address");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code bicfi-format}: an agent {@code FinInstnId/BICFI} that does not match the ISO 9362
     * pattern (here {@code INVALID}) is rejected.
     */
    @Test
    void bicfiFormatFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_bicfi_format_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "bicfi-format");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code agent-point-to-point-bicfi-mandatory}: a missing instructing/instructed-agent
     * {@code FinInstnId/BICFI} is rejected.
     */
    @Test
    void agentPointToPointBicfiMandatoryFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_agent_bicfi_mandatory_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "agent-point-to-point-bicfi-mandatory");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code charset-finx-extended-for-name-address}: a party {@code Nm} containing characters
     * outside the FIN-X extended set (here accented letters in {@code JOSÉ GARCÍA}) is rejected.
     */
    @Test
    void charsetFinxExtendedFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_charset_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "charset-finx-extended-for-name-address");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code instructed-agent-group-vs-tx} (R6): when {@code GrpHdr/InstdAgt} is present, a
     * transaction-level {@code InstdAgt} must not also be present; carrying both is rejected.
     */
    @Test
    void instructedAgentGroupVsTxFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_instd_agt_group_vs_tx_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "instructed-agent-group-vs-tx");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Rule {@code interbank-settlement-date-presence} (R11): when {@code GrpHdr/IntrBkSttlmDt} is
     * absent, each transaction must carry its own {@code IntrBkSttlmDt}; omitting both is rejected.
     */
    @Test
    void interbankSettlementDatePresenceFires() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_intrbksttlmdt_missing_negative.xml"));

        ValidationResult result = new CbprValidator().validate(mx);

        assertThat(result.isValid()).isFalse();
        Optional<Finding> finding = findingFor(result, "interbank-settlement-date-presence");
        assertThat(finding).isPresent();
        assertThat(finding.get().getSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.getFindings()).hasSize(1);
    }

    /**
     * Returns the first finding carrying the given rule identifier, if any.
     *
     * <p>All assertions in this class flow through the validator and are matched on
     * {@link Finding#getRuleId()}, so this helper isolates the finding for one rule id without
     * depending on the (never-imported) concrete rule classes.
     *
     * @param result the validation result to search; never {@code null}
     * @param ruleId the CBPR+ rule identifier (kebab-case) to match verbatim
     * @return the first matching finding, or {@link Optional#empty()} when the rule did not fire
     */
    private static Optional<Finding> findingFor(ValidationResult result, String ruleId) {
        return result.getFindings().stream()
                .filter(f -> f.getRuleId().equals(ruleId))
                .findFirst();
    }
}
