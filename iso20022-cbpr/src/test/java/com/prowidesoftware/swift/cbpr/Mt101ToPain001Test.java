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

import com.prowidesoftware.swift.cbpr.migration.Mt101ToPain001;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for the <strong>MT101&rarr;pain.001.001.09</strong> address-scoped migration
 * (Capability B of the {@code iso20022-cbpr} module) fed back through {@link CbprValidator} (Capability A).
 *
 * <p>An MT101 (Request for Transfer, FIN) carries its ordering customer and beneficiary in the repetitive
 * Sequence B, so {@link Mt101ToPain001} maps the <em>first</em> present ordering-customer option
 * ({@code :50F:}/{@code :50H:}/{@code :50G:}) onto {@code PmtInf/Dbtr} and the first present beneficiary option
 * ({@code :59F:}/{@code :59:}/{@code :59A:}) onto the transaction {@code Cdtr}, migrating only the party
 * {@code Nm} and {@code PstlAdr} (plus the accompanying account identifier). The resulting
 * {@link MxPain00100109} is <strong>partial by design</strong>: the group header, initiating party, payment
 * method, amounts, agents, dates, remittance and references are all left {@code null}.
 *
 * <p>Because the migrated message is partial it will generally carry other, by-design findings and
 * {@link ValidationResult#isValid()} will typically be {@code false}. Per the module convention (AAP
 * &sect;0.6.1) these tests therefore assert <strong>specifically</strong> on the hero-rule finding
 * {@code structured-address-min-town-country} and <strong>never</strong> on overall validity.
 *
 * <p>The fixture {@code mt101.txt} supplies a structured {@code :50F:} ordering customer
 * (ROBERT&nbsp;BROWN&nbsp;/&nbsp;DE&nbsp;BERLIN &rarr; Structured debtor) and a hybrid {@code :59F:}
 * beneficiary (MARIA&nbsp;WEBER&nbsp;/&nbsp;PARK&nbsp;LANE&nbsp;7&nbsp;/&nbsp;FR&nbsp;PARIS &rarr; Hybrid
 * creditor). Both classifications pass the hero rule, so the expectation is that the
 * {@code structured-address-min-town-country} finding is <em>absent</em> for the migrated message.
 *
 * @see Mt101ToPain001
 * @see CbprValidator
 */
class Mt101ToPain001Test {

    /**
     * The structured {@code :50F:} ordering customer and hybrid {@code :59F:} beneficiary of {@code mt101.txt}
     * migrate to hero-rule-compliant addresses, so validating the migrated pain.001 must produce no
     * {@code structured-address-min-town-country} finding.
     */
    @Test
    void mt101SequenceBMigrationPassesHeroRule() throws IOException {
        MxPain00100109 mx = new Mt101ToPain001().translate(Lib.readResource("mt101.txt"));
        assertThat(mx).isNotNull();
        // The migrator always produces a usable pain.001 body (a single PmtInf carrying the migrated parties).
        assertThat(mx.getCstmrCdtTrfInitn()).isNotNull();

        ValidationResult result = new CbprValidator().validate(mx);

        // Ordering customer is Structured (:50F: DE/BERLIN, no address line) and the beneficiary is Hybrid
        // (:59F: FR/PARIS + PARK LANE 7); both pass, so the hero rule must NOT fire for either party.
        assertThat(findingFor(result, "structured-address-min-town-country")).isNotPresent();
    }

    /** The address-scoped MT101&rarr;pain.001 migration must never throw for a well-formed MT101. */
    @Test
    void migrationDoesNotThrow() {
        assertThatCode(() -> new Mt101ToPain001().translate(Lib.readResource("mt101.txt")))
                .doesNotThrowAnyException();
    }

    /**
     * Returns the first finding carrying the given rule identifier, if any.
     *
     * @param result the validation result to search; never {@code null}
     * @param ruleId the CBPR+ rule identifier to look for (kebab-case), taken verbatim from the rule inventory
     * @return the first matching {@link Finding}, or {@link Optional#empty()} when the rule did not fire
     */
    private static Optional<Finding> findingFor(ValidationResult result, String ruleId) {
        return result.getFindings().stream()
                .filter(f -> f != null && ruleId.equals(f.getRuleId()))
                .findFirst();
    }
}
