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

package com.sun.grizzly.filterchain;

import com.sun.grizzly.Appendable;
import com.sun.grizzly.Connection;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.Transformer;
import java.io.IOException;

/**
 *
 * @author Alexey Stashok
 */
public class CodecFilterAdapter<K, L> extends FilterAdapter
        implements CodecFilter<K, L> {
    private final Transformer<K, L> decoder;
    private final Transformer<L, K> encoder;

    public CodecFilterAdapter(Transformer<K, L> decoder, Transformer<L, K> encoder) {
        this.decoder = decoder;
        this.encoder = encoder;
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        final Connection connection = ctx.getConnection();
        final K message = (K) ctx.getMessage();

        final TransformationResult<K, L> result =
                decoder.transform(connection, message);

        switch(result.getStatus()) {
            case COMPLETED:
            {
                final K remainder = result.getExternalRemainder();
                final boolean hasRemaining =
                        decoder.hasInputRemaining(remainder);
                decoder.release(ctx);
                
                ctx.setMessage(result.getMessage());
                if (hasRemaining) {
                    return ctx.getInvokeAction(
                            Appendable.Utils.makeAppendable(remainder));
                } else {
                    return ctx.getInvokeAction();
                }
            }
            case INCOMPLETED:
            {
                return ctx.getStopAction(
                        Appendable.Utils.makeAppendable(message));
            }
            case ERROR:
            {
                throw new TransformationException(getClass().getName() +
                        " transformation error: (" + result.getErrorCode() + ") " +
                        result.getErrorDescription());
            }
        }

        return nextAction;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        final Connection connection = ctx.getConnection();
        final L message = (L) ctx.getMessage();

        final TransformationResult<L, K> result = encoder.transform(connection, message);

        switch(result.getStatus()) {
            case COMPLETED:
            {
                ctx.setMessage(result.getMessage());
                final L remainder = result.getExternalRemainder();
                
                if (encoder.hasInputRemaining(remainder)) {
                    return ctx.getInvokeAction(
                            Appendable.Utils.makeAppendable(remainder));
                } else {
                    return ctx.getInvokeAction();
                }
            }
            case INCOMPLETED:
            {
                return ctx.getStopAction(
                        Appendable.Utils.makeAppendable(message));
            }
            case ERROR:
            {
                throw new TransformationException(getClass().getName() +
                        " transformation error: (" + result.getErrorCode() + ") " +
                        result.getErrorDescription());
            }
        }

        return nextAction;
    }


    @Override
    public Transformer<K, L> getDecoder() {
        return decoder;
    }

    @Override
    public Transformer<L, K> getEncoder() {
        return encoder;
    }

    @Override
    public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
        super.exceptionOccurred(ctx, error);
    }
}
