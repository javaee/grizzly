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
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import com.sun.grizzly.util.buf.Ascii;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import javax.net.ssl.SSLEngine;

/**
 * Utility class used to redirect an HTTP(s) request to another protocol and
 * endpoint. The following scenarios are supported:
 *
 * (1) http://host:port1 to https://host:port1
 * (2) https://host:port1 to http://host:port1
 * (3) http://host:port2 to https://host:port1
 * (4) https://host:port2 to https://host:port1
 * (5) http://host:port2 to http://host:port1
 * (6) https://host:port2 to http://host:port1
 *
 * This class internally start an NIO Selector listening on an 'external' port
 * to a 'redirect' port. All requests to the external port,
 * independently of the protocol are redirected to the 'redirect' port.
 *
 * @author Jeanfrancois Arcand
 */
public class HttpRedirector {
    
    /**
     * HTTP end line.
     */
    private final static String NEWLINE = "\r\n";
    

    /**
     * Header String.
     */
    private final static String headers = NEWLINE + "Connection:close" + NEWLINE
                + "Cache-control: private" + NEWLINE
                + NEWLINE;
    
    
    /**
     * HTTP SC_FOUND header.
     */
    private final static ByteBuffer SC_FOUND =
            ByteBuffer.wrap( ("HTTP/1.1 302 Moved Temporarily" 
                + NEWLINE).getBytes());
    
    
    // ------------------------------------------------------------ Constructors
    
    
    private HttpRedirector(){
    }


    // ---------------------------------------------------------- Public Methods

    
    /**
     * Redirect a secure request (https) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param protocolRequest PortUnification protocol request information
     */
    public static void redirectSSL(Context context,
                                   PUProtocolRequest protocolRequest)
    throws IOException {
        
        /*
         * By default if HTTPS request is going to be redirected - it it will 
         * be redirected to HTTP
         */
        redirectSSL(context, protocolRequest, false);
    }
    
    
    /**
     * Redirect a secure request (https) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param protocolRequest PortUnification protocol request information
     * @param redirectToSecure if true - request will be redirected to 
     * HTTPS protocol, otherwise HTTP
     */
    public static void redirectSSL(Context context,
                                   PUProtocolRequest protocolRequest,
                                   boolean redirectToSecure)
    throws IOException {
        redirectSSL(context,
                    protocolRequest.getSSLEngine(),
                    protocolRequest.getByteBuffer(),
                    protocolRequest.getSecuredOutputByteBuffer(),
                    null,
                    redirectToSecure);

    }


    /**
     * Redirect a secure request (https) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param sslEngine the sslEngine servicing the current request
     * @param byteBuffer input buffer
     * @param outputBB output buffer
     * @param redirectPort the port to redirect to.  If not specified, the
     *  port of the current socket will be used
     * @param redirectToSecure if <code>true</code>, the request will be
     *  redirected using the <code>HTTPS</code> protocol, otherwise
     *  <code>HTTP</code>
     */
    public static void redirectSSL(Context context,
                                   SSLEngine sslEngine,
                                   ByteBuffer byteBuffer,
                                   ByteBuffer outputBB,
                                   Integer redirectPort,
                                   boolean redirectToSecure)
    throws IOException {

        String host = createHostString(context, byteBuffer, redirectPort);
        redirectSSL(context, sslEngine, outputBB, redirectToSecure
                ? "Location: https://" + host
                : "Location: http://" + host);
    }




    /**
     * Redirect a un-secure request (http) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param protocolRequest PortUnification protocol request information
     */
    public static void redirect(Context context,
                                PUProtocolRequest protocolRequest)
    throws IOException {
        /*
         * By default if HTTP request is going to be redirected - it it will 
         * be redirected to HTTPS
         */
        redirect(context, protocolRequest, true);
    }
    
    
    /**
     * Redirect a un-secure request (http) to http or https.
     * @param context {@link Context} of the occured 
     * {@link java.nio.channels.SelectionKey} event
     * @param protocolRequest PortUnification protocol request information
     * @param redirectToSecure if true - request will be redirected to 
     * HTTPS protocol, otherwise HTTP
     */
    public static void redirect(Context context,
                                PUProtocolRequest protocolRequest,
                                boolean redirectToSecure)
    throws IOException {
        redirect(context,
                protocolRequest.getByteBuffer(),
                null,
                redirectToSecure);
    }

    /**
     * Redirect a un-secure request (http) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param byteBuffer input buffer
     * @param redirectPort the port to redirect to.  If not specified, the 
     *  port of the current socket will be used
     * @param redirectToSecure if true - request will be redirected to
     * HTTPS protocol, otherwise HTTP
     */
    public static void redirect(Context context,
                                ByteBuffer byteBuffer,
                                Integer redirectPort,
                                boolean redirectToSecure) throws IOException {

        String host = createHostString(context, byteBuffer, redirectPort);
        redirect(context, redirectToSecure
                    ? "Location: https://" + host
                    : "Location: http://" + host);
    }


    // --------------------------------------------------------- Private Methods


