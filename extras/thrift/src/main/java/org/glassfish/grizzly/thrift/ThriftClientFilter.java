/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.thrift;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.DataStructures;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * ThriftClientFilter is a client-side filter for Thrift RPC processors.
 * <p/>
 * Read-messages will be queued in LinkedBlockingQueue from which TGrizzlyClientTransport will read it.
 * <p/>
 * Usages:
 * <pre>
 * {@code
 * final FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
 * clientFilterChainBuilder.add(new TransportFilter()).add(new ThriftFrameFilter()).add(new ThriftClientFilter());
 * <p/>
 * final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
 * transport.setProcessor(clientFilterChainBuilder.build());
 * transport.start();
 * Future<Connection> future = transport.connect(ip, port);
 * final Connection connection = future.get(10, TimeUnit.SECONDS);
 * <p/>
 * final TTransport ttransport = TGrizzlyClientTransport.create(connection);
 * final TProtocol tprotocol = new TBinaryProtocol(ttransport);
 * user-generated.thrift.Client client = new user-generated.thrift.Client(tprotocol);
 * client.ping();
 * // execute more works
 * // ...
 * // release
 * ttransport.close();
 * connection.close();
 * transport.stop();
 * }
 * </pre>
 *
 * @author Bongjae Chang
 */
public class ThriftClientFilter extends BaseFilter {

    private final BlockingQueue<Buffer> inputBuffersQueue =
            DataStructures.getLTQInstance();

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        final Buffer input = ctx.getMessage();
        if (input == null) {
            throw new IOException("input message could not be null");
        }
        if (!input.hasRemaining()) {
            return ctx.getStopAction();
        }
        inputBuffersQueue.offer(input);
        return ctx.getStopAction();
    }

    public final BlockingQueue<Buffer> getInputBuffersQueue() {
        return inputBuffersQueue;
    }
}
