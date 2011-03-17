/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.filterchain.FilterChainContext;

/**
 * Abstraction, which represents HTTP transfer-encoding.
 * The implementation should take care about possible HTTP content fragmentation.
 *
 * @see FixedLengthTransferEncoding
 * @see ChunkedTransferEncoding
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
     * @param ctx {@link FilterChainContext}
     * @param httpHeader HTTP packet headers.
     * @param content ready HTTP content (might be null).
     */
    public void prepareSerialize(FilterChainContext ctx,
                                 HttpHeader httpHeader,
                                 HttpContent content);

    /**
     * Parse HTTP packet payload, represented by {@link Buffer} using specific
     * transfer encoding.
     *
     * @param ctx {@link FilterChainContext}
     * @param httpPacket {@link HttpHeader} with parsed headers.
     * @param buffer {@link Buffer} HTTP message payload.
     * @return {@link ParsingResult}
     */
    public ParsingResult parsePacket(FilterChainContext ctx,
            HttpHeader httpPacket, Buffer buffer);

    /**
     * Serialize HTTP packet payload, represented by {@link HttpContent}
     * using specific transfer encoding.
     *
     * @param ctx {@link FilterChainContext}
     * @param httpContent {@link HttpContent} with parsed {@link HttpContent#getHttpHeader()}.
     *
     * @return serialized {@link Buffer}
     */
    public Buffer serializePacket(FilterChainContext ctx,
            HttpContent httpContent);
}
