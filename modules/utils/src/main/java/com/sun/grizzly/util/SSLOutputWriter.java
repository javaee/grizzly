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

package com.sun.grizzly.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;

/**
 * SSL over NIO utility to encrypt {@link ByteBuffer} and flush them.
 * All the SSLEngine operations are delegated to class {@link SSLUtils}
 *
 * @author Jeanfrancois Arcand
 */
public final class SSLOutputWriter{

    /**
     * Encrypt the response and flush it using {@link OutputWriter}
     * @param channel  {@link SelectableChannel} to flush
     * @param bb  {@link ByteBuffer}
     * @return  number of bytes written
     * @throws java.io.IOException 
     */     
    public static long flushChannel(SelectableChannel channel, ByteBuffer bb)
            throws IOException{
      
        WorkerThread workerThread = (WorkerThread)Thread.currentThread();
        SSLEngine sslEngine = workerThread.getSSLEngine();
        ByteBuffer outputBB = workerThread.getOutputBB();
        return flushChannel(channel,bb,outputBB,sslEngine);
    }
    
    
    /**
     * Encrypt the response and flush it using {@link OutputWriter}
     * @param channel  {@link SelectableChannel} to flush 
     * @param bb  input {@link ByteBuffer}
     * @param outputBB  output {@link ByteBuffer}
     * @param sslEngine {@link SSLEngine}
     * @return  number of bytes written
     * @throws java.io.IOException 
     */     
    public static long flushChannel(SelectableChannel channel, ByteBuffer bb,
            ByteBuffer outputBB, SSLEngine sslEngine) throws IOException{  

        long nWrite = 0;
        while (bb.hasRemaining()) {
            SSLEngineResult result = SSLUtils.wrap(bb,outputBB,sslEngine);
            switch (result.getStatus()) {
                case OK:
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        SSLUtils.executeDelegatedTask(sslEngine);
                    }
                    break;

                default:
                    throw new IOException("SSLOutputWriter: " + result.getStatus());
            }

            if (outputBB.hasRemaining()) {
                nWrite += OutputWriter.flushChannel(channel,outputBB);
            }
        }
        outputBB.clear();
        return nWrite;
    }
}
