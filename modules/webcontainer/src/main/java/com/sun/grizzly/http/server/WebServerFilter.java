/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.http.server;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.filterchain.BaseFilter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.HttpCodecFilter;
import com.sun.grizzly.http.HttpContent;
import com.sun.grizzly.http.HttpRequestPacket;
import com.sun.grizzly.http.HttpResponsePacket;
import com.sun.grizzly.http.server.apapter.Adapter;
import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.HexUtils;
import com.sun.grizzly.http.util.MimeHeaders;
import com.sun.grizzly.memory.MemoryManager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static com.sun.grizzly.http.server.embed.GrizzlyWebServer.ServerConfiguration;

/**
 * TODO:
 *   JMX
 *   Statistics
 *   Interceptor support (necessary for FileCache?)
 */
public class WebServerFilter extends BaseFilter {

    private final ServerConfiguration config;


    private KeepAliveStats keepAliveStats = null;

    private static Attribute<Integer> keepAliveCounterAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            "connection-keepalive-counter", 0);
    

    // ------------------------------------------------------------ Constructors


    public WebServerFilter(ServerConfiguration config) {

        this.config = config;

    }


    // ----------------------------------------------------- Methods from Filter


    @Override public NextAction handleRead(FilterChainContext ctx)
          throws IOException {

        // Otherwise cast message to a HttpContent
        final HttpContent httpContent = (HttpContent) ctx.getMessage();

        HttpResponsePacket response = HttpResponsePacket.builder().build();

        if (!prepareRequest((HttpRequestPacket) httpContent.getHttpHeader(), response)) {
            // invalid request - marshal the response to the client
            ctx.write(response);
            return ctx.getStopAction();
        }

        HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();
        // TODO we should cache these
        GrizzlyRequest req = new GrizzlyRequest();
        req.initialize(request, ctx);
        GrizzlyResponse res = new GrizzlyResponse(false, false);
        res.initialize(req, response, ctx);
        //prepareProcessing(ctx, request, response);
        Adapter adapter = config.getAdapter();

        try {
            adapter.service(req, res);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        response.finish();
        return ctx.getStopAction();

    }


    // --------------------------------------------------------- Private Methods


    /**
     * Prepares the request for further processing.  If there is a problem
     * with the protocol request itself, the response will be updated with
     * the appropriate cause and will return <code>false</code> indicating
     * an issue with the request.
     *
     * @param request
     * @param response
     * @return
     */
    protected boolean prepareRequest(HttpRequestPacket request,
                                     HttpResponsePacket response) {

        boolean http11 = false;

//        http11 = true;
//        http09 = false;
//        contentDelimitation = false;
//        if (sslSupport != null) {
//            request.scheme().setString("https");
//        }
        BufferChunk protocolBC = request.getProtocolBC();
        if (protocolBC.equals(HttpCodecFilter.HTTP_1_1)) {
            protocolBC.setString(HttpCodecFilter.HTTP_1_1);
            response.setProtocol(HttpCodecFilter.HTTP_1_1);
            http11 = true;
        } else if (protocolBC.equals(HttpCodecFilter.HTTP_1_0)) {
            //http11 = false;
            //keepAlive = false;
            response.setProtocol(HttpCodecFilter.HTTP_1_1);
            protocolBC.setString(HttpCodecFilter.HTTP_1_1);
        //} else if (protocolBC.equals("")) {
            // HTTP/0.9
            //http09 = true;
            //http11 = false;
            //keepAlive = false;
        } else {
            // Unsupported protocol
            //http11 = false;
            //error = true;
            // Send 505; Unsupported HTTP version
            response.setStatus(505);
            response.setProtocol(HttpCodecFilter.HTTP_1_1);
            response.setReasonPhrase("Unsupported Protocol Version");
        }

        BufferChunk methodBC = request.getMethodBC();
        if (methodBC.equals(Constants.GET)) {
            methodBC.setString(Constants.GET);
        } else if (methodBC.equals(Constants.POST)) {
            methodBC.setString(Constants.POST);
        }
//
//        MimeHeaders headers = request.getMimeHeaders();
//
//        // Check connection header
//        MessageBytes connectionValueMB = headers.getValue("connection");
//        if (connectionValueMB != null) {
//            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
//            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
//                keepAlive = false;
//                connectionHeaderValueSet = false;
//            } else if (findBytes(connectionValueBC,
//                                 Constants.KEEPALIVE_BYTES) != -1) {
//                keepAlive = true;
//                connectionHeaderValueSet = true;
//            }
//        }
//
//        // Check user-agent header
//        if ((restrictedUserAgents != null) && ((http11) || (keepAlive))) {
//            MessageBytes userAgentValueMB =
//                request.getMimeHeaders().getValue("user-agent");
//            if (userAgentValueMB != null) {
//                // Check in the restricted list, and adjust the http11
//                // and keepAlive flags accordingly
//                String userAgentValue = userAgentValueMB.toString();
//                for (int i = 0; i < restrictedUserAgents.length; i++) {
//                    if (restrictedUserAgents[i].equals(userAgentValue)) {
//                        http11 = false;
//                        keepAlive = false;
//                    }
//                }
//            }
//        }
//
//        // Check for a full URI (including protocol://host:port/)
//        ByteChunk uriBC = request.requestURI().getByteChunk();
//        if (uriBC.startsWithIgnoreCase("http", 0)) {
//
//            int pos = uriBC.indexOf("://", 0, 3, 4);
//            int uriBCStart = uriBC.getStart();
//            int slashPos = -1;
//            if (pos != -1) {
//                byte[] uriB = uriBC.getBytes();
//                slashPos = uriBC.indexOf('/', pos + 3);
//                if (slashPos == -1) {
//                    slashPos = uriBC.getLength();
//                    // Set URI as "/"
//                    request.requestURI().setBytes
//                        (uriB, uriBCStart + pos + 1, 1);
//                } else {
//                    request.requestURI().setBytes
//                        (uriB, uriBCStart + slashPos,
//                         uriBC.getLength() - slashPos);
//                }
//                MessageBytes hostMB = headers.setValue("host");
//                hostMB.setBytes(uriB, uriBCStart + pos + 3,
//                                slashPos - pos - 3);
//            }
//
//        }
//
//        // Input filter setup
//        InputFilter[] inputFilters = inputBuffer.getFilters();
//
//        // Parse content-length header
//        long contentLength = request.getContentLengthLong();
//        if (contentLength >= 0) {
//
//            inputBuffer.addActiveFilter
//                (inputFilters[Constants.IDENTITY_FILTER]);
//            contentDelimitation = true;
//        }
//
//        // Parse transfer-encoding header
//        MessageBytes transferEncodingValueMB = null;
//        if (http11)
//            transferEncodingValueMB = headers.getValue("transfer-encoding");
//        if (transferEncodingValueMB != null) {
//            String transferEncodingValue = transferEncodingValueMB.toString();
//            // Parse the comma separated list. "identity" codings are ignored
//            int startPos = 0;
//            int commaPos = transferEncodingValue.indexOf(',');
//            String encodingName = null;
//            while (commaPos != -1) {
//                encodingName = transferEncodingValue.substring
//                    (startPos, commaPos).toLowerCase().trim();
//                if (!addInputFilter(inputFilters, encodingName)) {
//                    // Unsupported transfer encoding
//                    error = true;
//                    // 501 - Unimplemented
//                    response.setStatus(501);
//                }
//                startPos = commaPos + 1;
//                commaPos = transferEncodingValue.indexOf(',', startPos);
//            }
//            encodingName = transferEncodingValue.substring(startPos)
//                .toLowerCase().trim();
//            if (!addInputFilter(inputFilters, encodingName)) {
//                // Unsupported transfer encoding
//                error = true;
//                // 501 - Unimplemented
//                response.setStatus(501);
//            }
//        }
//
        MimeHeaders headers = request.getHeaders();
        BufferChunk valueBC = headers.getValue("host");

        // Check host header
        if (http11 && (valueBC == null)) {
            //error = true;
            // 400 - Bad request
            response.setStatus(400);
            response.setReasonPhrase("Bad Request");
            return false;
        }

        //parseHost(valueBC, request, response);
//
//        if (!contentDelimitation) {
//            // If there's no content length
//            // (broken HTTP/1.0 or HTTP/1.1), assume
//            // the client is not broken and didn't send a body
//            inputBuffer.addActiveFilter
//                (inputFilters[Constants.VOID_FILTER]);
//            contentDelimitation = true;
//        }

        return true;

    }


    public boolean parseHost(BufferChunk valueBC,
                             HttpRequestPacket request,
                             HttpResponsePacket response) {

        if (valueBC == null || valueBC.isNull()) {
            // HTTP/1.0
            // Default is what the socket tells us. Overridden if a host is
            // found/parsed
            Connection connection = request.getConnection();
            request.setServerPort(((InetSocketAddress) connection.getLocalAddress()).getPort());
            InetAddress localAddress = ((InetSocketAddress) connection.getLocalAddress()).getAddress();
            // Setting the socket-related fields. The adapter doesn't know
            // about socket.
            request.setLocalHost(localAddress.getHostName());
            request.serverName().setString(localAddress.getHostName());
            return true;
        }


        int valueS = valueBC.getStart();
        int valueL = valueBC.getEnd() - valueS;
        byte[] valueB = new byte[valueBC.getEnd() + valueBC.getStart()];
        valueBC.getBuffer().get(valueB);
        int colonPos = -1;
        byte[] hostNameC = new byte[valueL];  // extra allocation


        boolean ipv6 = (valueB[valueS] == '[');
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            char b = (char) valueB[i + valueS];
            hostNameC[i] = (byte) b;
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }

        if (colonPos < 0) {
            if (!request.isSecure()) {
                // 80 - Default HTTTP port
                request.setServerPort(80);
            } else {
                // 443 - Default HTTPS port
                request.setServerPort(443);
            }
            MemoryManager mm = request.getConnection().getTransport().getMemoryManager();
            Buffer b = mm.allocate(valueL);
            b.put(hostNameC);
            request.serverName().setBuffer(b);
            //request.serverName().setChars(hostNameC, 0, valueL);
        } else {
            MemoryManager mm = request.getConnection().getTransport().getMemoryManager();
            Buffer b = mm.allocate(colonPos);
            request.serverName().setBuffer(b);
            //request.serverName().setChars(hostNameC, 0, colonPos);

            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                int charValue = HexUtils.DEC[(int) valueB[i + valueS]];
                if (charValue == -1) {
                    // Invalid character
                    //error = true;
                    // 400 - Bad request
                    response.setStatus(400);
                    response.setReasonPhrase("Bad Request");
                    return false;
                }
                port = port + (charValue * mult);
                mult = 10 * mult;
            }
            request.setServerPort(port);

        }

        return true;

    }

}
