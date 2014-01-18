/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLSupport;
import org.glassfish.grizzly.ssl.SSLSupportImpl;

public class RequestUtils {

    private static final Logger LOGGER = Grizzly.logger(RequestUtils.class);


    public static Object populateCertificateAttribute(final Request request) {
        Object certificates = null;

        if (request.getRequest().isSecure()) {
            if (request.getRequest().getUpgradeDC().isNull()) {
                // It's normal HTTP request, not upgraded one
                try {
                    request.getInputBuffer().fillFully(
                            request.getHttpFilter().getConfiguration().getMaxBufferedPostSize());
                } catch (IOException e) {
                    throw new IllegalStateException("Can't complete SSL re-negotation", e);
                }
            }
            
            SSLBaseFilter.CertificateEvent event = new SSLBaseFilter.CertificateEvent(true);
            request.getContext().notifyDownstream(event);
            certificates = event.getCertificates();
            request.setAttribute(SSLSupport.CERTIFICATE_KEY, certificates);
        }

        return certificates;
    }

    public static void populateSSLAttributes(final Request request) {
        if (request.isSecure()) {
            try {
                SSLSupport sslSupport = new SSLSupportImpl(request.getContext().getConnection());
                Object sslO = sslSupport.getCipherSuite();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CIPHER_SUITE_KEY, sslO);
                }
                sslO = sslSupport.getPeerCertificateChain(false);
                if (sslO != null) {
                    request.setAttribute(SSLSupport.CERTIFICATE_KEY, sslO);
                }
                sslO = sslSupport.getKeySize();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.KEY_SIZE_KEY, sslO);
                }
                sslO = sslSupport.getSessionId();
                if (sslO != null) {
                    request.setAttribute(SSLSupport.SESSION_ID_KEY, sslO);
                }
            } catch (Exception ioe) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                            "Unable to populate SSL attributes",
                            ioe);
                }
            }
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                        "Unable to populate SSL attributes on plain HTTP request");
            }
        }
    }


    public static void handleSendFile(final Request request) {
        final Object f = request.getAttribute(Request.SEND_FILE_ATTR);
        if (f != null) {
            final Response response = request.getResponse();
            if (response.isCommitted()) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVER_REQUESTUTILS_SENDFILE_FAILED());
                }

                return;
            }

            final File file = (File) f;
            Long offset = (Long) request.getAttribute(Request.SEND_FILE_START_OFFSET_ATTR);
            Long len = (Long) request.getAttribute(Request.SEND_FILE_WRITE_LEN_ATTR);
            if (offset == null) {
                offset = 0L;
            }
            if (len == null) {
                len = file.length();
            }
            // let the sendfile() method suspend/resume the response.
            response.getOutputBuffer().sendfile(file, offset, len, null);
        }
    }
}
