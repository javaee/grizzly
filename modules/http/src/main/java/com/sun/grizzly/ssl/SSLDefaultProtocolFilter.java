/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.ssl;

import com.sun.grizzly.Context;
import com.sun.grizzly.http.DefaultProtocolFilter;
import com.sun.grizzly.http.HttpWorkerThread;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.util.InputReader;
import com.sun.grizzly.util.StreamAlgorithm;
import com.sun.grizzly.util.WorkerThread;
import com.sun.grizzly.util.net.SSLImplementation;
import com.sun.grizzly.util.net.SSLSupport;

import java.net.InetAddress;

/**
 * SSL support over NIO. This {@link com.sun.grizzly.http.Task} handles the SSL requests
 * using a non blocking socket. The SSL handshake is done using this class.
 * Once the handshake is successful, the {@link SSLProcessorTask} is
 * executed.
 *
 * @author Jean-Francois Arcand
 */
public class SSLDefaultProtocolFilter extends DefaultProtocolFilter {
    /**
     * The Coyote SSLImplementation used to retrive the {@link javax.net.ssl.SSLContext}
     */
    protected SSLImplementation sslImplementation;
    
    
    // -------------------------------------------------------------------- //

    /**
     *
     * <p>
     * Invokes {@link com.sun.grizzly.ssl.SSLDefaultProtocolFilter#SSLDefaultProtocolFilter(Class, int, com.sun.grizzly.util.net.SSLImplementation)}
     * with a <code>null</code> {@link InetAddress}.
     * </p>
     *
     * @param algorithmClass the {@link StreamAlgorithm}
     * @param port the network port to associate with this filter
     * @param sslImplementation the {@link SSLImplementation} to associate with
     *  this filter
     *
     * @deprecated Use {@link com.sun.grizzly.ssl.SSLDefaultProtocolFilter#DefaultProtocolFilter(Class, java.net.InetAddress, int)}
     */
    public SSLDefaultProtocolFilter(Class algorithmClass, int port,
            SSLImplementation sslImplementation) {
        super(algorithmClass, port);
        this.sslImplementation = sslImplementation;
    }


    /**
     <p>
     * Constructs a new <code>SSLDefaultProtocolFilter</code>.
     * </p>
     *
     * @param algorithmClass the {@link StreamAlgorithm}
     * @param address the network address to associate with this filter
     * @param port the network port to associate with this filter
     * @param sslImplementation the {@link SSLImplementation} to associate with
     *  this filter
     */
    public SSLDefaultProtocolFilter(Class algorithmClass,
                                    InetAddress address,
                                    int port,
                                    SSLImplementation sslImplementation) {
        super(algorithmClass, address, port);
        this.sslImplementation = sslImplementation;
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureProcessorTask(ProcessorTask processorTask, 
            Context context, StreamAlgorithm streamAlgorithm) {
        super.configureProcessorTask(processorTask, context, streamAlgorithm);
        
        SSLSupport sslSupport = sslImplementation.
                getSSLSupport(((WorkerThread)Thread.currentThread()).getSSLEngine());
        processorTask.setSSLSupport(sslSupport);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configureInputBuffer(final InputReader inputStream,
            final Context context, final HttpWorkerThread workerThread) {
        super.configureInputBuffer(inputStream, context, workerThread);
        inputStream.setSslEngine(workerThread.getSSLEngine());        
        inputStream.setInputBB(workerThread.getInputBB());        
    }
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean isSecure() {
        return true;
    }
}
