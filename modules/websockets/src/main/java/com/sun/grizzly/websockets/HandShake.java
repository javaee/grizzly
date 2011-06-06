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

import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.net.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private List<String> subProtocol = new ArrayList<String>();
    private List<String> extensions = new ArrayList<String>();

    public HandShake() {
    }

    public HandShake(URL url) {
        resourcePath = url.getPath();
        if ("".equals(resourcePath)) {
            resourcePath = "/";
        }

        origin = url.getHost();
        serverHostName = url.getHost();
        secure = "wss://".equals(url.getProtocol());
        port = url.getPort();
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

    public void setSecure(boolean secure) {
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
        sanitize(subProtocol);
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
                builder.append("; ");
            }
            builder.append(s);
        }
        return null;
    }

    protected void checkForHeader(Map<String, String> headers, final String header, final String validValue) {
        validate(header, validValue, headers.get(header));
    }

    protected void checkForHeader(MimeHeaders headers, final String header, final String validValue) {
        validate(header, validValue, headers.getHeader(header));
    }

    private void validate(String header, String validValue, String value) {
        if(!validValue.equalsIgnoreCase(value)) {
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

    protected void determineHostAndPort(MimeHeaders headers) {
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

    public abstract void initiate(NetworkHandler handler);

    public abstract void validateServerResponse(Map<String, String> map);

    public abstract void respond(Response response);

    protected List<String> split(final String header) {
        return header == null ? Collections.<String>emptyList() : Arrays.asList(header.split(";"));
    }
}
