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
import com.sun.grizzly.http.HttpCodecFilter.ContentParsingState;

/**
 * Fixed length transfer encoding implementation.
 *
 * @see TransferEncoding
 *
 * @author Alexey Stashok
 */
public final class FixedLengthTransferEncoding implements TransferEncoding {
    public FixedLengthTransferEncoding() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEncodeRequest(HttpRequestPacket requestPacket) {
        final long contentLength = requestPacket.getContentLength();

        return (contentLength != -1);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEncodeResponse(HttpResponsePacket responsePacket) {
        final long contentLength = responsePacket.getContentLength();

        return (contentLength != -1);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParsingResult parseRequest(Connection connection,
            HttpRequestPacket requestPacket, Buffer input) {
        return parsePacket(connection, requestPacket, input);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ParsingResult parseResponse(Connection connection,
            HttpResponsePacket responsePacket, Buffer buffer) {
        return parsePacket(connection, responsePacket, buffer);
    }

    private ParsingResult parsePacket(Connection connection, HttpHeader packet, Buffer input) {
        final HttpPacketParsing httpPacketParsing = (HttpPacketParsing) packet;
        // Get HTTP content parsing state
        final ContentParsingState contentParsingState =
                httpPacketParsing.getContentParsingState();


        if (contentParsingState.chunkRemainder == -1) {
            // if we have just parsed a HTTP message header
            // assign chunkRemainder to the HTTP message content length
            contentParsingState.chunkRemainder = packet.getContentLength();
        }

        Buffer remainder = null;

        final long thisPacketRemaining = contentParsingState.chunkRemainder;
        final int available = input.remaining();

        if (available > thisPacketRemaining) {
            // if input Buffer has part of the next HTTP message - slice it
            remainder = input.slice(
                    (int) (input.position() + thisPacketRemaining), input.limit());
            input.limit((int) (input.position() + thisPacketRemaining));
        }

        // recalc. the HTTP message remaining bytes
        contentParsingState.chunkRemainder -= input.remaining();

        final boolean isLast = (contentParsingState.chunkRemainder == 0);

        return ParsingResult.create(packet.httpContentBuilder().content(input)
                .last(isLast).build(), remainder);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer serializeRequest(Connection connection, HttpContent httpContent) {
        return httpContent.getContent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer serializeResponse(Connection connection, HttpContent httpContent) {
        return httpContent.getContent();
    }
}
