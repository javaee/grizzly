/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.websockets.rfc6455;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.websockets.Constants;
import org.glassfish.grizzly.websockets.HandShake;
import org.glassfish.grizzly.websockets.HandshakeException;
import org.glassfish.grizzly.websockets.SecKey;

import static org.glassfish.grizzly.websockets.Constants.*;

public class RFC6455HandShake extends HandShake {

    private final SecKey secKey;
    private final List<String> enabledExtensions = Collections.emptyList();
    private final List<String> enabledProtocols = Collections.emptyList();

    // ------------------------------------------------------------ Constructors


    public RFC6455HandShake(URI uri) {
        super(uri);
        secKey = new SecKey();
    }

    public RFC6455HandShake(HttpRequestPacket request) {
        super(request);
        final MimeHeaders mimeHeaders = request.getHeaders();
        String header = mimeHeaders.getHeader(Constants.SEC_WS_EXTENSIONS_HEADER);
        if (header != null) {
            setExtensions(parseExtensionsHeader(header));
        }
        secKey = SecKey.generateServerKey(new SecKey(mimeHeaders.getHeader(Constants.SEC_WS_KEY_HEADER)));
    }


    // -------------------------------------------------- Methods from HandShake

    @Override
    protected int getVersion() {
        return 13;
    }

    @Override
    public void setHeaders(HttpResponsePacket response) {
        response.setReasonPhrase(Constants.RESPONSE_CODE_MESSAGE);
        response.setHeader(Constants.SEC_WS_ACCEPT, secKey.getSecKey());
        if (!getEnabledExtensions().isEmpty()) {
            response.setHeader(Constants.SEC_WS_EXTENSIONS_HEADER,
                               join(getSubProtocol()));
        }
    }

    @Override
    public HttpContent composeHeaders() {
        HttpContent content = super.composeHeaders();
        final HttpHeader header = content.getHttpHeader();
        header.addHeader(Constants.SEC_WS_KEY_HEADER, secKey.toString());
        header.addHeader(Constants.SEC_WS_ORIGIN_HEADER, getOrigin());
        header.addHeader(Constants.SEC_WS_VERSION, getVersion() + "");
        if (!getExtensions().isEmpty()) {
            header.addHeader(Constants.SEC_WS_EXTENSIONS_HEADER,
                             joinExtensions(getExtensions()));
        }

        final String headerValue =
                header.getHeaders().getHeader(SEC_WS_ORIGIN_HEADER);
        header.getHeaders().removeHeader(SEC_WS_ORIGIN_HEADER);
        header.addHeader(ORIGIN_HEADER, headerValue);
        return content;
    }

    @Override
    public void validateServerResponse(final HttpResponsePacket headers)
    throws HandshakeException {
        super.validateServerResponse(headers);
        secKey.validateServerKey(headers.getHeader(Constants.SEC_WS_ACCEPT));
    }

    public List<String> getEnabledExtensions() {
        return enabledExtensions;
    }

    public List<String> getEnabledProtocols() {
        return enabledProtocols;
    }

}
