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

package org.glassfish.grizzly.http.multipart;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.io.NIOInputStream;

/**
 * Entry point for the multipart message processing.
 * Initiates non-blocking, asynchronous multipart message parsing and processing.
 * 
 * @since 2.0.1
 * 
 * @author Alexey Stashok
 * @author Heinrich Schuchardt
 */
public class MultipartScanner {
    public static final String BOUNDARY_ATTR = "boundary";
    
    private static final Logger LOGGER = Grizzly.logger(MultipartScanner.class);

    static final String MULTIPART_CONTENT_TYPE = "multipart";
    
    private MultipartScanner() {
    }
    
    /**
     * Initialize the multipart HTTP request processing.
     * 
     * @param request the multipart HTTP request.
     * @param partHandler the {@link MultipartEntryHandler}, which is responsible
     * for processing multipart entries.
     * @param completionHandler {@link CompletionHandler}, which is invoked after
     * multipart Request has been processed, or error occurred.
     */
    public static void scan(final Request request,
            final MultipartEntryHandler partHandler,
            final CompletionHandler<Request> completionHandler) {
        try {
            final String contentType = request.getContentType();
            if (contentType == null) {
                throw new IllegalStateException("ContentType not found");
            }
            
            final String[] contentTypeParams = contentType.split(";");
            final String[] contentSubType = contentTypeParams[0].split("/");

            if (contentSubType.length != 2
                    || !MULTIPART_CONTENT_TYPE.equalsIgnoreCase(contentSubType[0])) {
                throw new IllegalStateException("Not multipart request");
            }

            String boundary = null;
            final Map<String, String> contentTypeProperties =
                    new HashMap<String, String>();
            
            for (int i = 1; i < contentTypeParams.length; i++) {
                final String param = contentTypeParams[i].trim();
                final String[] paramValue = param.split("=", 2);
                if (paramValue.length == 2) {
                    String key = paramValue[0].trim();
                    String value = paramValue[1].trim();
                    if (value.charAt(0) == '"') {
                        value = value.substring(1,
                                value.length()
                                - 1);
                    }
                    contentTypeProperties.put(key, value);
                    if (BOUNDARY_ATTR.equals(key)) {
                        boundary = value;
                    }
                }
            }

            if (boundary == null) {
                throw new IllegalStateException("Boundary not found");
            }

            final NIOInputStream nioInputStream = request.getNIOInputStream();

            nioInputStream.notifyAvailable(new MultipartReadHandler(request,
                    partHandler, completionHandler,
                    new MultipartContext(boundary, contentType,
                    contentTypeProperties)));
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            } else {
                LOGGER.log(Level.WARNING, "Error occurred, but no CompletionHandler installed to handle it", e);
            }
        }
    }

    /**
     * Initialize the multipart/mixed {@link MultipartEntry} processing.
     * 
     * @param multipartMixedEntry the multipart/mixed {@link MultipartEntry}.
     * @param partHandler the {@link MultipartEntryHandler}, which is responsible
     * for processing multipart sub-entries.
     * @param completionHandler {@link CompletionHandler}, which is invoked after
     * multipart/mixed {@link MultipartEntry} has been processed, or error occurred.
     */
    public static void scan(final MultipartEntry multipartMixedEntry,
            final MultipartEntryHandler partHandler,
            final CompletionHandler<MultipartEntry> completionHandler) {
        try {
            final String contentType = multipartMixedEntry.getContentType();
            final String[] contentTypeParams = contentType.split(";");
            final String[] contentSubType = contentTypeParams[0].split("/");

            if (contentSubType.length != 2
                    || !MULTIPART_CONTENT_TYPE.equalsIgnoreCase(contentSubType[0])) {
                throw new IllegalStateException("Not multipart request");
            }

            String boundary = null;
            final Map<String, String> contentTypeProperties =
                    new HashMap<String, String>();
            
            for (int i = 1; i < contentTypeParams.length; i++) {
                final String param = contentTypeParams[i].trim();
                final String[] paramValue = param.split("=", 2);
                if (paramValue.length == 2) {
                    String key = paramValue[0].trim();
                    String value = paramValue[1].trim();
                    if (value.charAt(0) == '"') {
                        value = value.substring(1,
                                value.length()
                                - 1);
                    }
                    contentTypeProperties.put(key, value);
                    if (BOUNDARY_ATTR.equals(key)) {
                        boundary = value;
                    }
                }
            }

            if (boundary == null) {
                throw new IllegalStateException("Boundary not found");
            }

            final NIOInputStream nioInputStream = multipartMixedEntry.getNIOInputStream();

            nioInputStream.notifyAvailable(new MultipartReadHandler(multipartMixedEntry,
                    partHandler, completionHandler,
                    new MultipartContext(boundary, contentType,
                    contentTypeProperties)));
        } catch (Exception e) {
            if (completionHandler != null) {
                completionHandler.failed(e);
            } else {
                LOGGER.log(Level.WARNING, "Error occurred, but no CompletionHandler installed to handle it", e);
            }
        }
    }
}
