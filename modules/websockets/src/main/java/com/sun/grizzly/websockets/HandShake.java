/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.http.Parameters;
import com.sun.grizzly.util.net.URL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * @author Justin Lee
 */
public abstract class HandShake {
    private static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);

    private boolean secure;
    private String origin;
    private String serverHostName;
    private int port = 80;
    private String resourcePath;
    private String location;
    private final Map<String, String[]> queryParams = new TreeMap<String, String[]>();
    private List<String> subProtocol = new ArrayList<String>();
    private List<String> extensions = new ArrayList<String>();

    public HandShake() {
    }

    public HandShake(URL url) {
        resourcePath = url.getPath();
        if ("".equals(resourcePath)) {
            resourcePath = "/";
        }
        if(url.getQuery() != null) {
            resourcePath += "?" + url.getQuery();
        }

        origin = url.getHost();
        serverHostName = url.getHost();
        secure = "wss".equals(url.getProtocol());
        port = url.getPort();
        buildLocation();
    }

    public HandShake(Request request) {
        setSecure(request.scheme().equals("https"));
        MimeHeaders mimeHeaders = request.getMimeHeaders();
        checkForHeader(mimeHeaders, "Upgrade", "WebSocket");
        checkForHeader(mimeHeaders, "Connection", "Upgrade");
        origin = readHeader(mimeHeaders, WebSocketEngine.SEC_WS_ORIGIN_HEADER);
        if(origin == null) {
            origin = readHeader(mimeHeaders, WebSocketEngine.CLIENT_WS_ORIGIN_HEADER);
        }
        
        determineHostAndPort(mimeHeaders);

        subProtocol = split(mimeHeaders.getHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER));

        if (serverHostName == null) {
            throw new HandshakeException("Missing required headers for WebSocket negotiation");
        }

        resourcePath = request.requestURI().toString();
        final MessageBytes messageBytes = request.queryString();
        String queryString;
        if(messageBytes != null) {
            queryString = messageBytes.toString().trim();
            if(!"".equals(queryString)) {
                resourcePath += "?" + request.queryString();
            }
            Parameters queryParameters = new Parameters();
            queryParameters.processParameters(messageBytes);
            final Enumeration<String> names = queryParameters.getParameterNames();
            while(names.hasMoreElements()) {
                final String name = names.nextElement();
                queryParams.put(name, queryParameters.getParameterValues(name));
            }
        }
        buildLocation();
    }

    protected final void buildLocation() {
        StringBuilder builder = new StringBuilder((isSecure() ? "wss" : "ws") + "://" + serverHostName);
        if (port != 80) {
            builder.append(":" + port);
        }
        if (resourcePath == null || !resourcePath.startsWith("/") && !"".equals(resourcePath)) {
            builder.append("/");
        }
        builder.append(resourcePath);
        location =  builder.toString();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public boolean isSecure() {
        return secure;
    }

    public final void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getServerHostName() {
        return serverHostName;
    }

    public void setServerHostName(String serverHostName) {
        this.serverHostName = serverHostName;
    }

    public List<String> getSubProtocol() {
        return subProtocol;
    }

    public void setSubProtocol(List<String> subProtocol) {
        this.subProtocol = subProtocol;
    }

    private void sanitize(List<String> strings) {
        if(strings != null) {
            for (int i = 0; i < strings.size(); i++) {
                strings.set(i, strings.get(i) == null ? null : strings.get(i).trim());
            }
        }
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        sanitize(extensions);
        this.extensions = extensions;
    }

    protected String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String s : values) {
            if(builder.length() != 0) {
                builder.append(", ");
            }
            builder.append(s);
        }
        return builder.toString();
    }

    protected void checkForHeader(Map<String, String> headers, final String header, final String validValue) {
        validate(header, validValue, headers.get(header));
    }

    protected final void checkForHeader(MimeHeaders headers, final String header, final String validValue) {
        validate(header, validValue, headers.getHeader(header));
    }

    private void validate(String header, String validValue, String value) {
        boolean found = false;
        if(value.contains(",")) {
            for(String part: value.split(",")) {
                found |= part.trim().equalsIgnoreCase(validValue);
            }
        } else {
            found = value.equalsIgnoreCase(validValue);
        }
        if(!found) {
            throw new HandshakeException(String.format("Invalid %s header returned: '%s'", header, value));
        }
    }

    /**
     * Reads the header value using UTF-8 encoding
     *
     * @param headers
     * @param name
     * @return
     */
    public final String readHeader(MimeHeaders headers, final String name) {
        final MessageBytes value = headers.getValue(name);
        return value == null ? null : value.toString();
    }

    private void determineHostAndPort(MimeHeaders headers) {
        String header;
        header = readHeader(headers, "host");
        final int i = header == null ? -1 : header.indexOf(":");
        if (i == -1) {
            setServerHostName(header);
            setPort(80);
        } else {
            setServerHostName(header.substring(0, i));
            setPort(Integer.valueOf(header.substring(i + 1)));
        }
    }

    public void initiate(NetworkHandler handler) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("GET %s HTTP/1.1\r\n", getResourcePath()));
        builder.append(String.format("Host: %s\r\n", getServerHostName()));
        builder.append(String.format("Connection: Upgrade\r\n"));
        builder.append(String.format("Upgrade: WebSocket\r\n"));

        if (!getSubProtocol().isEmpty()) {
            builder.append(String.format("%s: %s\r\n", WebSocketEngine.SEC_WS_PROTOCOL_HEADER,
                    join(getSubProtocol())));
        }
        
        handler.write(builder.toString().getBytes());
    }

    public void validateServerResponse(Map<String, String> map) {
        if (map.isEmpty()) {
            throw new HandshakeException("No response headers received");
        }  // not enough data

        if(!WebSocketEngine.RESPONSE_CODE_VALUE.equals(map.get(WebSocketEngine.RESPONSE_CODE_HEADER))) {
            throw new HandshakeException(String.format("Response code was not %s: %s",
                    WebSocketEngine.RESPONSE_CODE_VALUE,
                    map.get(WebSocketEngine.RESPONSE_CODE_HEADER)));
        }
        checkForHeader(map, WebSocketEngine.UPGRADE, WebSocketEngine.WEBSOCKET);
        checkForHeader(map, WebSocketEngine.CONNECTION, WebSocketEngine.UPGRADE);

        if(!getSubProtocol().isEmpty() && map.get(WebSocketEngine.SEC_WS_PROTOCOL_HEADER) == null) {
            
        }
    }

    public void respond(WebSocketApplication application, Response response) {
        response.setStatus(101);
        response.setMessage("Web Socket Protocol Handshake");
        response.setHeader("Upgrade", "websocket");
        response.setHeader("Connection", "Upgrade");

        setHeaders(response);

        if (!getSubProtocol().isEmpty()) {
            response.setHeader(WebSocketEngine.SEC_WS_PROTOCOL_HEADER,
                    join(application.getSupportedProtocols(getSubProtocol())));
        }
        
        try {
            response.sendHeaders();
            response.flush();
        } catch (IOException e) {
            throw new HandshakeException(e.getMessage(), e);
        }

    }

    protected abstract void setHeaders(Response response);

    protected final List<String> split(final String header) {
        if (header == null) {
            return Collections.<String>emptyList();
        } else {
            final List<String> list = Arrays.asList(header.split(","));
            sanitize(list);
            return list;
        }
    }
}
