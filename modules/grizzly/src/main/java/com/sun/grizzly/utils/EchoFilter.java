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

package com.sun.grizzly.utils;

import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.logging.Filter;
import java.util.logging.Logger;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.streams.AddressableStreamReader;
import com.sun.grizzly.streams.AddressableStreamWriter;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;

/**
 * Echo {@link Filter} implementation
 * 
 * @author Alexey Stashok
 */
public class EchoFilter extends FilterAdapter {
    private static final Logger logger = Grizzly.logger;
    
    @Override
    public NextAction handleRead(final FilterChainContext ctx,
            final NextAction nextAction) throws IOException {
        final Object message = ctx.getMessage();

        if (message != null) {
            final Connection connection = ctx.getConnection();
            final Transformer encoder = ctx.getFilterChain().getCodec().getEncoder();
            final TransformationResult<Buffer> result =
                    encoder.transform(connection, message, null);
            encoder.release(connection);
            final Buffer buffer = result.getMessage();
            final Object address = ctx.getAddress();
            connection.write(address, buffer);
        } else {
            final StreamReader reader = ctx.getStreamReader();
            final StreamWriter writer = ctx.getStreamWriter();

            if (writer instanceof AddressableStreamWriter) {
                final AddressableStreamReader addressableReader =
                        (AddressableStreamReader) reader;
                // pull the buffer, if it wasn't
                addressableReader.getBuffer();

                final Object peerAddress = addressableReader.getPeerAddress();
                final AddressableStreamWriter addressableWriter =
                        (AddressableStreamWriter) writer;
                if (addressableWriter.getPeerAddress() == null) {
                    addressableWriter.setPeerAddress(peerAddress);
                }
            }
            writer.writeStream(reader);
            writer.flush();
        }

        return nextAction;
    }
}
