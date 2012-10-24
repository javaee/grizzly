/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.junit.Test;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileTransferTest {
    
    private static final int PORT = 3773;

    
    // ------------------------------------------------------------ Test Methods


    @Test
    public void testSimpleFileTransfer() throws Exception {
        TCPNIOTransport t = TCPNIOTransportBuilder.newInstance().build();
        FilterChainBuilder builder = FilterChainBuilder.stateless();
        final File f = generateTempFile(1024 * 1024);
        builder.add(new TransportFilter());
        builder.add(new BaseFilter() {
            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                ctx.write(new FileTransfer(f));
                return ctx.getStopAction();
            }
        });
        t.setProcessor(builder.build());
        t.bind(PORT);
        t.start();

        TCPNIOTransport client = TCPNIOTransportBuilder.newInstance().build();
        FilterChainBuilder clientChain = FilterChainBuilder.stateless();
        final SafeFutureImpl<File> future = SafeFutureImpl.create();
        final File temp = File.createTempFile("grizzly-download-", ".tmp");
        temp.deleteOnExit();
        final FileOutputStream out = new FileOutputStream(temp);
        final AtomicInteger total = new AtomicInteger(0);
        clientChain.add(new TransportFilter());
        clientChain.add(new BaseFilter() {
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                ctx.write(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, "."));
                return ctx.getStopAction();
            }

            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {
                Buffer b = ctx.getMessage();
                ByteBuffer bb = b.toByteBuffer();
                total.addAndGet(b.remaining());
                out.getChannel().write(bb);
                if (total.get() == f.length()) {
                    future.result(temp);
                }
                return ctx.getStopAction();
            }
        });
        client.setProcessor(clientChain.build());
        client.start();
        client.connect("localhost", PORT);
        long start = System.currentTimeMillis();
        BigInteger testSum = getMDSum(future.get(10, TimeUnit.SECONDS));
        long stop = System.currentTimeMillis();
        BigInteger controlSum = getMDSum(f);
        assertTrue(controlSum.equals(testSum));
        System.out.println("File transfer completed in " + (stop - start) + " ms.");
    }

    @Test
    public void negativeFileTransferAPITest() throws Exception {
        try {
            new FileTransfer(null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (NullPointerException npe) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }

        try {
            new FileTransfer(null, 0, 1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }
        
        File f = new File("foo");
        try {
            new FileTransfer(f, 0, 1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }
        
        f = new File(System.getProperty("java.io.tmpdir"));
        try {
            new FileTransfer(f, 0, 1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }

        f = File.createTempFile("grizzly-test-", ".tmp");
        new FileOutputStream(f).write(1);
        
        if (f.setReadable(false)) { // skip this check if setReadable returned false
            try {
                new FileTransfer(f, 0, 1);
                fail("Expected IllegalArgumentException to be thrown");
            } catch (IllegalArgumentException iae) {
                //noinspection ResultOfMethodCallIgnored
                f.setReadable(true);
            } catch (Exception e) {
                fail("Unexpected exception type: " + e);
            }
        }

        try {
            new FileTransfer(f, -1, 1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }

        try {
            new FileTransfer(f, 0, -1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }

        try {
            new FileTransfer(f, 2, 1);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException iae) {
        } catch (Exception e) {
            fail("Unexpected exception type: " + e);
        }
    } 
    
    // --------------------------------------------------------- Private Methods
    
    private static BigInteger getMDSum(final File f) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("MD5");  
        byte[] b = new byte[8192];
        FileInputStream in = new FileInputStream(f);
        int len;
        while ((len = in.read(b)) != -1) {
            digest.update(b, 0, len);
        }
        return new BigInteger(digest.digest());
    }
    
    
    private static File generateTempFile(final int size) throws IOException {
        final File f = File.createTempFile("grizzly-temp-" + size, ".tmp");
        Random r = new Random();
        byte[] data = new byte[8192];
        r.nextBytes(data);
        FileOutputStream out = new FileOutputStream(f);
        int total = 0;
        int remaining = size;
        while (total < size) {
            int len = ((remaining > 8192) ? 8192 : remaining);
            out.write(data, 0, len);
            total += len;
            remaining -= len;
        }
        f.deleteOnExit();
        return f;
    }
}
