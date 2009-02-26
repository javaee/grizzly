/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
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
                        protocolRequest.getChannel(), tmpBuffer, 2);
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
}
