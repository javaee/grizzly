/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.util.DataStructures;
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.sun.grizzly.filter.CustomProtocolHelper.logger;
import static com.sun.grizzly.filter.CustomProtocolHelper.log;

/**
 * Stream that blocks on empty {@link RemoteInputStream#read} until  {@link RemoteInputStream#close}
 * is called. This is usefull  in scenarios where a server streams  {@link FragmentMessage} to a client.
 * Enables therefore Client to start processing  bytes as soon as first {@link FragmentMessage} arrives.
 *
 * When finished a Server or Producer of bytes must call {@link RemoteInputStream#close} so that client
 * is released from waiting on this stream.
 *
 * @author John Vieten 24.06.2008
 * @version 1.0
 */
public class RemoteInputStream extends InputStream {
    /**
     * Signals EOF of Stream *
     */
    private static ByteBuffer POISON = ByteBuffer.allocate(1);
    private final static int END = -1;
    private ByteBuffer buffer;
    private final BlockingQueue<ByteBuffer> queue;
    private long timeStamp;
    private  IOException exception;
    private boolean applicationLayerException;




    public RemoteInputStream() {
        queue = DataStructures.getLTQinstance(ByteBuffer.class);
        timeStamp=System.currentTimeMillis();
    }

     public boolean isApplicationLayerException() {
        return applicationLayerException;
    }

    public void setApplicationLayerException(boolean applicationLayerException) {
        this.applicationLayerException = applicationLayerException;
    }

    public void setIOException(IOException exception) {
        this.exception = exception;
        close();
    }



    public long getTimeStamp() {
        return timeStamp;
    }

    /**
     * Adds a ByteBuffer to the stream
     *
     * @param buffer bytes  to add  to inputstream
     */
    public void add(ByteBuffer buffer) {
        try {
            queue.put(buffer);
        } catch (InterruptedException e) {
            logger().log(Level.SEVERE, "add buffer remain:" + buffer.remaining(), e);

        }
    }

    /**
     * Adds a List of ByteBuffers to the Stream
     *
     * @param bList List of ByteBuffer containing stream bytes
     */
    public void add(List<ByteBuffer> bList) {
        try {
            for (ByteBuffer byteBuffer : bList) {
                queue.put(byteBuffer);
            }
        } catch (InterruptedException e) {
            logger().log(Level.SEVERE, "add() buffer ", e);
        }
    }

    private boolean flagClosed = false;

    /**
     * Adds  EOF to end of  Stream.
     * This method must be called so that client stops waiting on this
     * Inputstream.
     */
    public void close() {
        if (!flagClosed) {
            flagClosed = true;
            queue.add(POISON);
        }
    }



    /**
     *  If no bytes are available blocks until {@link RemoteInputStream#close} is called.
     * @return int next byte of the stream
     * @throws IOException
     */
    public int read() throws IOException {
        if (buffer == POISON) return END;
        try {
            while (true) {
                if (buffer == null) {
                    buffer = queue.take();
                    if (buffer == POISON) {
                        log("poison taken");
                        if(exception!=null) throw exception;
                        break;
                    }
                }
                if (buffer.hasRemaining()) {
                    return buffer.get() & 0xff;
                } else {
                    buffer = null;
                }
            }
        } catch (InterruptedException e) {
            logger().log(Level.SEVERE, "read() buffer remain:" + buffer, e);
        }
        log("buffer -1 on interrupt");
        return END;
    }

   /**
     *  If no bytes are available blocks until {@link RemoteInputStream#close} is called
     * @param bytes byte array to be filled
     * @param off  offset
     * @param len  size
     * @return next bytes of the stream
     * @throws IOException
     */
    public int read(final byte[] bytes, final int off, final int len) throws IOException {
        log("start read(): off" + off + ";len:" + len);
        if (len == 0) {
            return 0;
        }
        if (buffer == POISON) return END;
        try {
            int countBytes = 0;
            while (true) {
                if (buffer == null) {
                    buffer = queue.take();
                    if (buffer == POISON) {
                        log("read(...) poison");
                        if(exception!=null) throw exception;
                        break;
                    }
                }
                if (!buffer.hasRemaining()) {
                    buffer = null;
                    continue;
                }
                log(buffer + "buffer.remaining():" + buffer);
                final int size = Math.min(buffer.remaining(), len - countBytes);
                buffer.get(bytes, off + countBytes, size);

                countBytes += size;

                if (countBytes == len) {
                    log("break");
                    break;
                }
            }
            return countBytes == 0 ? END : countBytes;
        } catch (InterruptedException e) {
            logger().log(Level.SEVERE, "Interrupt", e);
        }
        log("read(...) send -1 ");
        return END;
    }


    public long skip(final long n) throws IOException {
        throw new UnsupportedOperationException("skip()");
    }

    public int available() throws IOException {
        throw new UnsupportedOperationException("available()");
    }


}
