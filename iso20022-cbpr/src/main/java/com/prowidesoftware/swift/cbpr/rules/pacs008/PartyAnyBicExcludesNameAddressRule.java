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
package com.prowidesoftware.swift.cbpr.rules.pacs008;

import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.OrganisationIdentification29;
import com.prowidesoftware.swift.model.mx.dic.Party38Choice;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule <strong>{@code party-anybic-excludes-name-address}</strong>
 * for <strong>pacs.008.001.08</strong> (FI-to-FI Customer Credit Transfer).
 *
 * <p><strong>Rule (verbatim, severity {@link Severity#ERROR}):</strong> <em>"If AnyBIC is present on
 * Dbtr/Cdtr, Name and PostalAddress must not be present."</em>
 *
 * <p>Under the CBPR+ AnyBIC-exclusivity constraint, a party identified by an {@code AnyBIC}
 * (organisation BIC) is fully identified by that BIC alone; carrying an additional {@code Nm} (Name)
 * or {@code PstlAdr} (PostalAddress) on the same party is redundant and disallowed. This rule
 * therefore fires whenever a party declares an {@code AnyBIC} <em>and</em> also carries a Name and/or
 * a PostalAddress.
 *
 * <p>This rule is the logical inverse of the {@code creditor-name-mandatory-when-no-anybic} rule:
 * where that rule mandates a Name when {@code AnyBIC} is <em>absent</em>, this rule forbids Name and
 * PostalAddress when {@code AnyBIC} is <em>present</em>. Both navigate the same
 * {@code getId().getOrgId().getAnyBIC()} chain, with every hop null-guarded.
 *
 * <h2>Scope</h2>
 *
 * <p>The rule is applied to the {@code Dbtr} (Debtor) and {@code Cdtr} (Creditor) parties &mdash; both
 * {@link PartyIdentification135} &mdash; of every {@link CreditTransferTransaction39} in the message.
 * When {@code AnyBIC} is absent on a party, the rule does not fire: identification by Name is governed
 * by other rules (for example {@code creditor-name-mandatory-when-no-anybic} and
 * {@code party-name-mandatory-when-address-present}).
 *
 * <h2>Detection semantics</h2>
 *
 * <ul>
 *   <li><strong>AnyBIC presence</strong> is tested with {@link StringUtils#isNotBlank(CharSequence)}
 *       (a blank or {@code null} BIC means the rule does not apply).
 *   <li><strong>Name presence</strong> is tested with {@link StringUtils#isNotBlank(CharSequence)} on
 *       {@code PstlAdr}-independent {@code Nm}.
 *   <li><strong>PostalAddress presence</strong> is tested by object identity ({@code getPstlAdr() !=
 *       null}): any postal-address element, however sparse, counts as present.
 * </ul>
 *
 * <p>At most <strong>one</strong> {@link Finding} is emitted per offending party; its message lists
 * the offending element(s) ({@code Nm}, {@code PstlAdr}, or {@code Nm+PstlAdr}). This keeps the output
 * deterministic and easy to assert on in tests.
 *
 * <h2>Resilience</h2>
 *
 * <p>Consistent with the {@link CbprRule} contract, {@link #check(MxPacs00800108)} <strong>never
 * throws</strong>: every navigation hop (message root, credit-transfer container, transaction list,
 * each transaction, each party, and the {@code Id/OrgId/AnyBIC} chain) is null-guarded, and a
 * {@code null} or partial message simply yields fewer (or no) findings. An empty returned list means
 * the message satisfies this rule.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPacs00800108
 * @since 10.3.14
 */
public final class PartyAnyBicExcludesNameAddressRule implements CbprRule<MxPacs00800108> {

    /** The authoritative CBPR+ rule identifier, taken verbatim from the rule inventory. */
    private static final String RULE_ID = "party-anybic-excludes-name-address";

    /**
     * Evaluates the {@code party-anybic-excludes-name-address} rule against the supplied
     * pacs.008.001.08 message.
     *
     * <p>For each {@link CreditTransferTransaction39} in
     * {@code FIToFICstmrCdtTrf/CdtTrfTxInf}, the {@code Dbtr} and {@code Cdtr} parties are inspected:
     * when a party carries an {@code AnyBIC}, its {@code Nm} and {@code PstlAdr} must both be absent.
     * A violation produces a single {@link Severity#ERROR} finding naming the offending element(s).
     *
     * @param message the parsed pacs.008.001.08 message; may be {@code null} or partial
     * @return the list of findings, empty when the message satisfies this rule; never {@code null}
     */
    @Override
    public List<Finding> check(MxPacs00800108 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }
        FIToFICustomerCreditTransferV08 cdtTrf = message.getFIToFICstmrCdtTrf();
        if (cdtTrf == null) {
            return findings;
        }
        List<CreditTransferTransaction39> transactions = cdtTrf.getCdtTrfTxInf();
        if (transactions == null) {
            return findings;
        }
        for (int i = 0; i < transactions.size(); i++) {
            CreditTransferTransaction39 tx = transactions.get(i);
            if (tx == null) {
                continue;
            }
            String base = "FIToFICstmrCdtTrf/CdtTrfTxInf[" + i + "]";
            checkParty(tx.getDbtr(), base + "/Dbtr", findings);
            checkParty(tx.getCdtr(), base + "/Cdtr", findings);
        }
        return findings;
    }

    /**
     * Applies the AnyBIC-exclusivity check to a single party and appends a finding when it is
     * violated.
     *
     * <p>The rule fires only when the party carries a non-blank {@code AnyBIC}. In that case, the
     * presence of either a non-blank {@code Nm} or a non-{@code null} {@code PstlAdr} constitutes a
     * violation, and a single {@link Severity#ERROR} finding is recorded whose message names the
     * offending element(s). When {@code AnyBIC} is absent, the method returns without emitting
     * anything, because identification by Name is governed by other rules.
     *
     * @param party the party to inspect ({@code Dbtr} or {@code Cdtr}); may be {@code null}
     * @param path the human-readable element path of this party, used in any emitted finding
     * @param findings the accumulator to which a finding is appended on violation; never {@code null}
     */
    private void checkParty(PartyIdentification135 party, String path, List<Finding> findings) {
        if (party == null) {
            return;
        }
        // AnyBIC absent -> this rule does not apply (identification-by-name governed elsewhere).
        if (StringUtils.isBlank(anyBic(party))) {
            return;
        }
        boolean nmPresent = StringUtils.isNotBlank(party.getNm());
        boolean adrPresent = party.getPstlAdr() != null;
        if (nmPresent || adrPresent) {
            String offending =
                    (nmPresent ? "Nm" : "") + (nmPresent && adrPresent ? "+" : "") + (adrPresent ? "PstlAdr" : "");
            String message =
                    "AnyBIC is present so Name and PostalAddress must be absent, but present: " + offending + ".";
            findings.add(new Finding(RULE_ID, Severity.ERROR, path, message));
        }
    }

    /**
     * Null-safely extracts a party's organisation {@code AnyBIC} by walking the
     * {@code Id -> OrgId -> AnyBIC} chain.
     *
     * <p>Every hop is guarded, so a missing {@code Id}, {@code OrgId} or {@code AnyBIC} yields
     * {@code null} rather than throwing. A returned {@code null} (or blank) value indicates the party
     * is not identified by an organisation BIC.
     *
     * @param party the party whose {@code AnyBIC} is sought; may be {@code null}
     * @return the {@code AnyBIC} string, or {@code null} if any hop in the chain is absent
     */
    private String anyBic(PartyIdentification135 party) {
        if (party == null) {
            return null;
        }
        Party38Choice id = party.getId();
        if (id == null) {
            return null;
        }
        OrganisationIdentification29 orgId = id.getOrgId();
        if (orgId == null) {
            return null;
        }
        return orgId.getAnyBIC();
    }
}
