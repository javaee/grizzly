/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.ProcessingState;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;

/**
 *
 * @author oleksiys
 */
class Http2Request extends HttpRequestPacket implements Http2Header {
    private static final Logger LOGGER = Grizzly.logger(Http2Request.class);
    
    private static final ThreadCache.CachedTypeIndex<Http2Request> CACHE_IDX =
            ThreadCache.obtainIndex(Http2Request.class, 2);

    public static Http2Request create() {
        Http2Request http2Request =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (http2Request == null) {
            http2Request = new Http2Request();
        }

        return http2Request.init();
    }
    
    private final ProcessingState processingState = new ProcessingState();
    
    private final Http2Response response;
    
    /**
     * Char encoding parsed flag.
     */
    private boolean contentTypeParsed;

    Http2Request() {
        this.response = new Http2Response();
    }

    Http2Request(Http2Response response) {
        this.response = response;
    }
    
    @Override
    public ProcessingState getProcessingState() {
        return processingState;
    }

    private Http2Request init() {
        setResponse(response);
        response.setRequest(this);
        
        setChunkingAllowed(true);
        response.setChunkingAllowed(true);
        
        return this;
    }

    @Override
    public Http2Stream getHttp2Stream() {
        return Http2Stream.getStreamFor(this);
    }

    @Override
    public String getCharacterEncoding() {
        if (!contentTypeParsed) {
            parseContentTypeHeader();
        }

        return super.getCharacterEncoding();
    }

    @Override
    public void setCharacterEncoding(final String charset) {
        if (!contentTypeParsed) {
            parseContentTypeHeader();
        }

        super.setCharacterEncoding(charset);
    }

    @Override
    public String getContentType() {
        if (!contentTypeParsed) {
            parseContentTypeHeader();
        }

        return super.getContentType();
    }

    private void parseContentTypeHeader() {
        contentTypeParsed = true;

        if (!contentType.isSet()) {
            final DataChunk dc = headers.getValue(Header.ContentType);

            if (dc != null && !dc.isNull()) {
                setContentType(dc.toString());
            }
        }
    }

    @Override
    public Object getAttribute(final String name) {
        if (Http2Stream.HTTP2_STREAM_ATTRIBUTE.equals(name)) {
            return response.getHttp2Stream();
        }
        
        return super.getAttribute(name);
    }
    
    @Override
    protected void reset() {
        contentTypeParsed = false;
        
        processingState.recycle();
        
        super.reset();
    }

    @Override
    public void recycle() {
        reset();

        ThreadCache.putToCache(CACHE_IDX, this);
    }

    @Override
    public void setExpectContent(final boolean isExpectContent) {
        super.setExpectContent(isExpectContent);
    }

    @Override
    protected void requiresAcknowledgement(
            final boolean requiresAcknowledgement) {
        super.requiresAcknowledgement(requiresAcknowledgement);
    }
}
