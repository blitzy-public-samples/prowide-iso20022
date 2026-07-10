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

import com.prowidesoftware.swift.model.field.Field50F;
import com.prowidesoftware.swift.model.field.Field50G;
import com.prowidesoftware.swift.model.field.Field50H;
import com.prowidesoftware.swift.model.field.Field59;
import com.prowidesoftware.swift.model.field.Field59A;
import com.prowidesoftware.swift.model.field.Field59F;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.prowidesoftware.swift.model.mt.mt1xx.MT101;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.model.mx.dic.CreditTransferTransaction34;
import com.prowidesoftware.swift.model.mx.dic.CustomerCreditTransferInitiationV09;
import com.prowidesoftware.swift.model.mx.dic.OrganisationIdentification29;
import com.prowidesoftware.swift.model.mx.dic.Party38Choice;
import com.prowidesoftware.swift.model.mx.dic.PartyIdentification135;
import com.prowidesoftware.swift.model.mx.dic.PaymentInstruction30;
import com.prowidesoftware.swift.model.mx.dic.PostalAddress24;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Address-scoped migrator that translates the ordering customer (debtor) and beneficiary (creditor)
 * <em>identity and postal-address</em> of a legacy MT101 (Request for Transfer) into a partial ISO 20022
 * <strong>pain.001.001.09</strong> ({@link MxPain00100109}) Customer Credit Transfer Initiation.
 *
 * <p>This class is part of the {@code iso20022-cbpr} module (Capability B &mdash; address-scoped
 * MT&rarr;MX migration). It demonstrates the migration path from the FIN MT101 message, whose cross-border
 * usage migrates to pain.001 v9, focusing exclusively on the parties whose postal address is subject to the
 * CBPR+ SR2026 structured-address mandate. The resulting {@link MxPain00100109} is intended to be fed through
 * {@code CbprValidator.validate(...)} so that the hero rule {@code structured-address-min-town-country} can be
 * asserted end-to-end on a message assembled from real MT content.</p>
 *
 * <p><strong>Sequence B (repetitive) source fields.</strong> In an MT101 the ordering customer and the
 * beneficiary live in the repetitive Sequence B, so the MT-level accessors return a {@link List} of fields
 * rather than a single occurrence. For this partial single-transaction demonstration the migrator consumes the
 * <em>first</em> present occurrence of each option after a null-and-empty guard.</p>
 *
 * <p><strong>Ordering customer (debtor) resolution.</strong> The ordering customer is the 50a option
 * <strong>F</strong>, <strong>G</strong> or <strong>H</strong> (options 50C / 50L identify the <em>instructing
 * party</em> and are intentionally never consulted here). The options are resolved in the following priority so
 * that the happy path yields a compliant, structured address:</p>
 *
 * <ul>
 *   <li>{@link MT101#getField50F() Field 50F} (structured) &rarr; a Structured or Hybrid {@code PstlAdr}
 *       (Town + Country, optionally with address lines) &mdash; passes the hero rule;</li>
 *   <li>otherwise {@link MT101#getField50H() Field 50H} (account plus unstructured name-and-address) &rarr; an
 *       address-line-only {@code PstlAdr} &mdash; fails the hero rule (the intentional negative path);</li>
 *   <li>otherwise {@link MT101#getField50G() Field 50G} (account plus BIC, no postal address) &rarr; the party
 *       is identified by its BIC via {@code Id/OrgId/AnyBIC} and carries no address (hero rule not applicable).</li>
 * </ul>
 *
 * <p><strong>Beneficiary (creditor) resolution.</strong> The beneficiary is resolved analogously in the
 * priority {@link MT101#getField59F() Field 59F} (structured) &rarr; {@link MT101#getField59() Field 59}
 * (no-option, unstructured) &rarr; {@link MT101#getField59A() Field 59A} (BIC only).</p>
 *
 * <p><strong>Address/party-scoped and partial by design.</strong> Only the ordering-customer and beneficiary
 * name and postal address are migrated (each mapped onto {@code PmtInf/Dbtr} and
 * {@code PmtInf/CdtTrfTxInf/Cdtr} respectively). Every other branch of the target message is intentionally left
 * {@code null}: the group header (including the initiating party), the payment method and payment type
 * information, all amounts, the debtor and creditor agents, requested execution dates, remittance information
 * and references are <strong>not</strong> mapped. The returned {@link MxPain00100109} is therefore a partial
 * message by design, and the accompanying test asserts specifically on the structured-address findings rather
 * than on overall validity.</p>
 *
 * <p><strong>MT-API-bounded.</strong> The source MT is consumed <em>read-only</em> through the public Prowide
 * Core ({@code pw-swift-core}) MT API only ({@link AbstractMT#parse(String)}, the {@code MT101} Sequence B list
 * accessors and the universal {@link com.prowidesoftware.swift.model.field.Field#getValue()} accessor). No new
 * MT parser is introduced and no MT API is invented. All address assembly is delegated to the
 * {@code pw-swift-core}-free {@link AddressMigrationSupport}, which confines every dependency on the MT field
 * model to this class. The target MX object is a brand-new instance populated through the generated fluent
 * setters, which is legitimate output construction and not a mutation of any generated model class.</p>
 *
 * <p><strong>Resilience.</strong> All Sequence B list accessors are guarded against {@code null} and empty
 * results before the first element is read, and every field value is split defensively, so a well-formed MT101
 * never triggers a {@link NullPointerException}. Supplying content that is not a parseable MT101 is treated as a
 * caller-contract violation and raised as an {@link IllegalArgumentException}; this is deliberately distinct
 * from CBPR+ message validation, which is performed elsewhere and never throws.</p>
 *
 * <p><strong>Thread-safety.</strong> Instances are stateless and immutable; a single instance may be shared and
 * used concurrently.</p>
 *
 * @see MxPain00100109
 * @see MT101
 * @see AddressMigrationSupport
 */
public class Mt101ToPain001 {

    /** Line separator used to split the raw multi-line value of an MT field (CR/LF or LF). */
    private static final Pattern LINE_SEPARATOR = Pattern.compile("\\r?\\n");

    /**
     * ISO 9362 Business Identifier Code (BIC) pattern: four alphanumeric institution characters, two alphabetic
     * country characters, two alphanumeric location characters and an optional three alphanumeric branch suffix
     * (i.e. an 8- or 11-character BIC).
     */
    private static final Pattern BIC_PATTERN = Pattern.compile("[A-Z0-9]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?");

    /**
     * Creates a new, stateless MT101 &rarr; pain.001.001.09 migrator.
     */
    public Mt101ToPain001() {
        // No configuration state is required; the migrator is stateless and reusable.
    }

    /**
     * Parses the supplied FIN content as an MT message and migrates the ordering customer and beneficiary of the
     * resulting MT101 into a partial {@link MxPain00100109}.
     *
     * <p>The message is parsed with {@link AbstractMT#parse(String)} and is required to be an MT101. If the
     * content cannot be parsed, or does not correspond to an MT101, an {@link IllegalArgumentException} is raised
     * (this is a caller-contract violation, distinct from message-level CBPR+ validation, which is handled
     * elsewhere and never throws).</p>
     *
     * @param fin the raw FIN (SWIFT MT block structure) content of an MT101 message; must not be {@code null}
     * @return a partial {@link MxPain00100109} carrying only the migrated ordering-customer and beneficiary
     *     name and postal address
     * @throws IllegalArgumentException if {@code fin} is {@code null}, cannot be parsed as an MT message, or does
     *     not represent an MT101
     */
    public MxPain00100109 translate(final String fin) {
        if (fin == null) {
            throw new IllegalArgumentException("the FIN message content must not be null");
        }
        final AbstractMT abstractMT;
        try {
            abstractMT = AbstractMT.parse(fin);
        } catch (final IOException e) {
            throw new IllegalArgumentException("unable to parse the supplied content as an MT message", e);
        }
        if (!(abstractMT instanceof MT101)) {
            final String actual =
                    abstractMT == null ? "null" : abstractMT.getClass().getSimpleName();
            throw new IllegalArgumentException("expected an MT101 message but received " + actual);
        }
        return translate((MT101) abstractMT);
    }

    /**
     * Migrates the ordering customer and beneficiary of the supplied MT101 into a partial
     * {@link MxPain00100109}.
     *
     * <p>A single {@link PaymentInstruction30} is created carrying the migrated ordering customer as its debtor,
     * and a single {@link CreditTransferTransaction34} is created carrying the migrated beneficiary as its
     * creditor. No group header, payment method, payment type information, amount, agent, date, remittance or
     * reference data is mapped &mdash; the returned message is partial by design.</p>
     *
     * @param mt the source MT101 message; must not be {@code null}
     * @return a partial {@link MxPain00100109} carrying only the migrated ordering-customer and beneficiary
     *     name and postal address
     * @throws IllegalArgumentException if {@code mt} is {@code null}
     */
    public MxPain00100109 translate(final MT101 mt) {
        if (mt == null) {
            throw new IllegalArgumentException("the MT101 message must not be null");
        }

        // Build a fresh, partial pain.001.001.09 body. The group header (GrpHdr / GroupHeader85), including the
        // initiating party, is intentionally left null: only the ordering-customer and beneficiary identity are
        // in scope for this address-scoped migration.
        final CustomerCreditTransferInitiationV09 body = new CustomerCreditTransferInitiationV09();

        // A single payment instruction carries the migrated ordering customer (debtor); its single
        // credit-transfer transaction carries the migrated beneficiary (creditor).
        final PaymentInstruction30 pmtInf = new PaymentInstruction30();
        final CreditTransferTransaction34 tx = new CreditTransferTransaction34();

        // Map ONLY the ordering-customer identity + postal address onto the debtor, and the beneficiary identity
        // + postal address onto the creditor. Both parties are always set (possibly minimal) on this partial
        // message; the debtor agent, amount and all other branches remain null.
        pmtInf.setDbtr(mapOrderingCustomer(mt));
        tx.setCdtr(mapBeneficiary(mt));

        // getCdtTrfTxInf() and getPmtInf() lazily initialise non-null, mutable lists on the generated model.
        pmtInf.getCdtTrfTxInf().add(tx);
        body.getPmtInf().add(pmtInf);

        return new MxPain00100109().setCstmrCdtTrfInitn(body);
    }

    /**
     * Builds the ordering-customer party (target debtor) from the MT101 Sequence B 50a option, resolving Field
     * 50F, then Field 50H, then Field 50G in that priority. The returned party is never {@code null}; it is
     * minimal (empty) when no ordering-customer option is present.
     *
     * @param mt the source MT101 message; never {@code null}
     * @return the migrated debtor party; never {@code null}
     */
    private PartyIdentification135 mapOrderingCustomer(final MT101 mt) {
        final PartyIdentification135 dbtr = new PartyIdentification135();

        final List<Field50F> field50F = mt.getField50F();
        if (isPresent(field50F)) {
            // Structured "F" field: numbered name/address lines yield a Structured or Hybrid postal address.
            final List<String> lines = splitValue(field50F.get(0).getValue());
            final PostalAddress24 address = AddressMigrationSupport.structuredAddressFromFFieldLines(lines);
            dbtr.setNm(AddressMigrationSupport.nameFromLines(lines));
            dbtr.setPstlAdr(address);
            return dbtr;
        }

        final List<Field50H> field50H = mt.getField50H();
        if (isPresent(field50H)) {
            // Account plus unstructured name-and-address: yields an address-line-only (fully unstructured)
            // postal address that deliberately fails the hero rule when validated.
            final List<String> lines = splitValue(field50H.get(0).getValue());
            final PostalAddress24 address = AddressMigrationSupport.unstructuredAddressFromLines(lines);
            dbtr.setNm(AddressMigrationSupport.nameFromLines(lines));
            dbtr.setPstlAdr(address);
            return dbtr;
        }

        final List<Field50G> field50G = mt.getField50G();
        if (isPresent(field50G)) {
            // Account plus BIC: the ordering customer is identified by its BIC and carries NO postal address,
            // so the hero rule is not applicable. Only the party identity (AnyBIC) is preserved; no address is
            // ever invented for this option.
            applyBicIdentity(dbtr, splitValue(field50G.get(0).getValue()));
            return dbtr;
        }

        // Defensive: no supported ordering-customer option present; leave the debtor minimal.
        return dbtr;
    }

    /**
     * Builds the beneficiary party (target creditor) from the MT101 Sequence B 59a option, resolving Field 59F,
     * then Field 59 (no-option), then Field 59A in that priority. The returned party is never {@code null}; it is
     * minimal (empty) when no beneficiary option is present.
     *
     * @param mt the source MT101 message; never {@code null}
     * @return the migrated creditor party; never {@code null}
     */
    private PartyIdentification135 mapBeneficiary(final MT101 mt) {
        final PartyIdentification135 cdtr = new PartyIdentification135();

        final List<Field59F> field59F = mt.getField59F();
        if (isPresent(field59F)) {
            // Structured "F" field: numbered name/address lines yield a Structured or Hybrid postal address.
            final List<String> lines = splitValue(field59F.get(0).getValue());
            final PostalAddress24 address = AddressMigrationSupport.structuredAddressFromFFieldLines(lines);
            cdtr.setNm(AddressMigrationSupport.nameFromLines(lines));
            cdtr.setPstlAdr(address);
            return cdtr;
        }

        final List<Field59> field59 = mt.getField59();
        if (isPresent(field59)) {
            // No-option beneficiary: free-format name-and-address yields an address-line-only (fully
            // unstructured) postal address that deliberately fails the hero rule when validated.
            final List<String> lines = splitValue(field59.get(0).getValue());
            final PostalAddress24 address = AddressMigrationSupport.unstructuredAddressFromLines(lines);
            cdtr.setNm(AddressMigrationSupport.nameFromLines(lines));
            cdtr.setPstlAdr(address);
            return cdtr;
        }

        final List<Field59A> field59A = mt.getField59A();
        if (isPresent(field59A)) {
            // BIC-identified beneficiary: carries NO postal address, so the hero rule is not applicable. Only
            // the party identity (AnyBIC) is preserved; no address is ever invented for this option.
            applyBicIdentity(cdtr, splitValue(field59A.get(0).getValue()));
            return cdtr;
        }

        // Defensive: no supported beneficiary option present; leave the creditor minimal.
        return cdtr;
    }

    /**
     * Preserves the party identity of a BIC-only option (50G / 59A) by setting {@code Id/OrgId/AnyBIC} from the
     * ISO 9362 identifier-code line, when one can be located. No postal address is produced for the BIC-only
     * options; this method never invents address data.
     *
     * @param party the party being assembled; never {@code null}
     * @param lines the split field value lines; may be {@code null} or empty
     */
    private void applyBicIdentity(final PartyIdentification135 party, final List<String> lines) {
        final String bic = extractBic(lines);
        if (bic != null) {
            party.setId(new Party38Choice().setOrgId(new OrganisationIdentification29().setAnyBIC(bic)));
        }
    }

    /**
     * Splits the raw multi-line value of an MT field into individual lines on CR/LF or LF boundaries.
     *
     * <p>The split is null-safe: a {@code null} value yields an empty (but non-{@code null}) list. Trailing empty
     * lines are naturally discarded by {@link String#split(String)} with the default limit. The returned list is
     * a fresh, mutable {@link ArrayList} so the caller (or {@link AddressMigrationSupport}) may consume it
     * freely.</p>
     *
     * @param value the raw field value; may be {@code null}
     * @return a never-{@code null}, mutable list of lines; empty when {@code value} is {@code null}
     */
    private List<String> splitValue(final String value) {
        if (value == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(LINE_SEPARATOR.split(value)));
    }

    /**
     * Locates the ISO 9362 BIC on a BIC-only field (50G / 59A) using the universal split field value rather than
     * positional components.
     *
     * <p>Blank lines and {@code '/'}-prefixed account/party-identifier lines are ignored. The last remaining line
     * that fully matches {@link #BIC_PATTERN} is returned as the BIC. Returns {@code null} when no such line
     * exists; no BIC is ever invented.</p>
     *
     * @param lines the split field value lines; may be {@code null}
     * @return the BIC string, or {@code null} if none could be identified
     */
    private String extractBic(final List<String> lines) {
        if (lines == null) {
            return null;
        }
        String bic = null;
        for (final String line : lines) {
            if (line == null) {
                continue;
            }
            final String candidate = line.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            // Account/party-identifier lines are prefixed with '/'; they never carry the BIC.
            if (candidate.charAt(0) == '/') {
                continue;
            }
            // Keep the last line that matches the ISO 9362 BIC pattern (the identifier-code line).
            if (BIC_PATTERN.matcher(candidate).matches()) {
                bic = candidate;
            }
        }
        return bic;
    }

    /**
     * Indicates whether the given Sequence B field list carries at least one occurrence.
     *
     * @param list the field list returned by an MT101 accessor; may be {@code null}
     * @return {@code true} when the list is non-{@code null} and non-empty; {@code false} otherwise
     */
    private static boolean isPresent(final List<?> list) {
        return list != null && !list.isEmpty();
    }
}
