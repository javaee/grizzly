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

import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.BufferOutputStream;

import java.io.IOException;

/**
 * ThriftServerFilter is a server-side filter for Thrift RPC processors.
 * <p/>
 * You can set the specific response size by constructor for optimal performance.
 * <p/>
 * Usages:
 * <pre>
 * {@code
 * final FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
 * final user-generated.thrift.Processor tprocessor = new user-generated.thrift.Processor(new user-generated.thrift.Handler);
 * <p/>
 * serverFilterChainBuilder.add(new TransportFilter()).add(new ThriftFrameFilter()).add(new ThriftServerFilter(tprocessor));
 * <p/>
 * final TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
 * transport.setProcessor(serverFilterChainBuilder.build());
 * transport.bind(port);
 * transport.start();
 * // release
 * //...
 * }
 * </pre>
 *
 * @author Bongjae Chang
 */
public class ThriftServerFilter extends BaseFilter {

    private static final int THRIFT_DEFAULT_RESPONSE_BUFFER_SIZE = 4096;

    private final TProcessor processor;
    private final TProtocolFactory protocolFactory;
    private final int responseSize;

    public ThriftServerFilter(final TProcessor processor) {
        this(processor, new TBinaryProtocol.Factory(), THRIFT_DEFAULT_RESPONSE_BUFFER_SIZE);
    }

    public ThriftServerFilter(final TProcessor processor, final TProtocolFactory protocolFactory) {
        this(processor, protocolFactory, THRIFT_DEFAULT_RESPONSE_BUFFER_SIZE);
    }

    public ThriftServerFilter(final TProcessor processor, final int responseSize) {
        this(processor, new TBinaryProtocol.Factory(), responseSize);
    }

    public ThriftServerFilter(final TProcessor processor, final TProtocolFactory protocolFactory, final int responseSize) {
        this.processor = processor;
        if (protocolFactory == null) {
            this.protocolFactory = new TBinaryProtocol.Factory();
        } else {
            this.protocolFactory = protocolFactory;
        }
        if (responseSize < THRIFT_DEFAULT_RESPONSE_BUFFER_SIZE) {
            this.responseSize = THRIFT_DEFAULT_RESPONSE_BUFFER_SIZE;
        } else {
            this.responseSize = responseSize;
        }
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        if (processor == null) {
            throw new IllegalArgumentException("TProcessor could not be null");
        }
        final Buffer input = ctx.getMessage();
        if (input == null) {
            throw new IOException("input message could not be null");
        }
        if (!input.hasRemaining()) {
            return ctx.getStopAction();
        }

        final MemoryManager memoryManager = ctx.getMemoryManager();
        final BufferOutputStream outputStream = new BufferOutputStream(memoryManager, memoryManager.allocate(responseSize));
        final TTransport ttransport = new TGrizzlyServerTransport(input, outputStream);
        final TProtocol protocol = protocolFactory.getProtocol(ttransport);
        try {
            processor.process(protocol, protocol);
        } catch (TException te) {
            ttransport.close();
            input.dispose();
            outputStream.getBuffer().dispose();
            throw new IOException(te);
        }
        input.dispose();
        final Buffer output = outputStream.getBuffer();
        output.trim();
        output.allowBufferDispose(true);
        ctx.write(ctx.getAddress(), output, null);
        try {
            outputStream.close();
        } catch (IOException ignore) {
        }
        ttransport.close();
        return ctx.getStopAction();
    }
}
