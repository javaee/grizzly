/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.compression.lzma;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

import java.io.IOException;

public class LZMAFilter extends BaseFilter {

    private final LZMAEncoder encoder = new LZMAEncoder();
    private final LZMADecoder decoder = new LZMADecoder();


    // ----------------------------------------------------- Methods from Filter


    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final Buffer input = ctx.getMessage();
        final TransformationResult<Buffer, Buffer> result =
                decoder.transform(connection, input);

        final Buffer remainder = result.getExternalRemainder();

        if (remainder == null) {
            input.tryDispose();
        } else {
            input.shrink();
        }

        try {
            switch (result.getStatus()) {
                case COMPLETE: {
                    ctx.setMessage(result.getMessage());
                    decoder.finish(connection);
                    return ctx.getInvokeAction(remainder);
                }
                case INCOMPLETE: {
                    return ctx.getStopAction(remainder);
                }
                case ERROR: {
                    throw new IllegalStateException("LZMA decode error. Code: "
                            + result.getErrorCode() + " Description: "
                            + result.getErrorDescription());
                }
                default:
                    throw new IllegalStateException("Unexpected status: " +
                            result.getStatus());
            }
        } finally {
            result.recycle();
        }
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final Buffer input = ctx.getMessage();
        final TransformationResult<Buffer, Buffer> result =
                encoder.transform(connection, input);

        if (!input.hasRemaining()) {
            input.tryDispose();
        } else {
            input.shrink();
        }

        try {
            switch (result.getStatus()) {
                case COMPLETE:
                    encoder.finish(connection);
                case INCOMPLETE: {
                    final Buffer readyBuffer = result.getMessage();
                    if (readyBuffer != null) {
                        ctx.setMessage(readyBuffer);
                        return ctx.getInvokeAction();
                    } else {
                        return ctx.getStopAction();
                    }
                }

                case ERROR: {
                    throw new IllegalStateException("LZMA encode error. Code: "
                            + result.getErrorCode() + " Description: "
                            + result.getErrorDescription());
                }

                default:
                    throw new IllegalStateException("Unexpected status: " +
                            result.getStatus());
            }
        } finally {
            result.recycle();
        }
    }


    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        decoder.release(connection);
        encoder.release(connection);

        return super.handleClose(ctx);
    }

}
