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

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00200108;
import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import com.prowidesoftware.swift.model.mx.MxPacs00900108;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import com.prowidesoftware.swift.utils.Lib;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Entry-point behaviour tests for {@link CbprValidator}.
 *
 * <p>This class deliberately verifies only the three <em>mechanical</em> contracts of the single
 * public entry point {@link CbprValidator#validate(AbstractMX)}, not the verdict of any individual
 * CBPR+ rule (those are exercised exhaustively by the per-message test classes such as
 * {@code Pacs008ValidationTest}, {@code Pacs009ValidationTest} and {@code Pain001ValidationTest}). The
 * three contracts are:
 *
 * <ol>
 *   <li><strong>Dispatch by concrete MX type.</strong> A {@code pacs.008.001.08},
 *       {@code pacs.009.001.08} or {@code pain.001.001.09} message must be routed to the rule set that
 *       applies to it &mdash; including when the caller holds the message through the
 *       {@link AbstractMX} base type.
 *   <li><strong>Never throws.</strong> A {@code null} message, an unsupported MX subtype, a
 *       {@code null} produced by the generic parser for unrecognised input, and a structurally empty
 *       target message (all-{@code null} internal branches) must each yield a non-{@code null}
 *       {@link ValidationResult} rather than an exception.
 *   <li><strong>Collect all findings.</strong> Every applicable rule runs and every finding is
 *       aggregated; the validator must not stop at the first violation.
 * </ol>
 *
 * <p>Consistent with the module's testing convention, real fixtures are parsed into real generated
 * {@code Mx*} objects (no mocks) and driven through the real {@link CbprValidator}. Assertions are
 * made exclusively through the public API &mdash; {@code new CbprValidator()},
 * {@link CbprValidator#validate(AbstractMX)}, {@link ValidationResult#isValid()},
 * {@link ValidationResult#getFindings()} and {@link Finding#getRuleId()} &mdash; and never against the
 * internal rule classes (whose simple names intentionally collide across the per-message subpackages).
 *
 * @see CbprValidator
 * @see ValidationResult
 * @see Finding
 */
class CbprValidatorTest {

    // ---------------------------------------------------------------------------------------------
    // Contract 1 - dispatch by concrete MX type (the correct rule set runs for each message type).
    // ---------------------------------------------------------------------------------------------

    /**
     * A clean {@code pacs.008.001.08} message must dispatch to the pacs.008 rule set and pass every
     * rule, yielding a valid result with no findings.
     */
    @Test
    void dispatchesPacs008AndPasses() throws IOException {
        MxPacs00800108 mx = MxPacs00800108.parse(Lib.readResource("pacs008_structured_address.xml"));

        ValidationResult r = new CbprValidator().validate(mx);

        assertThat(r).isNotNull();
        assertThat(r.isValid()).isTrue();
        assertThat(r.getFindings()).isEmpty();
    }

    /**
     * A clean {@code pacs.009.001.08} message must dispatch to the pacs.009 rule set and pass every
     * rule, yielding a valid result with no findings.
     */
    @Test
    void dispatchesPacs009AndPasses() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_structured_address.xml"));

        ValidationResult r = new CbprValidator().validate(mx);

        assertThat(r).isNotNull();
        assertThat(r.isValid()).isTrue();
        assertThat(r.getFindings()).isEmpty();
    }

    /**
     * A clean {@code pain.001.001.09} message must dispatch to the pain.001 rule set and pass every
     * rule, yielding a valid result with no findings.
     */
    @Test
    void dispatchesPain001AndPasses() throws IOException {
        MxPain00100109 mx = MxPain00100109.parse(Lib.readResource("pain001_structured_address.xml"));

        ValidationResult r = new CbprValidator().validate(mx);

        assertThat(r).isNotNull();
        assertThat(r.isValid()).isTrue();
        assertThat(r.getFindings()).isEmpty();
    }

    /**
     * Dispatch must resolve the concrete subtype even when the message is held through the
     * {@link AbstractMX} base type.
     *
     * <p>The first reference is a genuine {@link MxPacs00800108} upcast to {@link AbstractMX}: the
     * validator must still route it to the pacs.008 rule set (a clean fixture stays valid). The second
     * reference is produced by the generic {@link AbstractMX#parse(String)} parser, which auto-detects
     * the concrete subtype from the document namespace; the validator must dispatch it identically.
     */
    @Test
    void dispatchesViaAbstractMxUpcast() throws IOException {
        AbstractMX mx = MxPacs00800108.parse(Lib.readResource("pacs008_structured_address.xml"));

        ValidationResult r = new CbprValidator().validate(mx);

        assertThat(r).isNotNull();
        assertThat(r.isValid()).isTrue();
        assertThat(r.getFindings()).isEmpty();

        // The generic parser auto-detects the concrete subtype; dispatch must behave identically.
        AbstractMX generic = AbstractMX.parse(Lib.readResource("pacs008_structured_address.xml"));
        assertThat(new CbprValidator().validate(generic)).isNotNull();
    }

    // ---------------------------------------------------------------------------------------------
    // Contract 2 - never throws (null, unsupported type, malformed/null parse, empty body).
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code validate(null)} must not throw and must return a non-{@code null}, valid, empty result:
     * there is nothing to validate, so no rule set applies.
     */
    @Test
    void nullMessageReturnsEmptyValidResultAndDoesNotThrow() {
        assertThatCode(() -> new CbprValidator().validate(null)).doesNotThrowAnyException();

        ValidationResult r = new CbprValidator().validate(null);

        assertThat(r).isNotNull();
        assertThat(r.isValid()).isTrue();
        assertThat(r.getFindings()).isEmpty();
    }

    /**
     * An {@link AbstractMX} subtype that is not one of the three CBPR+ targets carries no rule set, so
     * it must fall through to a non-{@code null}, valid, empty result. {@link MxPacs00200108}
     * (pacs.002) is used as a representative non-target type; an empty instance suffices because
     * dispatch is by {@code instanceof}.
     */
    @Test
    void unsupportedMxTypeReturnsEmptyValidResult() {
        ValidationResult r = new CbprValidator().validate(new MxPacs00200108());

        assertThat(r).isNotNull();
        assertThat(r.isValid()).isTrue();
        assertThat(r.getFindings()).isEmpty();
    }

    /**
     * The generic parser may return {@code null} for input it does not recognise as an MX message; the
     * validator must tolerate that {@code null} without throwing and still return a non-{@code null}
     * result.
     */
    @Test
    void malformedXmlDoesNotThrow() {
        AbstractMX mx = AbstractMX.parse("<not-a-valid-mx/>"); // may be null

        assertThatCode(() -> new CbprValidator().validate(mx)).doesNotThrowAnyException();
        assertThat(new CbprValidator().validate(mx)).isNotNull();
    }

    /**
     * The strongest guard on the never-throw contract: a structurally empty target message (its
     * {@code FIToFICstmrCdtTrf} body and every downstream branch are {@code null}) must not cause any
     * rule to throw. The validator's per-rule safety net and each rule's own defensive coding must
     * survive a fully-{@code null} object graph and still return a non-{@code null} result.
     */
    @Test
    void emptyPacs008BodyDoesNotThrow() {
        MxPacs00800108 empty = new MxPacs00800108(); // no FIToFICstmrCdtTrf body

        assertThatCode(() -> new CbprValidator().validate(empty)).doesNotThrowAnyException();
        assertThat(new CbprValidator().validate(empty)).isNotNull();
    }

    // ---------------------------------------------------------------------------------------------
    // Contract 3 - collect all findings (the validator does not short-circuit on the first hit).
    // ---------------------------------------------------------------------------------------------

    /**
     * The national-clearing-code fixture co-fires two distinct {@code WARNING} rules
     * ({@code agent-national-clearing-code-only} and {@code fi-party-bicfi-preferred}). Their
     * simultaneous presence in a single result proves the validator runs every applicable rule and
     * aggregates every finding rather than stopping at the first. Because both findings are
     * {@code WARNING}-severity, the result remains valid.
     */
    @Test
    void collectsAllFindingsNotShortCircuited() throws IOException {
        MxPacs00900108 mx = MxPacs00900108.parse(Lib.readResource("pacs009_national_clearing_code_negative.xml"));

        ValidationResult r = new CbprValidator().validate(mx);

        assertThat(r.getFindings())
                .extracting(Finding::getRuleId)
                .contains("agent-national-clearing-code-only", "fi-party-bicfi-preferred");
        assertThat(r.isValid()).isTrue();
    }
}
