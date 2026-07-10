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

import com.prowidesoftware.swift.cbpr.migration.Mt103ToPacs008;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof of the MT103 &rarr; pacs.008.001.08 address-scoped migration (Capability B), closed
 * back through the CBPR+ SR2026 validator (Capability A).
 *
 * <p>Each test parses a real MT103 FIN fixture, runs it through {@link Mt103ToPacs008#translate(String)}
 * to obtain a <em>partial</em> {@link MxPacs00800108}, and then feeds that migrated message back into
 * {@link CbprValidator#validate(com.prowidesoftware.swift.model.mx.AbstractMX)}. This is the single test
 * that exercises the whole feature in one pass: real MT text in, a migrated MX out, and a CBPR+ verdict
 * on the migrated result.
 *
 * <h2>Why these tests assert only on the hero rule, never on overall validity</h2>
 *
 * <p>The migrators are <strong>address-scoped by design</strong>: they map only the debtor and creditor
 * party identity ({@code Nm}) and postal address ({@code PstlAdr}); agents, amounts, settlement dates,
 * remittance information and references are intentionally left {@code null}. A migrated message is
 * therefore <em>partial</em> and legitimately carries other by-design findings, so
 * {@link ValidationResult#isValid()} is generally {@code false} even for the structured case. These tests
 * consequently assert <strong>specifically on the {@code structured-address-min-town-country} finding</strong>
 * (present/absent and its severity) and <strong>never on overall {@link ValidationResult#isValid() validity}</strong>,
 * per the module's testing convention.
 *
 * <h2>Mandated negative migration</h2>
 *
 * <p>{@link #unstructuredMt103MigrationFailsHeroRule()} is the negative migration required by the feature
 * specification: a message migrated from a fully-unstructured MT source (an MT103 whose beneficiary is a
 * no-letter-option Field&nbsp;59 name-and-address block) must fail the hero rule
 * {@code structured-address-min-town-country} with {@link Severity#ERROR}.
 *
 * <p>The tests use JUnit&nbsp;5 with AssertJ only and no mocking, driving real domain objects against real
 * fixtures.
 */
class Mt103ToPacs008Test {

    /**
     * The CBPR+ hero rule identifier, copied verbatim from the authoritative rule inventory. Both tests
     * key their assertions off this single constant so the rule id is spelled exactly once.
     */
    private static final String STRUCTURED_ADDRESS_RULE = "structured-address-min-town-country";

    /**
     * A structured/hybrid MT103 (Field&nbsp;50F structured debtor + Field&nbsp;59F hybrid creditor) migrates
     * to a pacs.008 whose migrated parties both satisfy the hero rule, so no
     * {@code structured-address-min-town-country} finding is raised.
     */
    @Test
    void structuredMt103MigrationPassesHeroRule() throws IOException {
        MxPacs00800108 mx = new Mt103ToPacs008().translate(Lib.readResource("mt103_structured.txt"));
        assertThat(mx).as("migrated pacs.008 from structured MT103").isNotNull();
        // The migrator must have produced a usable body carrying the migrated debtor/creditor.
        assertThat(mx.getFIToFICstmrCdtTrf())
                .as("migrated FIToFICstmrCdtTrf body")
                .isNotNull();

        ValidationResult result = new CbprValidator().validate(mx);

        // Debtor is Structured (50F: town + country, no address line) and creditor is Hybrid
        // (59F: town + country + one address line); the hero rule must NOT fire for either.
        assertThat(findingFor(result, STRUCTURED_ADDRESS_RULE))
                .as("hero rule must be absent when both migrated parties are structured/hybrid")
                .isNotPresent();
    }

    /**
     * MANDATED negative migration: an MT103 whose beneficiary is a fully-unstructured Field&nbsp;59
     * name-and-address block migrates to a pacs.008 whose creditor has an address-line-only
     * {@code PstlAdr}, which fails the hero rule {@code structured-address-min-town-country} with
     * {@link Severity#ERROR}. The structured Field&nbsp;50F debtor still passes.
     */
    @Test
    void unstructuredMt103MigrationFailsHeroRule() throws IOException {
        MxPacs00800108 mx = new Mt103ToPacs008().translate(Lib.readResource("mt103_unstructured.txt"));
        assertThat(mx).as("migrated pacs.008 from unstructured MT103").isNotNull();

        ValidationResult result = new CbprValidator().validate(mx);

        Optional<Finding> hero = findingFor(result, STRUCTURED_ADDRESS_RULE);
        // The hero rule fired: the unstructured (Field 59) creditor address is not permitted under SR2026.
        assertThat(hero)
                .as("hero rule must fire for the unstructured (Field 59) creditor address")
                .isPresent();
        assertThat(hero.get().getSeverity()).as("hero rule severity").isEqualTo(Severity.ERROR);
        // It is the creditor (Field 59, unstructured) that failed while the structured 50F debtor passed;
        // the pacs.008 StructuredAddressRule element path names the offending party.
        assertThat(hero.get().getElementPath())
                .as("offending element path should identify the creditor")
                .contains("Cdtr");
        // Note: overall isValid() is intentionally NOT asserted — the partial migrated message carries
        // other by-design findings (absent agents, amounts, dates, etc.).
    }

    /**
     * Migration itself never throws for a well-formed MT103 source, consistent with the module's
     * never-throw contract for the end-to-end migrate-then-validate flow.
     */
    @Test
    void migrationDoesNotThrow() throws IOException {
        String fin = Lib.readResource("mt103_structured.txt");
        assertThatCode(() -> new Mt103ToPacs008().translate(fin)).doesNotThrowAnyException();
    }

    /**
     * Returns the first {@link Finding} carrying the given rule identifier, if any.
     *
     * <p>The lookup is null-safe on both sides: {@code null} findings are skipped and the supplied
     * (non-{@code null}) {@code ruleId} literal drives the comparison, so a finding whose own
     * {@code ruleId} is {@code null} never matches.
     *
     * @param result the validation result to search; never {@code null}
     * @param ruleId the CBPR+ rule identifier to look for (kebab-case); never {@code null}
     * @return an {@link Optional} holding the first matching finding, or empty when none matches
     */
    private static Optional<Finding> findingFor(ValidationResult result, String ruleId) {
        return result.getFindings().stream()
                .filter(f -> f != null && ruleId.equals(f.getRuleId()))
                .findFirst();
    }
}
