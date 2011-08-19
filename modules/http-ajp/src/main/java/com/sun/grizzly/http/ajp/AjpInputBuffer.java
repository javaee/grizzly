/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;

public class AjpInputBuffer extends InternalInputBuffer {
    private String secret;
    private boolean isTomcatAuthentication = true;
    private ByteBuffer buffer;

    public AjpInputBuffer(Request request, int requestBufferSize) {
        super(request, requestBufferSize);
    }

    @Override
    public void parseRequestLine() throws IOException {
        if (pos >= lastValid) {
            if (!fill())
                throw new EOFException(sm.getString("iib.eof.error"));
        }

        final int type = extractType();

        buffer = ByteBuffer.wrap(buf, 5, lastValid);
        switch (type) {
            case AjpConstants.JK_AJP13_FORWARD_REQUEST:
                processForwardRequest();
                break;
            case AjpConstants.JK_AJP13_DATA:
//                return processData(chunk);
                break;
            case AjpConstants.JK_AJP13_SHUTDOWN:
//                return processShutdown(chunk);
                break;
            case AjpConstants.JK_AJP13_CPING_REQUEST:
//                return processCPing();
                break;
            default:
                throw new IllegalStateException("Unknown message " + type);
        }
    }

    @Override
    public void parseHeaders() throws IOException {
    }

    /**
     * Configure Ajp Filter using properties.
     * We support following properties: request.useSecret, request.secret, tomcatAuthentication.
     *
     * @param properties
     */
    public void configure(final Properties properties) {
        if (Boolean.parseBoolean(properties.getProperty("request.useSecret"))) {
            secret = Double.toString(Math.random());
        }

        secret = properties.getProperty("request.secret", secret);
        isTomcatAuthentication = Boolean.parseBoolean(properties.getProperty("tomcatAuthentication", "true"));
    }

    private int extractType() throws IOException {
        final int type;
//        if (!httpRequestInProcessAttr.isSet(connection)) {
        // if request is no in process - it should be a new Ajp message
        type = buf[4] & 0xFF;
//        } else {
//            Ajp Data Packet
//            type = AjpConstants.JK_AJP13_DATA;
//        }

        return type;
    }

    private void processForwardRequest() throws IOException {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        AjpMessageUtils.decodeRequest(buffer, isTomcatAuthentication, ajpRequest);
        pos = buffer.position();

        if (secret != null) {
            final String epSecret = ajpRequest.getSecret();
            if (epSecret == null || !secret.equals(epSecret)) {
                throw new IllegalStateException("Secret doesn't match");
            }
        }

        final long contentLength = request.getContentLength();
        if (contentLength > 0) {
            // if content-length > 0 - the first data chunk will come immediately,
            // so let's wait for it
            ajpRequest.setContentBytesRemaining((int) contentLength);
            ajpRequest.setExpectContent(true);
        } else {
            // content-length == 0 - no content is expected
            ajpRequest.setExpectContent(false);
        }
    }

}
