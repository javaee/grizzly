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
package com.sun.grizzly.websockets;

import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;

import java.util.logging.Logger;

/**
 * @author Justin Lee
 */
public abstract class HandShake {
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);

    private boolean secure;
    private String origin;
    private String serverHostName;
    private String port = "80";
    private String resourcePath;
    private String location;
    private String subProtocol;

    public HandShake(boolean isSecure, String path) {
        secure = isSecure;
        resourcePath = path;
    }

    public HandShake(boolean isSecure, String origin, String serverHostName, String portNumber, String resourcePath) {
        this.origin = origin;
        this.serverHostName = serverHostName;
        secure = isSecure;
        port = portNumber;
        this.resourcePath = resourcePath;
        subProtocol = null;
        location = buildLocation(isSecure);
    }

    String buildLocation(boolean isSecure) {
        StringBuilder builder = new StringBuilder((isSecure ? "wss" : "ws") + "://" + serverHostName);
        if (!"80".equals(port)) {
            builder.append(":" + port);
        }
        if (resourcePath == null || !resourcePath.startsWith("/") && !"".equals(resourcePath)) {
            builder.append("/");
        }
        builder.append(resourcePath);
        return builder.toString();
    }

    /**
     * Reads the header value using UTF-8 encoding
     *
     * @param headers
     * @param name
     * @return
     */
    final String readHeader(MimeHeaders headers, final String name) {
        final MessageBytes value = headers.getValue(name);
        return value == null ? null : value.toString();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Origin (bytes 4F 72 69 67 69 6E; always the fourth name-value pair)
     * <p/>
     * The value gives the scheme, hostname, and port (if it's not the default
     * port for the given scheme) of the page that asked the client to open the Web Socket.
     * It would be interesting if the server's operator had deals with operators of other sites,
     * since the server could then decide how to respond (or indeed, _whether_ to respond) based
     * on which site was requesting a connection.  The value must be interpreted as UTF-8.
     */
    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
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

    /**
     * WebSocket-Protocol (bytes 57 65 62 53 6F 63 6B 65 74 2D 50 72 6F 74 6F 63 6F 6C; optional,
     * if present, will be the fifth name-value pair)
     * The value gives the name of a subprotocol that the client is intending to select.  It would be interesting if
     * the server supports multiple protocols or subProtocol versions.  The value must be interpreted as UTF-8.
     */
    public String getSubProtocol() {
        return subProtocol;
    }

    public void setSubProtocol(String subProtocol) {
        this.subProtocol = subProtocol;
    }
}