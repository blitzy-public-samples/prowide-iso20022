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

import com.prowidesoftware.swift.cbpr.migration.Mt202ToPacs009;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage of the <strong>MT202 &rarr; pacs.009.001.08</strong> migration flow
 * (Capability B), which is the deliberate <strong>BIC-only / BIC-exempt</strong> counter-example to
 * the MT103 flow.
 *
 * <p>In an MT202 the beneficiary institution is identified <em>by BIC only</em> (field {@code 58A})
 * and therefore carries <strong>no postal address</strong>. {@link Mt202ToPacs009} maps that BIC
 * onto the creditor financial institution ({@code CdtTrfTxInf/Cdtr/FinInstnId/BICFI}) of a partial
 * {@link MxPacs00900108} and builds no {@code PstlAdr}. Because a BIC-identified party carries no
 * address, the CBPR+ SR2026 hero rule {@code structured-address-min-town-country} is
 * <em>not applicable</em> to this message &mdash; consistent with SWIFT guidance that, for agents,
 * use of the BIC only continues to be a valid alternative to a structured address.
 *
 * <p>The migrated pacs.009 is <strong>partial by design</strong> (only the beneficiary BICFI is
 * mapped; every other branch is {@code null}), so these tests deliberately do <strong>not</strong>
 * assert on the overall {@link ValidationResult#isValid() validity} of the message. Instead they
 * assert on the <em>presence of specific findings</em>: the hero rule must be absent (the BIC-exempt
 * path), and the migrated BICFI must pass the {@code bicfi-format} rule. Pairing the two proves both
 * that the exempt path is exercised and that the identifier actually carried across is well-formed.
 *
 * <p>The fixture {@code mt202.txt} carries {@code :58A:IRVTUS3NXXX}, a valid 11-character ISO 9362
 * BIC. Tests use JUnit&nbsp;5 and AssertJ only, with no mocking, exercising the real migrator,
 * validator and generated model in a single JVM.
 *
 * @see Mt202ToPacs009
 * @see CbprValidator
 * @see ValidationResult
 */
class Mt202ToPacs009Test {

    /**
     * The MT202 beneficiary institution is identified by BIC only and carries no postal address, so
     * the hero rule {@code structured-address-min-town-country} is not applicable (BIC-only parties
     * are exempt) and must not fire against the migrated pacs.009.
     */
    @Test
    void bicOnlyMt202MigrationHasNoStructuredAddressFinding() throws IOException {
        MxPacs00900108 mx = new Mt202ToPacs009().translate(Lib.readResource("mt202.txt"));
        assertThat(mx).isNotNull();

        ValidationResult result = new CbprValidator().validate(mx);

        // Beneficiary FI identified by BIC only, no PstlAdr -> hero rule is not applicable (exempt).
        assertThat(findingFor(result, "structured-address-min-town-country")).isNotPresent();
    }

    /**
     * The migrated beneficiary {@code BICFI} ({@code IRVTUS3NXXX}) is a well-formed 11-character ISO
     * 9362 code, so the {@code bicfi-format} rule must not fire. The deep-getter assertion confirms the
     * BIC was actually mapped, so the absence of the finding is meaningful rather than vacuous (an
     * empty migration would also carry no {@code bicfi-format} finding).
     */
    @Test
    void migratedBicfiIsValidFormat() throws IOException {
        MxPacs00900108 mx = new Mt202ToPacs009().translate(Lib.readResource("mt202.txt"));

        ValidationResult result = new CbprValidator().validate(mx);

        // IRVTUS3NXXX is a valid 11-char ISO 9362 BIC -> bicfi-format must NOT fire.
        assertThat(findingFor(result, "bicfi-format")).isNotPresent();

        // Confirm the migration actually mapped the beneficiary BICFI onto the creditor FI, so the
        // absence of a bicfi-format finding above is a genuine pass and not a vacuous one.
        assertThat(mx.getFICdtTrf()
                        .getCdtTrfTxInf()
                        .get(0)
                        .getCdtr()
                        .getFinInstnId()
                        .getBICFI())
                .isEqualTo("IRVTUS3NXXX");
    }

    /**
     * Returns the first finding carrying the supplied rule identifier, or an empty {@link Optional}
     * when the result contains no such finding.
     *
     * @param result the validation result to search; never {@code null}
     * @param ruleId the CBPR+ rule identifier to look for (kebab-case)
     * @return the first matching {@link Finding}, or {@link Optional#empty()} when none is present
     */
    private static Optional<Finding> findingFor(ValidationResult result, String ruleId) {
        return result.getFindings().stream()
                .filter(f -> f != null && ruleId.equals(f.getRuleId()))
                .findFirst();
    }
}
