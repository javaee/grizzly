/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import java.io.IOException;
import java.net.InetAddress;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.ProcessingState;
import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.ssl.SSLSupport;
import org.glassfish.grizzly.utils.BufferInputStream;

/**
 * {@link HttpRequestPacket} implementation, which also contains AJP
 * related meta data.
 * 
 * @author Alexey Stashok
 */
public final class AjpHttpRequest extends HttpRequestPacket {
    private static final Logger LOGGER = Grizzly.logger(AjpHttpRequest.class);
    
    private static final ThreadCache.CachedTypeIndex<AjpHttpRequest> CACHE_IDX =
            ThreadCache.obtainIndex(AjpHttpRequest.class, 2);

    public static AjpHttpRequest create() {
        AjpHttpRequest httpRequestImpl =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (httpRequestImpl == null) {
            httpRequestImpl = new AjpHttpRequest();
        }

        return httpRequestImpl.init();
    }

    private final DataChunk instanceId = DataChunk.newInstance();
    private final DataChunk sslCert = DataChunk.newInstance();

    final DataChunk tmpDataChunk = DataChunk.newInstance();
    
    private String secret;
    
    private final AjpHttpResponse cachedResponse = new AjpHttpResponse();
    
    final ProcessingState processingState = new ProcessingState();

    private int contentBytesRemaining = -1;

    private AjpHttpRequest() {
    }

    @Override
    public Object getAttribute(final String name) {
        Object result = super.getAttribute(name);
        
        // If it's CERTIFICATE_KEY request - lazy initialize it, if required
        if (result == null && SSLSupport.CERTIFICATE_KEY.equals(name)) {
            // Extract SSL certificate information (if requested)
            if (!sslCert.isNull()) {
                final BufferChunk bc = sslCert.getBufferChunk();
                BufferInputStream bais = new BufferInputStream(bc.getBuffer(),
                        bc.getStart(), bc.getEnd());

                // Fill the first element.
                X509Certificate jsseCerts[];
                try {
                    CertificateFactory cf =
                            CertificateFactory.getInstance("X.509");
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                    jsseCerts = new X509Certificate[1];
                    jsseCerts[0] = cert;
                } catch (java.security.cert.CertificateException e) {
                    LOGGER.log(Level.SEVERE, "Certificate convertion failed", e);
                    return null;
                }

                setAttribute(SSLSupport.CERTIFICATE_KEY, jsseCerts);
                result = jsseCerts;
            }
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLocalPort() {
        return localPort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRemotePort() {
        return remotePort;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataChunk localAddr() {
        if (localAddressC.isNull()) {
            // Copy the addr from localName
            localAddressC.setString(localNameC.toString());
        }
        
        return localAddressC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataChunk localName() {
        return localNameC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataChunk remoteAddr() {
        return remoteAddressC;
    }

    /**
     * @return the current remote host value. Unlike {@link #remoteHost()}, this
     *         method doesn't try to resolve the host name based on the current
     *         {@link #remoteAddr()} value
     */
    public DataChunk remoteHostRaw() {
        return remoteHostC;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public DataChunk remoteHost() {
        if (remoteHostC.isNull()) {
            try {
                remoteHostC.setString(InetAddress.getByName(
                        remoteAddr().toString()).
                        getHostName());
            } catch (IOException iex) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST, "Unable to resolve {0}", remoteAddr());
                }
            }
        }
        
        return remoteHostC;
    }
    
    /**
     * Get the instance id (or JVM route). Currently Ajp is sending it with each
     * request. In future this should be fixed, and sent only once ( or
     * 'negotiated' at config time so both tomcat and apache share the same name.
     *
     * @return the instance id
     */
    public DataChunk instanceId() {
        return instanceId;
    }
    
    DataChunk sslCert() {
        return sslCert;
    }

    String getSecret() {
        return secret;
    }

    void setSecret(final String secret) {
        this.secret = secret;
    }
    
    private AjpHttpRequest init() {
        cachedResponse.setRequest(this);
        cachedResponse.setChunkingAllowed(true);
        setResponse(cachedResponse);
        return this;
    }

    @Override
    public ProcessingState getProcessingState() {
        return processingState;
    }

    public int getContentBytesRemaining() {
        return contentBytesRemaining;
    }

    public void setContentBytesRemaining(final int contentBytesRemaining) {
        this.contentBytesRemaining = contentBytesRemaining;
    }

    @Override
    public void setExpectContent(boolean isExpectContent) {
        super.setExpectContent(isExpectContent);
    }

    void setUnparsedHostHeader(final DataChunk hostValue) {
        unparsedHostC = hostValue;
    }

    @Override
    protected void doParseHostHeader() {
        AjpMessageUtils.parseHost(unparsedHostC, serverNameRaw(), this);
    }
    
    @Override
    protected void reset() {
        processingState.recycle();
        contentBytesRemaining = -1;
        cachedResponse.recycle();

        instanceId.recycle();
        sslCert.recycle();
        tmpDataChunk.recycle();

        secret = null;
        
        super.reset();
    }

    @Override
    public void recycle() {
        reset();

        ThreadCache.putToCache(CACHE_IDX, this);
    }

    
}
