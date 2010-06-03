/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly.http;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.ThreadCache;

/**
 * Class, responsible for parsing and serializing
 * The implementation should take care about possible HTTP content fragmentation.
 *
 * @author Alexey Stashok
 */
public interface TransferEncoding {
    /**
     * Return <tt>true</tt> if this encoding should be used to parse the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     * 
     * @param httpPacket {@link HttpHeader}.
     * @return <tt>true</tt> if this encoding should be used to parse the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     */
    public boolean wantDecode(HttpHeader httpPacket);

    /**
     * Return <tt>true</tt> if this encoding should be used to serialize the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     *
     * @param httpPacket {@link HttpHeader}.
     * @return <tt>true</tt> if this encoding should be used to serialize the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     */
    public boolean wantEncode(HttpHeader httpPacket);

    /**
     * This method will be called by {@link HttpCodecFilter} to let
     * <tt>TransferEncoding</tt> prepare itself for the content serialization.
     * At this time <tt>TransferEncoding</tt> is able to change, update HTTP
     * packet headers.
     *
     * @param httpHeader HTTP packet headers.
     * @param content ready HTTP content (might be null).
     */
    public void prepareSerialize(HttpHeader httpHeader, HttpContent content);

    /**
     * Parse HTTP packet payload, represented by {@link Buffer} using specific
     * transfer encoding.
     *
     * @param connection {@link Connection}
     * @param httpPacket {@link HttpHeader} with parsed headers.
     * @param buffer {@link Buffer} HTTP message payload.
     * @return {@link ParsingResult}
     */
    public ParsingResult parsePacket(Connection connection,
            HttpHeader httpPacket, Buffer buffer);

    /**
     * Serialize HTTP packet payload, represented by {@link HttpContent}
     * using specific transfer encoding.
     *
     * @param connection {@link Connection}
     * @param httpContent {@link HttpContent} with parsed {@link HttpContent#getHttpHeader()}.
     *
     * @return serialized {@link Buffer}
     */
    public Buffer serializePacket(Connection connection,
            HttpContent httpContent);


    public final class ParsingResult {
        private static final ThreadCache.CachedTypeIndex<ParsingResult> CACHE_IDX =
                ThreadCache.obtainIndex(ParsingResult.class, 1);

        private HttpContent httpContent;
        private Buffer remainderBuffer;

        public static ParsingResult create(HttpContent httpContent, Buffer remainderBuffer) {
            ParsingResult resultObject = ThreadCache.takeFromCache(CACHE_IDX);
            if (resultObject == null) {
                resultObject = new ParsingResult();
            }

            resultObject.httpContent = httpContent;
            resultObject.remainderBuffer = remainderBuffer;

            return resultObject;
        }

        private ParsingResult() {
        }

        public Buffer getRemainderBuffer() {
            return remainderBuffer;
        }

        public HttpContent getHttpContent() {
            return httpContent;
        }

        protected void recycle() {
            remainderBuffer = null;
            httpContent = null;
        }
    }
}
