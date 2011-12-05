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

package com.sun.grizzly.http.portunif;

import com.sun.grizzly.Context;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.portunif.ProtocolHandler;
import com.sun.grizzly.util.Utils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

/**
 * Redirect the request to the proper protocol, which can be http or https.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class HttpProtocolHandler implements ProtocolHandler {
    public enum Mode {
        HTTP, HTTPS, HTTP_HTTPS;
    }
    
    private static final int DEFAULT_HTTP_HEADER_BUFFER_SIZE = 48 * 1024;
    
    /**
     * The protocols supported by this handler.
     */
    protected String[][] protocols = {{"http"}, {"https"}, {"https", "http"}};
    
    private Mode mode;
    
    public HttpProtocolHandler() {
        mode = Mode.HTTP;
    }
    
    public HttpProtocolHandler(Mode mode) {
        this.mode = mode;
    }

    /**
     * Redirect the request to the protocol defined in the
     * <code>protocolInfo</code>. Protocols supported are http and https.
     *
     * @param protocolInfo The protocol that needs to be redirected.
     */
    public boolean handle(Context context, PUProtocolRequest protocolRequest) throws IOException {
        if (protocolRequest.getProtocolName().equalsIgnoreCase("https")) {
            HttpRedirector.redirectSSL(context, protocolRequest);
        } else {
            HttpRedirector.redirect(context, protocolRequest);
        }
        
        /* ======================================================
         * Java HTTP(S) client sends request in 2 chunks: header, payload
         * We need to make sure client started to send payload before redirecting/closing
         * the connection. Otherwise client can not receive "HTTP 302 redirect" response.
         */ 
        ByteBuffer tmpBuffer = protocolRequest.getByteBuffer();
        tmpBuffer.clear();
        try {
            int totalReadBytes = 0;
            
            while(tmpBuffer.hasRemaining() && totalReadBytes < DEFAULT_HTTP_HEADER_BUFFER_SIZE) {
                int count = Utils.readWithTemporarySelector(
                        protocolRequest.getChannel(), tmpBuffer, 20).bytesRead;
                if (count == -1) {
                    break;
                }
                totalReadBytes += count;
            }
        } catch(IOException e) {
            // ignore.
        } finally {
            tmpBuffer.clear();
        }
        //=========================================================
        
        return false;
    }
    
    
    /**
     * Returns an array of supported protocols.
     * @return an array of supported protocols.
     */
    public String[] getProtocols() {
        return protocols[mode.ordinal()];
    }
    
    
    /**
     * Invoked when the SelectorThread is about to expire a SelectionKey.
     * @return true if the SelectorThread should expire the SelectionKey, false
     *              if not.
     */
    public boolean expireKey(SelectionKey key){
        return true;
    }

    public ByteBuffer getByteBuffer() {
        return null;
    }
}
