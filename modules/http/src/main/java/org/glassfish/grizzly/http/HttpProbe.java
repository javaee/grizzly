/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.Connection;

/**
 * Monitoring probe providing callbacks that may be invoked by Grizzly Http filters.
 *
 * @author Alexey Stashok
 *
 * @since 2.0
 */
public interface HttpProbe {
    /**
     * Method will be called, when {@link Buffer} will come for processing to
     * the {@link HttpCodecFilter} (either request or response).
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param buffer {@link Buffer} to be parsed.
     */
    void onDataReceivedEvent(Connection connection, Buffer buffer);

    /**
     * Method will be called, when {@link Buffer}, produced by the
     * {@link HttpCodecFilter} will be ready to go to the next
     * {@link org.glassfish.grizzly.filterchain.Filter} in the chain and finally
     * written on wire.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param buffer serialized {@link Buffer}.
     */
    void onDataSentEvent(Connection connection, Buffer buffer);

    /**
     * Method will be called, when HTTP message header gets parsed
     * (either request or response).
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header parsed {@link HttpHeader}.
     * @param size the size of the parsed header buffer.
     */
    void onHeaderParseEvent(Connection connection, HttpHeader header,
                            int size);

    /**
     * Method will be called, when HTTP message header gets serialized
     * (either request or response).
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header serialized {@link HttpHeader}.
     * @param buffer the serialized header {@link Buffer}.
     */
    void onHeaderSerializeEvent(Connection connection, HttpHeader header,
                                Buffer buffer);

    /**
     * Method will be called, when HTTP message content chunk gets parsed
     * (either request or response).
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param content parsed {@link HttpContent}.
     */
    void onContentChunkParseEvent(Connection connection,
                                  HttpContent content);

    /**
     * Method will be called, when HTTP message content chunk is prepared to be
     * serialized (either request or response).
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param content {@link HttpContent} to be serialized.
     */
    void onContentChunkSerializeEvent(Connection connection,
                                      HttpContent content);

    /**
     * Method will be called, when {@link ContentEncoding} will be applied
     * during the parsing/decoding of the certain HTTP message content chunk.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be parsed/decoded.
     * @param contentEncoding {@link ContentEncoding} to be applied.
     */
    void onContentEncodingParseEvent(Connection connection,
                                     HttpHeader header, Buffer buffer, ContentEncoding contentEncoding);

    /**
     * This method will be called after the {@link ContentEncoding} has been
     * applied.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param result the result of the decode operation.
     * @param contentEncoding the {@link ContentEncoding} that was applied.
     *
     * @since 2.3.3
     */
    void onContentEncodingParseResultEvent(Connection connection,
                                           HttpHeader header,
                                           Buffer result,
                                           ContentEncoding contentEncoding);

    /**

    /**
     * Method will be called, when {@link ContentEncoding} will be applied
     * during the serialization/encoding of the certain HTTP message content chunk.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be serialized/encoded.
     * @param contentEncoding {@link ContentEncoding} to be applied.
     */
    void onContentEncodingSerializeEvent(Connection connection,
                                         HttpHeader header, Buffer buffer, ContentEncoding contentEncoding);

    /**
     * Method will be called, when {@link ContentEncoding} will be applied
     * during the serialization/encoding of the certain HTTP message content chunk.
     *
     * @param connection      {@link Connection}, the event belongs to.
     * @param header          HTTP {@link HttpHeader}, the event belongs to.
     * @param result          The result of the encoding processes.
     * @param contentEncoding {@link ContentEncoding} to be applied.
     *
     * @since 2.3.3
     */
    void onContentEncodingSerializeResultEvent(Connection connection,
                                               HttpHeader header,
                                               Buffer result,
                                               ContentEncoding contentEncoding);

    /**
     * Method will be called, when {@link TransferEncoding} will be applied
     * during the parsing/decoding of the certain HTTP message content chunk.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be parsed/decoded.
     * @param transferEncoding {@link TransferEncoding} to be applied.
     */
    void onTransferEncodingParseEvent(Connection connection,
                                      HttpHeader header, Buffer buffer, TransferEncoding transferEncoding);

    /**
     * Method will be called, when {@link TransferEncoding} will be applied
     * during the serialization/encoding of the certain HTTP message content chunk.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be serialized/encoded.
     * @param transferEncoding {@link TransferEncoding} to be applied.
     */
    void onTransferEncodingSerializeEvent(Connection connection,
                                          HttpHeader header, Buffer buffer, TransferEncoding transferEncoding);

    /**
     * Method will be called, when error occurs during the {@link HttpCodecFilter} processing.
     *
     * @param connection {@link Connection}, the event belongs to.
     * @param connection {@link HttpPacket}, the event belongs to.
     * @param error error
     */
    void onErrorEvent(Connection connection, HttpPacket httpPacket, Throwable error);
    
    
    // ---------------------------------------------------------- Nested Classes


    /**
     * {@link HttpProbe} adapter that provides no-op implementations for
     * all interface methods allowing easy extension by the developer.
     *
     * @since 2.1.9
     */
    @SuppressWarnings("UnusedDeclaration")
    class Adapter implements HttpProbe {


        // ---------------------------------------------- Methods from HttpProbe

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDataReceivedEvent(Connection connection, Buffer buffer) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onDataSentEvent(Connection connection, Buffer buffer) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onHeaderParseEvent(Connection connection, HttpHeader header, int size) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onHeaderSerializeEvent(Connection connection, HttpHeader header, Buffer buffer) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContentChunkParseEvent(Connection connection, HttpContent content) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContentChunkSerializeEvent(Connection connection, HttpContent content) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContentEncodingParseEvent(Connection connection, HttpHeader header, Buffer buffer, ContentEncoding contentEncoding) {}

        /**
          * {@inheritDoc}
          */
        @Override
        public void onContentEncodingParseResultEvent(Connection connection, HttpHeader header, Buffer result, ContentEncoding contentEncoding) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContentEncodingSerializeEvent(Connection connection, HttpHeader header, Buffer buffer, ContentEncoding contentEncoding) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onContentEncodingSerializeResultEvent(Connection connection, HttpHeader header, Buffer result, ContentEncoding contentEncoding) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTransferEncodingParseEvent(Connection connection, HttpHeader header, Buffer buffer, TransferEncoding transferEncoding) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTransferEncodingSerializeEvent(Connection connection, HttpHeader header, Buffer buffer, TransferEncoding transferEncoding) {}

        /**
         * {@inheritDoc}
         */
        @Override
        public void onErrorEvent(Connection connection, HttpPacket httpPacket, Throwable error) {}

    } // END Adapter
}
