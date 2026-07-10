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
package com.prowidesoftware.swift.cbpr.migration;

import com.prowidesoftware.swift.model.field.Field58A;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.prowidesoftware.swift.model.mt.mt2xx.MT202;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.dic.BranchAndFinancialInstitutionIdentification6;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction36;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.FinancialInstitutionIdentification18;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Address-scoped migrator that translates the beneficiary institution of a legacy MT202
 * (General Financial Institution Transfer) into a partial ISO 20022
 * <strong>pacs.009.001.08</strong> ({@link MxPacs00900108}) Financial Institution Credit Transfer.
 *
 * <p>This class is part of the {@code iso20022-cbpr} module (Capability B &mdash; address-scoped
 * MT&rarr;MX migration). It demonstrates the <strong>BIC-exempt path</strong> of the CBPR+ SR2026
 * structured-address effort: in a pacs.009 message the parties are <em>financial institutions</em>
 * identified by their Business Identifier Code ({@code BICFI}), and such BIC-identified agents
 * <strong>carry no postal address</strong>. Consequently the hero rule
 * {@code structured-address-min-town-country} is <em>not applicable</em> to this flow &mdash;
 * consistent with SWIFT guidance that, for agents, use of the BIC only continues to be a valid
 * option. Because no address is produced, this migrator deliberately does <strong>not</strong>
 * build any {@code PostalAddress24} and does not use the shared address-migration support.</p>
 *
 * <p><strong>Address/party-scoped and partial by design.</strong> Only the beneficiary institution
 * identity (the {@code BICFI} carried by MT field 58A) is migrated, mapped onto the target
 * creditor financial institution ({@code CdtTrfTxInf/Cdtr/FinInstnId/BICFI}). Every other branch of
 * the target message is intentionally left {@code null}: the group header, the debtor institution,
 * all agents (debtor/creditor/instructing/instructed and intermediaries), the interbank settlement
 * amount and date, remittance information and references are <strong>not</strong> mapped. The
 * resulting {@link MxPacs00900108} is therefore a partial message by design.</p>
 *
 * <p><strong>MT-API-bounded.</strong> The source MT is consumed <em>read-only</em> through the
 * public Prowide Core ({@code pw-swift-core}) MT API only ({@link AbstractMT#parse(String)},
 * {@link MT202#getField58A()} and the universal {@link com.prowidesoftware.swift.model.field.Field}
 * accessors). No new MT parser is introduced and no MT API is invented. The target MX object is a
 * brand-new instance populated through the generated fluent setters, which is legitimate output
 * construction and not a mutation of any generated model class.</p>
 *
 * <p><strong>BICFI extraction.</strong> Field 58A option A has the structure
 * {@code [/1!a][/34x]} (optional party-identifier line) followed by the identifier-code (BIC) line
 * {@code 4!a2!a2!c[3!c]}. To avoid fragile assumptions about component indices, the BIC is located
 * with a universal, robust strategy: the field's {@link Field58A#getValue() raw value} is split into
 * lines, blank lines and {@code '/'}-prefixed party-identifier lines are skipped, and the last line
 * matching the ISO 9362 pattern is selected as the {@code BICFI}.</p>
 *
 * <p><strong>Thread-safety.</strong> Instances are stateless and immutable; a single instance may be
 * shared and used concurrently.</p>
 *
 * @see MxPacs00900108
 * @see MT202
 */
public class Mt202ToPacs009 {

    /**
     * ISO 9362 Business Identifier Code (BIC / BICFI) pattern: four alphanumeric institution
     * characters, two alphabetic country characters, two alphanumeric location characters and an
     * optional three alphanumeric branch suffix (i.e. an 8- or 11-character BIC).
     */
    private static final Pattern BIC_PATTERN = Pattern.compile("[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?");

    /** Line separator used to split the raw multi-line value of an MT field (CR/LF or LF). */
    private static final Pattern LINE_SEPARATOR = Pattern.compile("\\r?\\n");

    /**
     * Creates a new, stateless MT202 &rarr; pacs.009.001.08 migrator.
     */
    public Mt202ToPacs009() {
        // No configuration state is required; the migrator is stateless and reusable.
    }

    /**
     * Parses the supplied FIN content as an MT message and migrates the beneficiary institution of
     * the resulting MT202 into a partial {@link MxPacs00900108}.
     *
     * <p>The message is parsed with {@link AbstractMT#parse(String)} and is required to be an MT202.
     * If the content cannot be parsed, or does not correspond to an MT202, an
     * {@link IllegalArgumentException} is raised (this is a caller-contract violation, distinct from
     * message-level CBPR+ validation, which is handled elsewhere and never throws).</p>
     *
     * @param fin the raw FIN (SWIFT MT block structure) content of an MT202 message; must not be
     *     {@code null}
     * @return a partial {@link MxPacs00900108} carrying only the migrated beneficiary institution
     * @throws IllegalArgumentException if {@code fin} is {@code null}, cannot be parsed as an MT
     *     message, or does not represent an MT202
     */
    public MxPacs00900108 translate(final String fin) {
        if (fin == null) {
            throw new IllegalArgumentException("the FIN message content must not be null");
        }
        final AbstractMT abstractMT;
        try {
            abstractMT = AbstractMT.parse(fin);
        } catch (final IOException e) {
            throw new IllegalArgumentException("unable to parse the supplied content as an MT message", e);
        }
        if (!(abstractMT instanceof MT202)) {
            final String actual =
                    abstractMT == null ? "null" : abstractMT.getClass().getSimpleName();
            throw new IllegalArgumentException("expected an MT202 message but received " + actual);
        }
        return translate((MT202) abstractMT);
    }

    /**
     * Migrates the beneficiary institution of the supplied MT202 into a partial
     * {@link MxPacs00900108}.
     *
     * <p>Exactly one {@link CreditTransferTransaction36} is created and its creditor financial
     * institution is populated from MT field 58A (see {@link #translate(String)} for the parsing
     * entry point). No group header, debtor, agent, amount, date, remittance or reference data is
     * mapped &mdash; the returned message is partial by design.</p>
     *
     * @param mt the source MT202 message; must not be {@code null}
     * @return a partial {@link MxPacs00900108} carrying only the migrated beneficiary institution
     * @throws IllegalArgumentException if {@code mt} is {@code null}
     */
    public MxPacs00900108 translate(final MT202 mt) {
        if (mt == null) {
            throw new IllegalArgumentException("the MT202 message must not be null");
        }

        // Build a fresh, partial pacs.009.001.08 body. The group header (GrpHdr) is intentionally
        // left null: only the beneficiary institution identity is in scope for this migration.
        final FinancialInstitutionCreditTransferV08 body = new FinancialInstitutionCreditTransferV08();

        // A single credit-transfer transaction carries the migrated beneficiary institution.
        final CreditTransferTransaction36 tx = new CreditTransferTransaction36();

        // Map ONLY the beneficiary institution identity (Field 58A BICFI) onto the creditor FI.
        final BranchAndFinancialInstitutionIdentification6 creditor = mapBeneficiaryInstitution(mt);
        if (creditor != null) {
            tx.setCdtr(creditor);
        }

        // getCdtTrfTxInf() lazily initialises a non-null, mutable list on the generated model.
        body.getCdtTrfTxInf().add(tx);

        return new MxPacs00900108().setFICdtTrf(body);
    }

    /**
     * Builds the creditor financial institution from MT field 58A, or returns {@code null} when the
     * field is absent or no ISO 9362 BIC can be located. The BIC path carries no postal address, so
     * only {@code FinInstnId/BICFI} is populated.
     *
     * @param mt the source MT202 message
     * @return a {@link BranchAndFinancialInstitutionIdentification6} whose {@code FinInstnId/BICFI}
     *     is set, or {@code null} when there is nothing to migrate
     */
    private BranchAndFinancialInstitutionIdentification6 mapBeneficiaryInstitution(final MT202 mt) {
        final Field58A field58A = mt.getField58A();
        if (field58A == null) {
            // No Field 58A option A present (for example, the message may use option D instead);
            // nothing to migrate on the BIC-only path.
            return null;
        }
        final String bicfi = extractBicFi(field58A);
        if (bicfi == null || bicfi.isEmpty()) {
            // The field is present but no ISO 9362 identifier-code line could be located. Remain
            // defensive and do NOT invent a BIC.
            return null;
        }

        final FinancialInstitutionIdentification18 finInstnId = new FinancialInstitutionIdentification18();
        finInstnId.setBICFI(bicfi);

        final BranchAndFinancialInstitutionIdentification6 creditor =
                new BranchAndFinancialInstitutionIdentification6();
        creditor.setFinInstnId(finInstnId);
        return creditor;
    }

    /**
     * Extracts the ISO 9362 BIC (used as {@code BICFI}) from a Field 58A value using the universal
     * {@link Field58A#getValue() raw value} rather than positional components.
     *
     * <p>The raw value is split into lines; blank lines and {@code '/'}-prefixed party-identifier
     * lines are ignored. The last remaining line that fully matches {@link #BIC_PATTERN} is returned
     * as the BIC. Returns {@code null} when no such line exists.</p>
     *
     * @param field58A the beneficiary institution field (option A); never {@code null}
     * @return the BIC string, or {@code null} if none could be identified
     */
    private String extractBicFi(final Field58A field58A) {
        final String rawValue = field58A.getValue();
        if (rawValue == null) {
            return null;
        }
        String bicfi = null;
        for (final String line : LINE_SEPARATOR.split(rawValue)) {
            if (line == null) {
                continue;
            }
            final String candidate = line.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            // Party-identifier lines are prefixed with '/'; they never carry the BIC.
            if (candidate.charAt(0) == '/') {
                continue;
            }
            // Keep the last line that matches the ISO 9362 BIC pattern (the identifier-code line).
            if (BIC_PATTERN.matcher(candidate).matches()) {
                bicfi = candidate;
            }
        }
        return bicfi;
    }
}
