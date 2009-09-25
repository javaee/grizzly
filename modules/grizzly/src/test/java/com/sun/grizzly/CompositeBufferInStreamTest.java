/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2003-2008 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly;

import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.TransportFilter;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;
import com.sun.grizzly.utils.CompositeBuffer;
import com.sun.grizzly.utils.Pair;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;

/**
 * Test how {@link CompositeBuffer} works with Streams.
 * 
 * @author Alexey Stashok
 */
public class CompositeBufferInStreamTest extends TestCase {
    public static final int PORT = 7783;

    public void testCompositeBuffer() throws Exception {
        Connection connection = null;
        final TCPNIOTransport transport = TransportFactory.getInstance().createTCPTransport();

        final Buffer portion1 = MemoryUtils.wrap(transport.getMemoryManager(), "Hello");
        final Buffer portion2 = MemoryUtils.wrap(transport.getMemoryManager(), " ");
        final Buffer portion3 = MemoryUtils.wrap(transport.getMemoryManager(), "world!");

        final FutureImpl lock1 = new FutureImpl();
        final FutureImpl lock2 = new FutureImpl();
        final FutureImpl lock3 = new FutureImpl();

        final Pair<Buffer, FutureImpl>[] portions = new Pair[]{
            new Pair<Buffer, FutureImpl>(portion1, lock1),
            new Pair<Buffer, FutureImpl>(portion2, lock2),
            new Pair<Buffer, FutureImpl>(portion3, lock3)
        };

        try {
            transport.bind(PORT);
            transport.getFilterChain().add(new TransportFilter());
            transport.getFilterChain().add(new StepsFilter(portions));
            
            transport.start();

            Future<Connection> future = transport.connect("localhost", PORT);
            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            final StreamWriter writer = connection.getStreamWriter();

            for(Pair<Buffer, FutureImpl> portion : portions) {
                final Buffer buffer = portion.getFirst();
                final Future locker = portion.getSecond();
                
                writer.writeBuffer(buffer);
                final Future writeFuture = writer.flush();
                writeFuture.get(5000, TimeUnit.MILLISECONDS);

                locker.get(5000, TimeUnit.MILLISECONDS);
            }

            assertTrue(true);
            
        } finally {
            if (connection != null) {
                connection.close();
            }

            transport.stop();
            TransportFactory.getInstance().close();
        }
    }

    public class StepsFilter extends FilterAdapter {
        private final Attribute<Integer> indexAttribute =
                Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("index", 0);
        
        private final Pair<Buffer, FutureImpl>[] portions;

        public StepsFilter(Pair<Buffer, FutureImpl>... portions) {
            this.portions = portions;
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx,
                NextAction nextAction) throws IOException {

            final Connection connection = ctx.getConnection();
            final StreamReader reader = ctx.getStreamReader();
            final Buffer compositeBuffer = reader.asReadOnlyBufferWindow();

            int index = indexAttribute.get(connection);
            final FutureImpl currentLocker = portions[index].getSecond();

            for(int i=0; i<index; i++) {
                final Buffer currentBuffer = portions[i].getFirst();
                for(int j=0; j<currentBuffer.limit(); j++) {
                    if (!compositeBuffer.hasRemaining()) {
                        return ctx.getStopAction();
                    }

                    final byte found = compositeBuffer.get();
                    final byte expected = currentBuffer.get(j);
                    if (found != expected) {
                        currentLocker.failure(new IllegalStateException(
                                "CompositeBuffer content is broken. Offset: " +
                                compositeBuffer.position() + " found: " + found +
                                " expected: " + expected));
                        return ctx.getStopAction();
                    }
                }
            }

            currentLocker.setResult(index);
            indexAttribute.set(connection, index + 1);
            
            return nextAction;
        }

    }
}
