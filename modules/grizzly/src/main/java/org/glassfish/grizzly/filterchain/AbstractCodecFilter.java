/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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
 */

package org.glassfish.grizzly.filterchain;

import java.io.IOException;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.Transformer;

/**
 *
 * @author Alexey Stashok
 */
public abstract class AbstractCodecFilter<K, L> extends BaseFilter
        implements CodecFilter<K, L> {
    private final Transformer<K, L> decoder;
    private final Transformer<L, K> encoder;

    public AbstractCodecFilter(Transformer<K, L> decoder,
            Transformer<L, K> encoder) {
        this.decoder = decoder;
        this.encoder = encoder;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final K message = (K) ctx.getMessage();

        final TransformationResult<K, L> result =
                decoder.transform(connection, message);

        switch(result.getStatus()) {
            case COMPLETE:
                final K remainder = result.getExternalRemainder();
                final boolean hasRemaining =
                        decoder.hasInputRemaining(connection, remainder);
                decoder.release(connection);
                ctx.setMessage(result.getMessage());
                if (hasRemaining) {
                    return ctx.getInvokeAction(remainder);
                } else {
                    return ctx.getInvokeAction();
                }
            case INCOMPLETE:
                return ctx.getStopAction(message);
            case ERROR:
                throw new TransformationException(getClass().getName() +
                        " transformation error: (" + result.getErrorCode() + ") " +
                        result.getErrorDescription());
        }

        return ctx.getInvokeAction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final L message = (L) ctx.getMessage();

        final TransformationResult<L, K> result = encoder.transform(connection, message);

        switch(result.getStatus()) {
            case COMPLETE:
                ctx.setMessage(result.getMessage());
                final L remainder = result.getExternalRemainder();
                final boolean hasRemaining =
                        encoder.hasInputRemaining(connection, remainder);
                encoder.release(connection);
                if (hasRemaining) {
                    return ctx.getInvokeAction(remainder);
                } else {
                    return ctx.getInvokeAction();
                }
            case INCOMPLETE:
                return ctx.getStopAction(message);
            case ERROR:
                throw new TransformationException(getClass().getName() +
                        " transformation error: (" + result.getErrorCode() + ") " +
                        result.getErrorDescription());
        }

        return ctx.getInvokeAction();
    }


    @Override
    public Transformer<K, L> getDecoder() {
        return decoder;
    }

    @Override
    public Transformer<L, K> getEncoder() {
        return encoder;
    }
}
