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

package com.sun.grizzly.http.ajp;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.net.SSLSupport;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Request} implementation, which also contains AJP
 * related meta data.
 *
 * @author Alexey Stashok
 */
public final class AjpHttpRequest extends Request {
    private static final Logger LOGGER = Logger.getLogger(AjpHttpRequest.class.getName());
    private boolean secure;
    private boolean expectContent;

    public static AjpHttpRequest create() {
        return new AjpHttpRequest();
    }

    private final MessageBytes instanceId = MessageBytes.newInstance();
    private final MessageBytes sslCert = MessageBytes.newInstance();

    private String secret;

    private final AjpHttpResponse response = new AjpHttpResponse();

//    final ProcessingState processingState = new ProcessingState();

    private int contentBytesRemaining = -1;

    public AjpHttpRequest() {
        response.setRequest(this);
        setResponse(response);
    }

    @Override
    public Object getAttribute(final String name) {
        Object result = super.getAttribute(name);

        // If it's CERTIFICATE_KEY request - lazy initialize it, if required
        if (result == null && SSLSupport.CERTIFICATE_KEY.equals(name)) {
            // Extract SSL certificate information (if requested)
            if (!sslCert.isNull()) {
                final ByteChunk bc = sslCert.getByteChunk();
                InputStream stream = new ByteArrayInputStream(bc.getBytes(), bc.getStart(), bc.getEnd());

                // Fill the first element.
                X509Certificate jsseCerts[];
                try {
                    CertificateFactory cf = CertificateFactory.getInstance("X.509");
                    jsseCerts = new X509Certificate[]{(X509Certificate) cf.generateCertificate(stream)};
                } catch (CertificateException e) {
                    LOGGER.log(Level.SEVERE, "Certificate conversion failed", e);
                    return null;
                }

                setAttribute(SSLSupport.CERTIFICATE_KEY, jsseCerts);
                result = jsseCerts;
            }
        }

        return result;
    }

    /**
     * Get the instance id (or JVM route). Curently Ajp is sending it with each
     * request. In future this should be fixed, and sent only once ( or
     * 'negotiated' at config time so both tomcat and apache share the same name.
     *
     * @return the instance id
     */
    public MessageBytes instanceId() {
        return instanceId;
    }

    public MessageBytes sslCert() {
        return sslCert;
    }

    public String getSecret() {
        return secret;
    }

    void setSecret(final String secret) {
        this.secret = secret;
    }

//    @Override
//    public ProcessingState getProcessingState() {
//        return processingState;
//    }

    public int getContentBytesRemaining() {
        return contentBytesRemaining;
    }

    public void setContentBytesRemaining(final int contentBytesRemaining) {
        this.contentBytesRemaining = contentBytesRemaining;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(final boolean secure) {
        this.secure = secure;
    }

    @Override
    public void recycle() {
        //        processingState.recycle();
        contentBytesRemaining = -1;
        response.recycle();
        instanceId.recycle();
        sslCert.recycle();
        secret = null;
        super.recycle();
    }

    public boolean isExpectContent() {
        return expectContent;
    }

    public void setExpectContent(boolean expectContent) {
        this.expectContent = expectContent;
    }
}