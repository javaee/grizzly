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

package com.sun.grizzly.samples.udpecho;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChain;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;

/**
 * Implementation of {@link FilterChain} filter, which replies with the request
 * message.
 * In UDP {@link EchoServer} sample, we initialize {@link TransportFilter} to
 * operate in message mode, which fits better for UDP transport, so
 * <tt>EchoFilter</tt>, unlike in TCP transport example, should operate with
 * context message, not streams.
 *
 * @author Alexey Stashok
 */
public class EchoFilter extends FilterAdapter {
    private static final Logger logger = Logger.getLogger(EchoFilter.class.getName());
    
    /**
     * Handle just read operation, when some message has come and ready to be
     * processed.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @param nextAction default {@link NextAction} filter chain will execute
     *                   after processing this {@link Filter}. Could be modified.
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        // Echo the input message, passing it throw a FilterChain transformer

        // incoming message
        final Object message = ctx.getMessage();
        // peer address
        final Object address = ctx.getAddress();
        // connection, where response will be sent
        final Connection connection = ctx.getConnection();
        // FilterChain
        final FilterChain filterChain = ctx.getFilterChain();

        // FilterChain encoder, which will pass throw all FilterChain Filters,
        // so each of them will be able to transform the message, before it
        // will be sent on a wire
        final Transformer encoder = filterChain.getCodec().getEncoder();

        // transform the message. Each CodecFilter in FilterChain is able
        // to take part in message transformation
        TransformationResult<Buffer> result = encoder.transform(connection,
                message, null);
        if (result.getStatus() == Status.COMPLETED) {
            Buffer echoMessage = result.getMessage();
            logger.info("Echo '" +
                    echoMessage.contentAsString(Charset.defaultCharset()) +
                    "' address: " + address);
            connection.write(address, echoMessage);
        } else {
            throw new IllegalStateException("Can not transform the message");
        }

        encoder.release(connection);

        return nextAction;
    }

}