    /**
     * Redirect a secure request (http) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param httpHeaders HTTP headers, which will be sent with response
     */
    private static void redirect(Context context, String httpHeaders)
    throws IOException{

        final SocketChannel channel =
                (SocketChannel) context.getSelectionKey().channel();

        final ByteBuffer[] byteBuffers = new ByteBuffer[2];
        final ByteBuffer statusLineBuffer = SC_FOUND.slice();
        final ByteBuffer headersBuffer = ByteBuffer.wrap((httpHeaders +
                new String((byte[]) context.getAttribute(HttpProtocolFinder.HTTP_REQUEST_URL)) +
                headers).getBytes());
        byteBuffers[0] = statusLineBuffer;
        byteBuffers[1] = headersBuffer;
        
        OutputWriter.flushChannel(channel, byteBuffers);
    }


    /**
     * Redirect a secure request (https) to http or https.
     * @param context {@link Context} of the current
     * {@link java.nio.channels.SelectionKey} event
     * @param sslEngine the sslEngine servicing the current request
     * @param outputBB output buffer
     * @param httpHeaders HTTP headers, which will be sent with response
     */
    private static void redirectSSL(Context context,
                                    SSLEngine sslEngine,
                                    ByteBuffer outputBB,
                                    String httpHeaders) throws IOException {

        final SocketChannel channel =
                (SocketChannel) context.getSelectionKey().channel();

        final ByteBuffer statusLineBuffer = SC_FOUND.slice();
        final ByteBuffer headersBuffer = ByteBuffer.wrap((httpHeaders +
                new String((byte[]) context.getAttribute(HttpProtocolFinder.HTTP_REQUEST_URL)) +
                headers).getBytes());
        
        // Do extra copy, but anyways it's cheaper than calling flushChannel
        // two time for each buffer
        final ByteBuffer singleBuffer = ByteBuffer.allocate(
                statusLineBuffer.remaining() + headersBuffer.remaining());
        singleBuffer.put(statusLineBuffer);
        singleBuffer.put(headersBuffer);
        singleBuffer.flip();
        
        SSLOutputWriter.flushChannel(channel,
                singleBuffer,
                outputBB,
                sslEngine);
    }


    private static String createHostString(Context context,
                                           ByteBuffer byteBuffer,
                                           Integer redirectPort) {

        boolean portSpecified =  (redirectPort != null);
        String host = parseHost(byteBuffer, !portSpecified);

        if (host == null) {
            Socket s = ((SocketChannel) context.getSelectionKey().channel()).socket();
            InetAddress address = s.getLocalAddress();
            // potential perf issue here....
            host = address.getHostName();
            host += ':' + Integer.toString(((portSpecified) ? redirectPort : s.getLocalPort()));
        } else {
            if (portSpecified) {
                host = host + ':' + Integer.toString(redirectPort);
            }
        }
        return host;

    }


    
    /**
     * Return the host value, or null if not found.
     * @param byteBuffer the request bytes.
     */
    private static String parseHost(ByteBuffer byteBuffer, boolean includePort){

        int curPosition = byteBuffer.position();
        int curLimit = byteBuffer.limit();

        // Rule a - If nothing, return to the Selector.
        if (byteBuffer.position() == 0)
            return null;
       
        byteBuffer.position(0);
        byteBuffer.limit(curPosition);
        

        int state =0;
        int start =0;
        int end = 0;        
        
        try {                         
            byte c;            
            
            // Rule b - try to determine the host header
            while(byteBuffer.hasRemaining()) {
                c = (byte)Ascii.toLower(byteBuffer.get());
                switch(state) {
                    case 0: // Search for first 'h'
                        if (c == 0x68){
                            state = 1;      
                        } else {
                            state = 0;
                        }
                        break;
                    case 1: // Search for next 'o'
                        if (c == 0x6f){
                            state = 2;
                        } else {
                            state = 0;
                        }
                        break;
                    case 2: // Search for next 's'
                        if (c == 0x73){
                            state = 3;
                        } else {
                            state = 0;
                        }
                        break;
                    case 3: // Search for next 't'
                        if (c == 0x74){
                            state = 4;
                        } else {
                            state = 0;
                        }
                        break; 
                    case 4: // Search for next ':'
                        if (c == 0x3a){
                            state = 5;
                        } else {
                            state = 0;
                        }
                        break;     
                    case 5: // Get the Host                  
                        StringBuilder sb = new StringBuilder();
                        while (c != 0x0d && c != 0x0a) {
                            if (c == 0x3a && !includePort) {
                                break;
                            }
                            sb.append((char) c);
                            c = byteBuffer.get();
                        }
                        return sb.toString().trim();                        
                    default:
                        throw new IllegalArgumentException("Unexpected state");
                }      
            }
            return null;
        } catch (BufferUnderflowException bue) {
            return null;
        } finally {     
            if ( end > 0 ){
                byteBuffer.position(start);
                byteBuffer.limit(end);
            } else {
                byteBuffer.limit(curLimit);
                byteBuffer.position(curPosition);                               
            }
        }       
    }
    
}

