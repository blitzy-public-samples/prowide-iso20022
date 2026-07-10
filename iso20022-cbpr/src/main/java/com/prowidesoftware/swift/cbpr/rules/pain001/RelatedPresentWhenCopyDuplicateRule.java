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
package com.prowidesoftware.swift.cbpr.rules.pain001;

import com.prowidesoftware.swift.cbpr.CbprRule;
import com.prowidesoftware.swift.cbpr.Finding;
import com.prowidesoftware.swift.cbpr.Severity;
import com.prowidesoftware.swift.model.mx.AppHdr;
import com.prowidesoftware.swift.model.mx.BusinessAppHdrV02;
import com.prowidesoftware.swift.model.mx.MxPain00100109;
import java.util.ArrayList;
import java.util.List;

/**
 * CBPR+ Standards Release 2026 (SR2026) rule
 * <strong>{@code related-present-when-copydupl}</strong> (severity {@link Severity#WARNING}) for
 * <strong>pain.001.001.09</strong> (Customer Credit Transfer Initiation, {@link MxPain00100109}).
 *
 * <p>This is the CBPR+ rule known as <strong>R1</strong>. Its authoritative requirement, preserved
 * verbatim from the CBPR+ rule inventory, is:
 *
 * <blockquote>"If AppHdr CopyDuplicate present, Related must be present"</blockquote>
 *
 * <p>A CopyDuplicate indicator ({@code CpyDplct}) on the Business Application Header marks a message
 * as a copy and/or duplicate of a previously exchanged message. When that indicator is present, the
 * header must also carry at least one Related ({@code Rltd}) reference identifying the original
 * business message the copy/duplicate relates to. This rule enforces that pairing.
 *
 * <h2>Header-scoped, not body-scoped</h2>
 *
 * <p>Unlike every other pain.001 rule, this check inspects the <strong>Business Application
 * Header</strong> rather than the pain.001 document body. It deliberately <em>reuses the existing
 * header handling</em> instead of parsing or interpreting the header itself: the header carried by an
 * already-parsed {@link MxPain00100109} is obtained through {@link MxPain00100109#getAppHdr()}
 * (inherited from {@code AbstractMX}) and evaluated only when it is a {@link BusinessAppHdrV02} &mdash;
 * the CBPR+ default header (BAH V2). This is achieved purely by composition and a single downcast; no
 * new header parser is introduced, {@code AppHdrParser} is not invoked here (the {@code AppHdr} is
 * already attached to the message), and no change is made to {@code iso20022-core}.
 *
 * <h2>Evaluation</h2>
 *
 * <p>The rule proceeds in short-circuiting steps and reports at most one finding:
 *
 * <ul>
 *   <li>if the message is {@code null}, or {@link MxPain00100109#getAppHdr()} is {@code null}, or the
 *       header is not a {@link BusinessAppHdrV02}, the rule does not apply &mdash; nothing is reported;
 *   <li>if the header's {@code CpyDplct} is <strong>absent</strong> (its accessor returns
 *       {@code null}), the rule is not triggered &mdash; nothing is reported;
 *   <li>if {@code CpyDplct} is <strong>present</strong> but the header's {@code Rltd} list is
 *       <strong>empty</strong>, exactly one {@link Severity#WARNING} {@link Finding} is produced;
 *   <li>if {@code CpyDplct} is present and {@code Rltd} is non-empty, the requirement is satisfied
 *       &mdash; nothing is reported.
 * </ul>
 *
 * <h2>Severity</h2>
 *
 * <p>This is the <em>only</em> pain.001 rule classified as a {@link Severity#WARNING} rather than an
 * {@link Severity#ERROR}. Keeping it a warning is essential to the CBPR+ classification: a finding
 * emitted by this rule signals a soft recommendation and, by design, does <strong>not</strong> flip
 * {@code ValidationResult.valid} to {@code false} (validity turns on the presence of an
 * {@code ERROR}-severity finding only). The rule must never be strengthened to {@code ERROR}.
 *
 * <h2>Behavioral contract</h2>
 *
 * <p>In line with {@link CbprRule}, this rule <strong>never throws</strong>. The {@code instanceof}
 * operator is null-safe, so a {@code null} message header (and a {@code null} message, guarded
 * explicitly) is handled by the same type guard without a {@link NullPointerException}. The
 * {@code Rltd} accessor is lazily initialized and never returns {@code null}, so emptiness is tested
 * with {@code isEmpty()} rather than a {@code null} check. The returned list is empty when the message
 * complies (or the rule does not apply) and is never {@code null}.
 *
 * <p>Instances are stateless and hold no mutable data, so a single instance may be reused across
 * messages and shared between threads. The (implicit) public no-argument constructor lets the
 * validator create the rule with {@code new RelatedPresentWhenCopyDuplicateRule()}.
 *
 * @see CbprRule
 * @see Finding
 * @see Severity
 * @see MxPain00100109
 * @see BusinessAppHdrV02
 * @since 10.3.14
 */
public class RelatedPresentWhenCopyDuplicateRule implements CbprRule<MxPain00100109> {

    /** The CBPR+ rule identifier reported by every {@link Finding} this rule emits. */
    private static final String RULE_ID = "related-present-when-copydupl";

    /**
     * Evaluates the CBPR+ <strong>R1</strong> {@code related-present-when-copydupl} rule against the
     * given pain.001.001.09 message and returns the findings it produces.
     *
     * <p>The rule inspects the message's Business Application Header (reused, not reparsed) and emits a
     * single {@link Severity#WARNING} finding when a CopyDuplicate indicator is present without an
     * accompanying Related reference. In every other situation &mdash; including a {@code null}
     * message, an absent or non-{@link BusinessAppHdrV02} header, an absent CopyDuplicate indicator, or
     * a present-and-non-empty Related list &mdash; the rule reports nothing.
     *
     * @param message the parsed pain.001.001.09 message to validate; may be {@code null} or partial,
     *     in which case the rule reports nothing and does not throw
     * @return a list containing a single {@code WARNING} finding when CopyDuplicate is present but
     *     Related is absent; otherwise an empty list. Never {@code null}.
     */
    @Override
    public List<Finding> check(MxPain00100109 message) {
        List<Finding> findings = new ArrayList<>();
        if (message == null) {
            return findings;
        }
        AppHdr appHdr = message.getAppHdr();
        if (!(appHdr instanceof BusinessAppHdrV02)) {
            // No business header attached, or a header type this rule does not evaluate.
            return findings;
        }
        BusinessAppHdrV02 bah = (BusinessAppHdrV02) appHdr;
        if (bah.getCpyDplct() == null) {
            // CopyDuplicate indicator is absent -> the rule is not triggered.
            return findings;
        }
        if (bah.getRltd().isEmpty()) {
            // getRltd() is lazily initialized and never null; test emptiness, not nullity.
            findings.add(new Finding(
                    RULE_ID,
                    Severity.WARNING,
                    "AppHdr/Rltd",
                    "AppHdr CopyDuplicate is present but Related (Rltd) is absent; "
                            + "CBPR+ R1 requires Related to be present when CopyDuplicate is indicated."));
        }
        return findings;
    }
}
