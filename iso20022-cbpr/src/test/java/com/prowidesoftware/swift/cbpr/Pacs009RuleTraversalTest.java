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

import static org.assertj.core.api.Assertions.assertThat;

import com.prowidesoftware.swift.cbpr.rules.pacs009.AgentNationalClearingCodeOnlyRule;
import com.prowidesoftware.swift.cbpr.rules.pacs009.BicfiFormatRule;
import com.prowidesoftware.swift.cbpr.rules.pacs009.CharsetFinxExtendedRule;
import com.prowidesoftware.swift.cbpr.rules.pacs009.FiPartyBicfiPreferredRule;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression tests proving that the pacs.009 rule classes traverse the full financial-institution
 * agent set (including the previous instructing agents {@code PrvsInstgAgt1..3}), sweep the complete
 * {@link com.prowidesoftware.swift.model.mx.dic.PostalAddress24} address surface for charset
 * violations, use a consistent zero-based transaction index in element paths, and only surface the
 * {@code agent-national-clearing-code-only} warning when every collected agent resolves to a country.
 *
 * <p>These tests exercise the exact gaps flagged during the Checkpoint&nbsp;2 review: three pacs.009
 * rules previously omitted {@code PrvsInstgAgt1..3}, the pacs.009 charset rule inspected only a subset
 * of address components, {@code FiPartyBicfiPreferredRule} used a 1-based index inconsistent with the
 * other rules, and {@code AgentNationalClearingCodeOnlyRule} could warn even when an agent's country
 * could not be determined.
 *
 * <p>Consistent with the module testing convention, real fixtures are parsed into real generated
 * {@link MxPacs00900108} objects (no mocks) and driven through the real rule implementations.
 */
class Pacs009RuleTraversalTest {

    /**
     * Parses a pacs.009.001.08 test fixture from the test classpath into a real {@link MxPacs00900108}.
     *
     * @param resource the fixture file name under {@code src/test/resources}
     * @return the parsed message
     * @throws IOException if the fixture cannot be read
     */
    private static MxPacs00900108 parse(String resource) throws IOException {
        return MxPacs00900108.parse(Lib.readResource(resource));
    }

    @Test
    void bicfiFormatRule_flagsMalformedBicfiOnPreviousInstructingAgent() throws IOException {
        MxPacs00900108 mx = parse("pacs009_prvs_instg_agt_negative.xml");

        List<Finding> findings = new BicfiFormatRule().check(mx);

        // The malformed BICFI "INVALID99" (9 chars, not 8/11) sits on PrvsInstgAgt1; before the fix this
        // agent was not traversed and the malformed value was silently accepted.
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.getRuleId()).isEqualTo("bicfi-format");
            assertThat(f.getSeverity()).isEqualTo(Severity.ERROR);
            assertThat(f.getElementPath()).isEqualTo("FICdtTrf/CdtTrfTxInf[0]/PrvsInstgAgt1/FinInstnId/BICFI");
        });
    }

    @Test
    void charsetRule_flagsInvalidCharactersOnPreviousInstructingAgentNameAndAddressComponent()
            throws IOException {
        MxPacs00900108 mx = parse("pacs009_prvs_instg_agt_negative.xml");

        List<Finding> findings = new CharsetFinxExtendedRule().check(mx);

        // PrvsInstgAgt1 must now be traversed: its Name "PRÉV BANK" carries a non-FIN-X character.
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.getRuleId()).isEqualTo("charset-finx-extended-for-name-address");
            assertThat(f.getSeverity()).isEqualTo(Severity.ERROR);
            assertThat(f.getElementPath()).isEqualTo("FICdtTrf/CdtTrfTxInf[0]/PrvsInstgAgt1/FinInstnId/Nm");
        });

        // The full PostalAddress24 sweep must reach StrtNm ("Rüe Neuve"), an address component that was
        // previously omitted by the pacs.009 charset rule.
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.getRuleId()).isEqualTo("charset-finx-extended-for-name-address");
            assertThat(f.getSeverity()).isEqualTo(Severity.ERROR);
            assertThat(f.getElementPath())
                    .isEqualTo("FICdtTrf/CdtTrfTxInf[0]/PrvsInstgAgt1/FinInstnId/PstlAdr/StrtNm");
        });
    }

    @Test
    void fiPartyBicfiPreferredRule_warnsForPreviousInstructingAgentWithoutBicfi_usingZeroBasedIndex()
            throws IOException {
        MxPacs00900108 mx = parse("pacs009_prvs_instg_agt_negative.xml");

        List<Finding> findings = new FiPartyBicfiPreferredRule().check(mx);

        // PrvsInstgAgt2 is identified by a clearing-system member id only (no BICFI), so it must trigger
        // the preference WARNING; and the element path must use the zero-based transaction index [0]
        // consistent with the other pacs.009 rules (previously this rule used a 1-based index).
        assertThat(findings).anySatisfy(f -> {
            assertThat(f.getRuleId()).isEqualTo("fi-party-bicfi-preferred");
            assertThat(f.getSeverity()).isEqualTo(Severity.WARNING);
            assertThat(f.getElementPath()).isEqualTo("FICdtTrf/CdtTrfTxInf[0]/PrvsInstgAgt2/FinInstnId");
        });

        // No warning path may use a 1-based CdtTrfTxInf[1] index for this single-transaction message.
        assertThat(findings).allSatisfy(f -> assertThat(f.getElementPath()).doesNotContain("CdtTrfTxInf[1]"));
    }

    @Test
    void agentNationalClearingCodeOnlyRule_doesNotWarnWhenAnAgentCountryCannotBeDetermined()
            throws IOException {
        MxPacs00900108 mx = parse("pacs009_national_clearing_code_unknown_country_negative.xml");

        List<Finding> findings = new AgentNationalClearingCodeOnlyRule().check(mx);

        // One agent (IntrmyAgt2, clearing-code-only with no BICFI and no postal-address country) has an
        // undeterminable country, so the rule cannot claim that "all agents share one country" and must
        // emit no warning — even though the known countries are all "DE" and a clearing-only agent exists.
        assertThat(findings).isEmpty();
    }

    @Test
    void agentNationalClearingCodeOnlyRule_stillWarnsWhenEveryAgentSharesOneCountry() throws IOException {
        MxPacs00900108 mx = parse("pacs009_national_clearing_code_negative.xml");

        List<Finding> findings = new AgentNationalClearingCodeOnlyRule().check(mx);

        // Regression guard: in the existing fixture every collected agent resolves to "DE" and one agent
        // is clearing-code-only, so exactly one informational WARNING must still be emitted.
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).getRuleId()).isEqualTo("agent-national-clearing-code-only");
        assertThat(findings.get(0).getSeverity()).isEqualTo(Severity.WARNING);
        assertThat(findings.get(0).getElementPath()).isEqualTo("FICdtTrf");
    }
}
