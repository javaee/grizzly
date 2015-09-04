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
package com.sun.grizzly.http.ajp;

import com.sun.grizzly.http.Constants;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.http.SocketChannelOutputBuffer;
import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.InternalInputBuffer;
import com.sun.grizzly.tcp.http11.OutputFilter;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.net.SSLSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AjpProcessorTask extends ProcessorTask {
    private final static Logger LOGGER = SelectorThread.logger();
    
    private final AjpConfiguration ajpConfiguration;
    
    public AjpProcessorTask(final AjpConfiguration ajpConfiguration,
            final boolean initialize) {
        super(initialize, false);
        this.ajpConfiguration = ajpConfiguration;
    }

    public AjpConfiguration getAjpConfiguration() {
        return ajpConfiguration;
    }
    
    @Override
    protected Request createRequest() {
        return new AjpHttpRequest();
    }

    @Override
    protected Response createResponse() {
        return new AjpHttpResponse();
    }

    @Override
    protected InternalInputBuffer createInputBuffer(Request request, int requestBufferSize) {
        return new AjpInputBuffer(ajpConfiguration, this, request, requestBufferSize);
    }

    @Override
    public void sendHeaders() {
        ((AjpOutputBuffer) outputBuffer).sendHeaders();
    }

    @Override
    protected SocketChannelOutputBuffer createOutputBuffer(Response response, int sendBufferSize,
            boolean bufferResponse) {
        return new AjpOutputBuffer(response, sendBufferSize, bufferResponse);
    }

    @Override
    public boolean parseRequest() throws Exception {
        try {
            ((AjpInputBuffer) inputBuffer).readAjpMessageHeader();
        } catch (Throwable t) {
            error = true;
            response.setStatus(400); // Bad request
            return true;
        }
        return super.parseRequest();
    }

    @Override
    public void invokeAdapter() {
        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;

        switch (ajpRequest.getType()) {
            case AjpConstants.JK_AJP13_FORWARD_REQUEST: {
                try {
                    ajpRequest.setForwardRequestProcessing(true);
                    if (ajpRequest.isExpectContent()) {
                        // if expect content - parse following data chunk
                        ((AjpInputBuffer) request.getInputBuffer()).parseDataChunk();
                    }

                    super.invokeAdapter();
                } catch (Throwable e) {
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO,
                                "Exception during parsing data chunk on connection: "
                                + key.channel(), e);
                    }

                    error = true;
                }
                
                break;
            }
            case AjpConstants.JK_AJP13_SHUTDOWN: {
                try {
                    processShutdown();
                } catch (Throwable e) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE,
                                "Exception during shutdown request processing on connection: "
                                + key.channel(), e);
                    }
                    error = true;
                } finally {
                    // we don't want any response to be sent back
                    response.setCommitted(true);
                    ((AjpOutputBuffer) outputBuffer).setFinished(true);
                }
                break;
            }
            case AjpConstants.JK_AJP13_CPING_REQUEST: {
                try {
                    processCPing();
                } catch (Throwable e) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE,
                                "Exception during sending CPONG reply on connection: "
                                + key.channel(), e);
                    }

                    error = true;
                } finally {
                    // we don't want any response to be sent back
                    response.setCommitted(true);
                    ((AjpOutputBuffer) outputBuffer).setFinished(true);
                }

                break;
            }
            default:
                // we don't want any response to be sent back
                response.setCommitted(true);
                ((AjpOutputBuffer) outputBuffer).setFinished(true);
                error = true;
                LOGGER.log(Level.WARNING, "Invalid packet type: {0}", ajpRequest.getType());
        }
    }

    @Override
    public void action(ActionCode actionCode, Object param) {
        if (actionCode == ActionCode.ACTION_REQ_SSL_ATTRIBUTE) {
            AjpHttpRequest req = (AjpHttpRequest) param;

            // Extract SSL certificate information (if requested)
            MessageBytes certString = req.sslCert();
            if (certString != null && !certString.isNull()) {
                ByteChunk certData = certString.getByteChunk();
                ByteArrayInputStream bais =
                        new ByteArrayInputStream(certData.getBytes(),
                        certData.getStart(),
                        certData.getLength());

                // Fill the first element.
                X509Certificate jsseCerts[] = null;
                try {
                    CertificateFactory cf =
                            CertificateFactory.getInstance("X.509");
                    while (bais.available() > 0) {
                        X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                        if (jsseCerts == null) {
                            jsseCerts = new X509Certificate[1];
                            jsseCerts[0] = cert;
                        } else {
                            X509Certificate[] temp = new X509Certificate[jsseCerts.length + 1];
                            System.arraycopy(jsseCerts, 0, temp, 0, jsseCerts.length);
                            temp[jsseCerts.length] = cert;
                            jsseCerts = temp;
                        }
                    }
                } catch (java.security.cert.CertificateException e) {
                    LOGGER.log(Level.SEVERE, "Certificate convertion failed", e);
                    return;
                }

                req.setAttribute(SSLSupport.CERTIFICATE_KEY,
                        jsseCerts);
            }

        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ATTRIBUTE) {
            Request req = (Request) param;

            // If remoteHost not set by JK, get it's name from it's remoteAddr
            if(req.remoteHost().isNull()) {
                try {
                    req.remoteHost().setString(InetAddress.getByName(
                                               req.remoteAddr().toString()).
                                               getHostName());
                } catch(IOException iex) {
                    if(LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "Unable to resolve {0}", req.remoteAddr());
                    }
                }
            }
        } else if (actionCode == ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE) {
            // Has been set
        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE) {
            // Has been set
        } else if (actionCode == ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE) {
            final AjpHttpRequest req = (AjpHttpRequest) param;
            req.localAddr().setString(req.localName().toString());
        } else if (actionCode == ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE) {
            // Has been set
        } else if (actionCode == ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE) {
            // Has been set
        } else if (actionCode == ActionCode.ACTION_ACK) {
            // 100-Continue had to be processed by httpd
        } else {
            super.action(actionCode, param);
        }
    }

    
    /**
     * When committing the response, we have to validate the set of headers, as
     * well as setup the response filters.
     */
    @Override
    protected void prepareResponse() {

        contentDelimitation = false;
        MimeHeaders headers = response.getMimeHeaders();

        boolean entityBody = true;
        
        OutputFilter[] outputFilters = outputBuffer.getFilters();
        if (http09) {
            // HTTP/0.9
            outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
            return;
        }

        int statusCode = response.getStatus();
        if (statusCode == 204 || statusCode == 205 || statusCode == 304) {
            // No entity body
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            entityBody = false;
            contentDelimitation = true;
        }

        if (request.method().equals("HEAD")) {
            // No entity body
            outputBuffer.addActiveFilter(outputFilters[Constants.VOID_FILTER]);
            contentDelimitation = true;
        }

        if (!entityBody) {
            response.setContentLength(-1);
        } else {
            String contentType = response.getContentType();
            if (contentType != null) {
                headers.setValue("Content-Type").setString(contentType);
            } /* else if (defaultResponseType != null) {
                headers.setValue("Content-Type").setString(defaultResponseType);
            }*/

            String contentLanguage = response.getContentLanguage();
            if (contentLanguage != null && !"".equals(contentLanguage)) {
                headers.setValue("Content-Language").setString(contentLanguage);
            }
        }

        contentDelimitation = true;

        int contentLength = response.getContentLength();
        if (contentLength != -1) {
            headers.setValue("Content-Length").setInt(contentLength);
            outputBuffer.addActiveFilter(outputFilters[Constants.IDENTITY_FILTER]);
        }

        // If we know that the request is bad this early, add the
        // Connection: close header.
        keepAlive = keepAlive && !statusDropsConnection(statusCode)
                && !dropConnection;
        if (!keepAlive) {
            headers.setValue("Connection").setString("close");
            connectionHeaderValueSet = false;
        } else if (!http11 && !error) {
            headers.setValue("Connection").setString("Keep-Alive");
        }
        sendHeaders();
    }
    
    private void processShutdown() {
        if (!ajpConfiguration.isShutdownEnabled()) {
            throw new IllegalStateException("Shutdown is disabled");
        }

        final AjpHttpRequest ajpRequest = (AjpHttpRequest) request;
        String shutdownSecret = null;

        if (ajpRequest.getLength() > 1) { // request contains not just type byte
            // Secret is available
            final MessageBytes tmpMessageBytes = ajpRequest.tmpMessageBytes;
            ((AjpInputBuffer) inputBuffer).getBytesToMB(tmpMessageBytes);
            
            shutdownSecret = tmpMessageBytes.toString();
            tmpMessageBytes.recycle();
        }

        final String secret = ajpConfiguration.getSecret();
        
        if (secret != null &&
                !secret.equals(shutdownSecret)) {
            throw new IllegalStateException("Secret doesn't match, no shutdown");
        }

        final Queue<ShutdownHandler> shutdownHandlers = ajpConfiguration.getShutdownHandlers();
        for (ShutdownHandler handler : shutdownHandlers) {
            try {
                handler.onShutdown(key.channel());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Exception during ShutdownHandler execution", e);
            }
        }
    }
    
    /**
     * Process CPing request message.
     * We send CPong response back as plain Grizzly {@link Buffer}.
     *
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private void processCPing() throws IOException {
        response.setCommitted(true);
        keepAlive = true;
        final AjpOutputBuffer ajpOutputBuffer = (AjpOutputBuffer) outputBuffer;
        AjpHttpResponse.writeCPongReply(ajpOutputBuffer);
        ajpOutputBuffer.flush();
        ajpOutputBuffer.setFinished(true);
    }

    @Override
    public void setBufferSize(int requestBufferSize) {
        if (requestBufferSize < AjpConstants.MAX_PACKET_SIZE * 2) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Buffer size is set to {0} instead of {1} for performance reasons",
                        new Object[]{AjpConstants.MAX_PACKET_SIZE * 2, requestBufferSize});
            }
            
            requestBufferSize = AjpConstants.MAX_PACKET_SIZE * 2;
        }
        
        super.setBufferSize(requestBufferSize);
    }
    
    protected void error() {
        error = true;
        keepAlive = false;
    }
}
