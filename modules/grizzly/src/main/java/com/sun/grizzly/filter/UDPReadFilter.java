/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.Context;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;

/**
 * Simple ProtocolFilter implementation which read the available bytes
 * and delegate the processing to the next ProtocolFilter in the ProtocolChain.
 * If no bytes are available, no new ProtocolHandler will be a invoked and 
 * the connection (SelectionKey) will be cancelled.
 *
 * @deprecated The ReadFilter can be used for both TCP and UDP.  
 * @author Jeanfrancois Arcand
 */
public class UDPReadFilter extends ReadFilter{

    
    public final static String UDP_SOCKETADDRESS = "socketAddress";
    
    
    public UDPReadFilter(){
    }
     
    /**
     * Read available bytes and delegate the processing of them to the next
     * ProtocolFilter in the ProtocolChain.
     * @return <tt>true</tt> if the next ProtocolFilter on the ProtocolChain
     *                       need to bve invoked.
     */
    @Override
    public boolean execute(Context ctx) throws IOException {
        boolean result = true;
        SocketAddress socketAddress = null;
        DatagramChannel datagramChannel = null;
        Exception exception = null;
        SelectionKey key = ctx.getSelectionKey();
        key.attach(null);
        
        ByteBuffer byteBuffer = 
                ((WorkerThread)Thread.currentThread()).getByteBuffer();
        try {
            datagramChannel = (DatagramChannel)key.channel();
            socketAddress = datagramChannel.receive(byteBuffer);   
        } catch (IOException ex) {
            exception = ex;
            log("UDPReadFilter.execute",ex);
        } catch (RuntimeException ex) {
            exception = ex;    
            log("UDPReadFilter.execute",ex);
        } finally {                               
            if (exception != null){
                ctx.setAttribute(Context.THROWABLE,exception);
                result = false;
            } else if (socketAddress == null){
                ctx.setKeyRegistrationState(
                        Context.KeyRegistrationState.REGISTER);    
                result = false;
            } else {
                ctx.setAttribute(UDP_SOCKETADDRESS,socketAddress);
            }
        }       
        return result;
    }
}
