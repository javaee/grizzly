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
    private String[] subProtocol;
    private String[] extensions;

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

    public String[] getSubProtocol() {
        return subProtocol;
    }

    public void setSubProtocol(String[] subProtocol) {
        sanitize(subProtocol);
        this.subProtocol = subProtocol;
    }

    private void sanitize(String[] strings) {
        if(strings != null) {
            for (int i = 0; i < strings.length; i++) {
                strings[i] = strings[i] == null ? null : strings[i].trim();

            }
        }
    }

    public String[] getExtensions() {
        return extensions;
    }

    public void setExtensions(String[] extensions) {
        sanitize(extensions);
        this.extensions = extensions;
    }

    protected String join(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (String s : values) {
            if(builder.length() != 0) {
                builder.append("; ");
            }
            builder.append(s);
        }
        return null;
    }
}
