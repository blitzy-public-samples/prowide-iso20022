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

import com.prowidesoftware.swift.model.field.Field50A;
import com.prowidesoftware.swift.model.field.Field50F;
import com.prowidesoftware.swift.model.field.Field50K;
import com.prowidesoftware.swift.model.field.Field59;
import com.prowidesoftware.swift.model.field.Field59A;
import com.prowidesoftware.swift.model.field.Field59F;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.prowidesoftware.swift.model.mt.mt1xx.MT103;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction39;
import com.prowidesoftware.swift.model.mx.dic.FIToFICustomerCreditTransferV08;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Address-scoped migrator that translates the debtor and creditor of a legacy <strong>MT103</strong>
 * (Single Customer Credit Transfer) into a partial ISO 20022 <strong>pacs.008.001.08</strong>
 * ({@link MxPacs00800108}) FI-to-FI Customer Credit Transfer.
 *
 * <p>This class is part of the {@code iso20022-cbpr} module (Capability B &mdash; address-scoped
 * MT&rarr;MX migration). It demonstrates the customer-party migration path of the CBPR+ SR2026
 * structured-address effort: the ordering customer (debtor) and beneficiary customer (creditor) of a
 * cross-border MT103 are mapped onto the corresponding pacs.008 {@code Dbtr} and {@code Cdtr}
 * parties, carrying only the party <em>name</em> and <em>postal address</em>. The migrated message is
 * later fed through the CBPR+ validator (in tests) to prove structured-address compliance
 * end-to-end.</p>
 *
 * <p><strong>Address/party-scoped and partial by design.</strong> Only the debtor identity (MT field
 * 50A / 50F / 50K) and the creditor identity (MT field 59 / 59A / 59F) &mdash; that is, party
 * {@code Nm} and {@code PstlAdr} &mdash; are migrated onto a single
 * {@link CreditTransferTransaction39}. Every other branch of the target message is intentionally left
 * {@code null}: the group header ({@code GrpHdr}), all agents ({@code InstgAgt} / {@code InstdAgt} /
 * intermediary agents), the interbank settlement amount and date ({@code IntrBkSttlmAmt} /
 * {@code IntrBkSttlmDt}), the charge bearer ({@code ChrgBr}), remittance information ({@code RmtInf}),
 * the payment identification ({@code PmtId}) and all references are <strong>not</strong> mapped. The
 * resulting {@link MxPacs00800108} is therefore a partial message by design; a downstream validator is
 * expected to report other (non structured-address) findings on it, and callers should assert
 * specifically on the structured-address outcome.</p>
 *
 * <p><strong>Structured versus unstructured address handling.</strong> The choice of debtor/creditor
 * option drives the shape of the produced {@code PstlAdr}:</p>
 *
 * <ul>
 *   <li>The structured "F" options (Field 50F / 59F) yield a Structured or Hybrid address (town and
 *       country present, optionally with address lines) via
 *       {@link AddressMigrationSupport#structuredAddressFromFFieldLines(List)}, which passes the CBPR+
 *       hero rule {@code structured-address-min-town-country}.</li>
 *   <li>The unstructured name-and-address options (Field 50K / Field 59 no-option) yield an
 *       address-line-only address (no town or country) via
 *       {@link AddressMigrationSupport#unstructuredAddressFromLines(List)}, which deliberately
 *       <em>fails</em> the hero rule &mdash; this is the intended negative path and must never be
 *       "upgraded".</li>
 *   <li>The BIC-only options (Field 50A / 59A) carry no postal address; the party is left minimal and
 *       no address data is invented.</li>
 * </ul>
 *
 * <p><strong>MT-API-bounded.</strong> The source MT is consumed <em>read-only</em> through the public
 * Prowide Core ({@code pw-swift-core}) MT API only ({@link AbstractMT#parse(String)}, the
 * {@code MT103.getField5x()} single-occurrence accessors and the universal
 * {@link com.prowidesoftware.swift.model.field.Field#getValue()} accessor). No new MT parser is
 * introduced and no MT API is invented. The name-and-address line semantics are delegated to the
 * {@code pw-swift-core}-free {@link AddressMigrationSupport} helper, confining all MT-API usage to this
 * migrator. The target MX object is a brand-new instance populated through the generated fluent
 * setters, which is legitimate output construction and not a mutation of any generated model class.</p>
 *
 * <p><strong>Resilience.</strong> Every field read is null-safe: a debtor/creditor option that is not
 * present returns {@code null} from its accessor and is skipped, so a well-formed MT103 never causes a
 * {@link NullPointerException}. A {@code null}, unparseable, or non-MT103 input is a caller-contract
 * violation and is reported with an {@link IllegalArgumentException} (this is distinct from
 * message-level CBPR+ validation, which is performed elsewhere and never throws).</p>
 *
 * <p><strong>Thread-safety.</strong> Instances are stateless and immutable; a single instance may be
 * shared and used concurrently.</p>
 *
 * @see MxPacs00800108
 * @see MT103
 * @see AddressMigrationSupport
 */
public class Mt103ToPacs008 {

    /**
     * Regular expression used to split the raw multi-line value of an MT field into its individual
     * lines, accepting both CR/LF and LF terminators.
     */
    private static final String LINE_SEPARATOR_REGEX = "\\r?\\n";

    /**
     * Creates a new, stateless MT103 &rarr; pacs.008.001.08 migrator.
     */
    public Mt103ToPacs008() {
        // No configuration state is required; the migrator is stateless and reusable.
    }

    /**
     * Parses the supplied FIN content as an MT message and migrates the debtor and creditor of the
     * resulting MT103 into a partial {@link MxPacs00800108}.
     *
     * <p>The message is parsed with {@link AbstractMT#parse(String)} and is required to be an MT103.
     * If the content is {@code null}, cannot be parsed, or does not correspond to an MT103, an
     * {@link IllegalArgumentException} is raised (a caller-contract violation, distinct from
     * message-level CBPR+ validation, which is handled elsewhere and never throws).</p>
     *
     * @param fin the raw FIN (SWIFT MT block structure) content of an MT103 message; must not be
     *     {@code null}
     * @return a partial {@link MxPacs00800108} carrying only the migrated debtor and creditor party
     *     identity and postal address
     * @throws IllegalArgumentException if {@code fin} is {@code null}, cannot be parsed as an MT
     *     message, or does not represent an MT103
     */
    public MxPacs00800108 translate(final String fin) {
        if (fin == null) {
            throw new IllegalArgumentException("the FIN message content must not be null");
        }
        final AbstractMT abstractMT;
        try {
            abstractMT = AbstractMT.parse(fin);
        } catch (final IOException e) {
            throw new IllegalArgumentException("unable to parse the supplied content as an MT message", e);
        }
        if (!(abstractMT instanceof MT103)) {
            final String actual =
                    abstractMT == null ? "null" : abstractMT.getClass().getSimpleName();
            throw new IllegalArgumentException("expected an MT103 message but received " + actual);
        }
        return translate((MT103) abstractMT);
    }

    /**
     * Migrates the debtor and creditor of the supplied MT103 into a partial {@link MxPacs00800108}.
     *
     * <p>Exactly one {@link CreditTransferTransaction39} is created; its {@code Dbtr} is populated from
     * the MT103 50a option and its {@code Cdtr} from the 59a option (see {@link #translate(String)}
     * for the parsing entry point). No group header, agent, amount, date, charge, remittance or
     * reference data is mapped &mdash; the returned message is partial by design.</p>
     *
     * @param mt the source MT103 message; must not be {@code null}
     * @return a partial {@link MxPacs00800108} carrying only the migrated debtor and creditor
     * @throws IllegalArgumentException if {@code mt} is {@code null}
     */
    public MxPacs00800108 translate(final MT103 mt) {
        if (mt == null) {
            throw new IllegalArgumentException("the MT103 message must not be null");
        }

        // Build a fresh, partial pacs.008.001.08 body. The group header (GrpHdr) is intentionally
        // left null: only the debtor and creditor party identity/address are in scope.
        final FIToFICustomerCreditTransferV08 body = new FIToFICustomerCreditTransferV08();

        // A single credit-transfer transaction carries the migrated debtor and creditor.
        final CreditTransferTransaction39 tx = new CreditTransferTransaction39();
        tx.setDbtr(mapDebtor(mt));
        tx.setCdtr(mapCreditor(mt));

        // getCdtTrfTxInf() lazily initialises a non-null, mutable list on the generated model.
        body.getCdtTrfTxInf().add(tx);

        return new MxPacs00800108().setFIToFICstmrCdtTrf(body);
    }

    /**
     * Maps the MT103 ordering customer (debtor) into a {@link PartyIdentification135}, resolving the
     * present 50a option in priority order.
     *
     * <p>The options are mutually exclusive in a well-formed MT103; they are inspected in the order
     * 50F (structured), then 50K (unstructured), then 50A (BIC only). The structured option yields a
     * Structured/Hybrid postal address; the unstructured option yields an address-line-only address
     * (which fails the hero rule); the BIC-only option carries no postal address and leaves the party
     * minimal without inventing any address data. When no option is present the party is likewise left
     * minimal. The method always returns a non-{@code null} party so the target {@code Dbtr} branch is
     * populated.</p>
     *
     * @param mt the source MT103 message; never {@code null}
     * @return a never-{@code null} {@link PartyIdentification135} for the transaction {@code Dbtr}
     */
    private PartyIdentification135 mapDebtor(final MT103 mt) {
        final PartyIdentification135 debtor = new PartyIdentification135();

        final Field50F field50F = mt.getField50F();
        if (field50F != null) {
            // Structured "F" field: yields a Structured or Hybrid postal address (town + country).
            final List<String> lines = splitValue(field50F.getValue());
            debtor.setNm(AddressMigrationSupport.nameFromLines(lines));
            debtor.setPstlAdr(AddressMigrationSupport.structuredAddressFromFFieldLines(lines));
            return debtor;
        }

        final Field50K field50K = mt.getField50K();
        if (field50K != null) {
            // Unstructured name-and-address field: yields an address-line-only postal address that is
            // expected to fail the CBPR+ hero rule structured-address-min-town-country (by design).
            final List<String> lines = splitValue(field50K.getValue());
            debtor.setNm(AddressMigrationSupport.nameFromLines(lines));
            debtor.setPstlAdr(AddressMigrationSupport.unstructuredAddressFromLines(lines));
            return debtor;
        }

        final Field50A field50A = mt.getField50A();
        if (field50A != null) {
            // BIC-only ordering customer: carries no name or postal address. The party is left
            // minimal and no address data is invented (documented migration gap for a 50A debtor).
            return debtor;
        }

        // No debtor option present: leave the party minimal (defensive; partial by design).
        return debtor;
    }

    /**
     * Maps the MT103 beneficiary customer (creditor) into a {@link PartyIdentification135}, resolving
     * the present 59a option in priority order.
     *
     * <p>The options are mutually exclusive in a well-formed MT103; they are inspected in the order
     * 59F (structured), then Field 59 no-option (unstructured), then 59A (BIC only). The structured
     * option yields a Structured/Hybrid postal address; the unstructured no-option field yields an
     * address-line-only address (which fails the hero rule &mdash; the mandated negative path); the
     * BIC-only option carries no postal address and leaves the party minimal without inventing any
     * address data. When no option is present the party is likewise left minimal. The method always
     * returns a non-{@code null} party so the target {@code Cdtr} branch is populated.</p>
     *
     * @param mt the source MT103 message; never {@code null}
     * @return a never-{@code null} {@link PartyIdentification135} for the transaction {@code Cdtr}
     */
    private PartyIdentification135 mapCreditor(final MT103 mt) {
        final PartyIdentification135 creditor = new PartyIdentification135();

        final Field59F field59F = mt.getField59F();
        if (field59F != null) {
            // Structured "F" field: yields a Structured or Hybrid postal address (town + country).
            final List<String> lines = splitValue(field59F.getValue());
            creditor.setNm(AddressMigrationSupport.nameFromLines(lines));
            creditor.setPstlAdr(AddressMigrationSupport.structuredAddressFromFFieldLines(lines));
            return creditor;
        }

        final Field59 field59 = mt.getField59();
        if (field59 != null) {
            // Unstructured no-option field: yields an address-line-only postal address that is
            // expected to fail the CBPR+ hero rule structured-address-min-town-country (by design).
            final List<String> lines = splitValue(field59.getValue());
            creditor.setNm(AddressMigrationSupport.nameFromLines(lines));
            creditor.setPstlAdr(AddressMigrationSupport.unstructuredAddressFromLines(lines));
            return creditor;
        }

        final Field59A field59A = mt.getField59A();
        if (field59A != null) {
            // BIC-only beneficiary customer: carries no name or postal address. The party is left
            // minimal and no address data is invented (documented migration gap for a 59A creditor).
            return creditor;
        }

        // No creditor option present: leave the party minimal (defensive; partial by design).
        return creditor;
    }

    /**
     * Splits the raw value of an MT field into its individual lines in order, accepting both CR/LF and
     * LF terminators. Trailing empty lines are dropped (the default behaviour of
     * {@link String#split(String)}), which keeps the resulting list aligned with the field's
     * meaningful content.
     *
     * @param value the raw field value, possibly multi-line; may be {@code null}
     * @return an immutable {@link List} of the field's lines; an empty list when {@code value} is
     *     {@code null}; never {@code null} and never throws
     */
    private List<String> splitValue(final String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(value.split(LINE_SEPARATOR_REGEX));
    }
}
