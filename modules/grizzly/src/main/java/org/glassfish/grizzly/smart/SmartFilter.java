/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */

package org.glassfish.grizzly.smart;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.filterchain.CodecFilter;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.RerunChainAction;
import org.glassfish.grizzly.filterchain.StopAction;

/**
 *
 * @author oleksiys
 */
public class SmartFilter<K> extends FilterAdapter
        implements CodecFilter<Buffer, K> {

    private AttributeBuilder attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
    private Attribute<Buffer> remainderAttribute;

    private SmartCodec<K> smartCodec;

    public SmartFilter(Class clazz) {
        this(new SmartCodec<K>(clazz));
    }

    public SmartFilter(SmartCodec<K> smartCodec) {
        this.smartCodec = smartCodec;
        remainderAttribute = attributeBuilder.createAttribute(
                "UTFStringFilter.remainder");
    }

    public SmartFilter(SmartCodec<K> smartCodec, AttributeBuilder attrBuilder) {
        this.smartCodec = smartCodec;
        this.attributeBuilder = attrBuilder;
        remainderAttribute = attributeBuilder.createAttribute(
                "SmartFilter.remainder");
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        Buffer message = (Buffer) ctx.getMessage();
        Connection connection = ctx.getConnection();

        Transformer<Buffer, K> decoder = getDecoder();
        TransformationResult<K> result = decoder.transform(connection,
                message, null);
        switch (result.getStatus()) {
            case COMPLETED:
                decoder.release(connection);
                ctx.setMessage(result.getMessage());
                
                // Important. If message has remaining - we will need to
                // reinvoke the chain in postExecute phase
                if (message.hasRemaining()) {
                    remainderAttribute.set(ctx.obtainAttributes(), message);
                }

                return nextAction;

            case INCOMPLED:
                return new StopAction();

            default:
                decoder.release(connection);
                throw new IllegalStateException(result.getErrorCode() + ": " +
                        result.getErrorDescription());
        }
    }

    @Override
    public NextAction postRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        AttributeHolder holder = ctx.getAttributes();
        if (holder != null) {
            Buffer remainder = remainderAttribute.remove(holder);
            if (remainder != null) {
                ctx.setMessage(remainder);
                nextAction = new RerunChainAction();
            }
        }

        return nextAction;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        TransformationResult result;
        Connection connection = ctx.getConnection();

        Transformer<K, Buffer> encoder = getEncoder();
        result = encoder.transform(connection, (K) ctx.getMessage(), null);

        ctx.setMessage(result.getMessage());
        encoder.release(connection);
        return nextAction;
    }

    public Transformer<Buffer, K> getDecoder() {
        return smartCodec.getDecoder();
    }

    public Transformer<K, Buffer> getEncoder() {
        return smartCodec.getEncoder();
    }

}
