/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.web.connector.grizzly.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;

import com.sun.enterprise.web.connector.grizzly.Constants;
import com.sun.enterprise.web.connector.grizzly.FileCache;
import com.sun.enterprise.web.connector.grizzly.FileCacheFactory;
import com.sun.enterprise.web.connector.grizzly.Handler;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;

/**
 * This <code>Handler</code> is invoked after the request line has been parsed.
 *
 * @author Jeanfrancois Arcand
 */
public class NoParsingHandler implements Handler<Request> {
    /**
     * The <code>SocketChannel</code> used to send a static resources.
     */
    protected SocketChannel socketChannel;

    /**
     * The FileCache mechanism used to cache static resources.
     */
    protected FileCache fileCache;

    /**
     * Attach a <code>SocketChannel</code> to this object.
     */
    public void attachChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        if (fileCache == null && socketChannel != null) {
            fileCache = FileCacheFactory.getFactory(
                socketChannel.socket().getLocalPort()).getFileCache();
        }
    }

    /**
     * Intercept the request and decide if we cache the static resource. If the static resource is already cached,
     * return it.
     */
    public int handle(Request request, int handlerCode) throws IOException {
        if (fileCache == null) {
            return Handler.CONTINUE;
        }
        if (handlerCode == Handler.RESPONSE_PROCEEDED && canCache(request, handlerCode)) {
            addToCache(request);
        } else if (handlerCode == Handler.HEADERS_PARSED) {
            byte[] bc = getFullResourcePath(request);
            if (fileCache.sendCache(bc, 0, bc.length, socketChannel, keepAlive(request))) {
                return Handler.BREAK;
            }
            return Handler.CONTINUE;
        }
        return Handler.CONTINUE;
    }

    protected byte[] getFullResourcePath(Request request) {
        final String uri = getUri(request);
        File path = new File(getDocRoot(request), uri);
        return path.getAbsolutePath().getBytes();
    }

    protected String getUri(Request request) {
        return request.requestURI().toString();
    }

    protected boolean canCache(Request request, int handlerCode) {
        return fileCache.isEnabled();
    }

    protected void addToCache(Request request) {
        fileCache.add(getServletName(request), getDocRoot(request), getUri(request),
            getHeaders(request), isXpoweredBy(request));
    }

    protected boolean isXpoweredBy(Request request) {
        return false;
    }

    protected MimeHeaders getHeaders(Request request) {
        return request.getResponse().getMimeHeaders();
    }

    protected String getServletName(Request request) {
        return FileCache.DEFAULT_SERVLET_NAME;
    }

    protected String getDocRoot(Request request) {
        return SelectorThread.getWebAppRootPath();
    }

    /**
     * Get the keep-alive header.
     */
    private boolean keepAlive(Request request) {
        MimeHeaders headers = getHeaders(request);
        // Check connection header
        MessageBytes connectionValueMB = headers.getValue("connection");
        if (connectionValueMB != null) {
            ByteChunk connectionValueBC = connectionValueMB.getByteChunk();
            if (findBytes(connectionValueBC, Constants.CLOSE_BYTES) != -1) {
                return false;
            } else if (findBytes(connectionValueBC,
                Constants.KEEPALIVE_BYTES) != -1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Specialized utility method: find a sequence of lower case bytes inside a ByteChunk.
     */
    protected int findBytes(ByteChunk bc, byte[] b) {
        byte first = b[0];
        byte[] buff = bc.getBuffer();
        int start = bc.getStart();
        int end = bc.getEnd();
        // Look for first char
        int srcEnd = b.length;
        for (int i = start; i <= end - srcEnd; i++) {
            if (Ascii.toLower(buff[i]) != first) {
                continue;
            }
            // found first char, now look for a match
            int myPos = i + 1;
            for (int srcPos = 1; srcPos < srcEnd;) {
                if (Ascii.toLower(buff[myPos++]) != b[srcPos++]) {
                    break;
                }
                if (srcPos == srcEnd) {
                    return i - start;
                } // found it
            }
        }
        return -1;
    }
}
