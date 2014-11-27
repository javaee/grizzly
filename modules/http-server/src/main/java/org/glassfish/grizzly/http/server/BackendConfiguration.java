/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server;

/**
 * This configuration might be useful, when Grizzly HttpServer is running
 * behind an HTTP gateway like reverse proxy, load balancer etc...
 * 
 * In this situation the HTTP gateway may preprocess initial HTTP request
 * headers, examine them and forward modified HTTP request to a Grizzly HttpServer.
 * For example HTTP request received via HTTPS might be forwarded using plain HTTP
 * (for performance reason), so the protocol and client authentication might
 * be lost when the HTTP request reaches Grizzly HttpServer.
 * Using this configuration object, it's possible to instruct Grizzly HttpServer
 * to use custom HTTP request headers to get information about original protocol
 * used by client, user authentication information etc...
 * 
 * @since 2.3.18
 * 
 * @author Alexey Stashok
 */
public class BackendConfiguration {
    /**
     * The HTTP request scheme, which if non-null overrides default one picked
     * up by framework during runtime.
     */
    private String scheme;

    private String schemeMapping;
    
    private String remoteUserMapping;

    /**
     * Returns the HTTP request scheme, which if non-null overrides default one
     * picked up by framework during request processing.
     */
    public String getScheme() {
        return scheme;
    }

    /**
     * Sets the HTTP request scheme, which if non-null overrides default one
     * picked up by framework during request processing.
     * Pls. note this method resets {@link #schemeMapping} property if any
     * was set before.
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
        schemeMapping = null;
    }

    /**
     * Returns the HTTP request header name, whose value (if non-null) would be used
     * to override default protocol scheme picked up by framework during
     * request processing.
     */
    public String getSchemeMapping() {
        return schemeMapping;
    }

    /**
     * Sets the HTTP request header name, whose value (if non-null) would be used
     * to override default protocol scheme picked up by framework during
     * request processing.
     * Pls. note this method resets {@link #scheme} property if any
     * was set before.
     */
    public void setSchemeMapping(String schemeMapping) {
        this.schemeMapping = schemeMapping;
        scheme = null;
    }

    /**
     * Returns the HTTP request header name, whose value (if non-null) would be used
     * to set the name of the remote user that has been authenticated
     * for HTTP Request.
     * 
     * @see Request#getRemoteUser()
     */
    public String getRemoteUserMapping() {
        return remoteUserMapping;
    }

    /**
     * Sets the HTTP request header name, whose value (if non-null) would be used
     * to set the name of the remote user that has been authenticated
     * for HTTP Request.
     * 
     * @see Request#getRemoteUser()
     */
    public void setRemoteUserMapping(String remoteUserMapping) {
        this.remoteUserMapping = remoteUserMapping;
    }
}
