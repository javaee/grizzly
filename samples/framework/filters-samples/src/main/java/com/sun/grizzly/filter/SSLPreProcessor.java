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

import com.sun.grizzly.async.AsyncQueueDataProcessor;
import com.sun.grizzly.util.ThreadAttachment;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * @author John Vieten 20.10.2008
 * @version 1.0
 */
public class SSLPreProcessor implements AsyncQueueDataProcessor {
    public final static String SSL_PRE_PROCESSOR_KEY = "SSL_PRE_PROCESSOR_KEY";
    private int inputBBSize = 5 * 4096;
    private SSLEngine sslEngine;
    private ByteBuffer securedOutputBuffer;
    private ReentrantLock writeLock = new ReentrantLock();


    public SSLPreProcessor(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;

        int expected = sslEngine.getSession().getApplicationBufferSize();
        this.securedOutputBuffer = ByteBuffer.allocate(Math.max(inputBBSize, expected));
        sslEngine.getSession().putValue(SSL_PRE_PROCESSOR_KEY, this);
        securedOutputBuffer.flip();
    }


    public ByteBuffer getInternalByteBuffer() {
        return securedOutputBuffer;
    }

    public void process(ByteBuffer byteBuffer) throws IOException {
        if (!byteBuffer.hasRemaining() || securedOutputBuffer.hasRemaining()) return;
        securedOutputBuffer.clear();

        try {
            try {
                writeLock.lock();
                SSLEngineResult result = sslEngine.wrap(byteBuffer, securedOutputBuffer);
            } finally {
                writeLock.unlock();
            }
            securedOutputBuffer.flip();
        } catch (Exception e) {
            securedOutputBuffer.position(securedOutputBuffer.limit());
            throw new IOException(e.getMessage());
        }
    }

    public static SSLPreProcessor fromSelectionKey(SelectionKey key) {
        Object attachmentObj = key.attachment();
        if(!(attachmentObj instanceof ThreadAttachment)) {
            CustomProtocolHelper.log("SelectionKey : "+key+ " without SSLEngine. Maybe key is not ready yet.");
            return null;
        }
        ThreadAttachment attachment = (ThreadAttachment) attachmentObj;
        SSLEngine sslEngine = attachment.getSSLEngine();
        if(sslEngine==null) {
           CustomProtocolHelper.log("SelectionKey : "+key+ " without SSLEngine. Maybe key is not ready yet.");
           return null;

        }
        synchronized (sslEngine) {
            SSLPreProcessor preProcessor =
                    (SSLPreProcessor) sslEngine.getSession().getValue(SSLPreProcessor.SSL_PRE_PROCESSOR_KEY);
            if(preProcessor==null) {
              preProcessor = new SSLPreProcessor(sslEngine);
                 sslEngine.getSession().putValue(SSLPreProcessor.SSL_PRE_PROCESSOR_KEY,preProcessor);
            }
            return preProcessor;
        }

    }


}
