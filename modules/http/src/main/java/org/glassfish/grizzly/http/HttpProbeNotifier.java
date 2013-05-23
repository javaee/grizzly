/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
 * Utility class, which has notification methods for different
 * {@link HttpProbe} events.
 *
 * @author Alexey Stashok
 */
final class HttpProbeNotifier {

    /**
     * Notify registered {@link HttpProbe}s about the "data received" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param buffer {@link Buffer}.
     */
    static void notifyDataReceived(final HttpCodecFilter httpFilter,
            final Connection connection,
            final Buffer buffer) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onDataReceivedEvent(connection, buffer);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "data sent" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param buffer {@link Buffer}.
     */
    static void notifyDataSent(final HttpCodecFilter httpFilter,
            final Connection connection,
            final Buffer buffer) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onDataSentEvent(connection, buffer);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "header parsed" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}s been parsed.
     * @param size the size of the parsed header buffer.
     */
    static void notifyHeaderParse(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpHeader header, final int size) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onHeaderParseEvent(connection, header, size);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "header serialized" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}s been serialized.
     * @param buffer the serialized header {@link Buffer}.
     */
    static void notifyHeaderSerialize(final HttpCodecFilter httpFilter,
            final Connection connection, final HttpHeader header,
            final Buffer buffer) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onHeaderSerializeEvent(connection, header, buffer);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "content chunk parsed" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param content HTTP {@link HttpContent}s been parsed.
     */
    static void notifyContentChunkParse(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpContent content) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onContentChunkParseEvent(connection, content);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "content chunk serialize" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param content HTTP {@link HttpContent}s to be serialized.
     */
    static void notifyContentChunkSerialize(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpContent content) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onContentChunkSerializeEvent(connection, content);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "content encoding parse" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be parsed/decoded.
     * @param contentEncoding {@link ContentEncoding} to be applied for parsing.
     */
    static void notifyContentEncodingParse(final HttpCodecFilter httpFilter,
            final Connection connection, final HttpHeader header,
            final Buffer buffer, final ContentEncoding contentEncoding) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onContentEncodingParseEvent(connection, header, buffer,
                        contentEncoding);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the result of the "content encoding decode" event.
     *
     * @param httpFilter      the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection      the <tt>Connection</tt> event occurred on.
     * @param header          HTTP {@link HttpHeader}, the event belongs to.
     * @param result          the result of the decoding process.
     * @param contentEncoding the {@link ContentEncoding} which was applied.
     * @since 2.3.3
     */
    static void notifyContentEncodingParseResult(final HttpCodecFilter httpFilter,
                                                 final Connection connection,
                                                 final HttpHeader header,
                                                 final Buffer result,
                                                 final ContentEncoding contentEncoding) {
        final HttpProbe[] probes =
                httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onContentEncodingSerializeResultEvent(connection,
                                                            header,
                                                            result,
                                                            contentEncoding);
            }
        }
    }
    
    /**
     * Notify registered {@link HttpProbe}s about the "content encoding serialize" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be serialized/encoded.
     * @param contentEncoding {@link ContentEncoding} to be applied for serializing.
     */
    static void notifyContentEncodingSerialize(final HttpCodecFilter httpFilter,
            final Connection connection, final HttpHeader header,
            final Buffer buffer, final ContentEncoding contentEncoding) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onContentEncodingSerializeEvent(connection, header, buffer,
                        contentEncoding);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the result of the "content encoding serialize" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param result the result of the encoding process.
     * @param contentEncoding the {@link ContentEncoding} which was applied.
     *
     * @since 2.3.3
     */
    static void notifyContentEncodingSerializeResult(final HttpCodecFilter httpFilter,
                                                     final Connection connection,
                                                     final HttpHeader header,
                                                     final Buffer result,
                                                     final ContentEncoding contentEncoding) {
        final HttpProbe[] probes =
                httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onContentEncodingSerializeResultEvent(connection,
                                                            header,
                                                            result,
                                                            contentEncoding);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "transfer encoding parse" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be parsed/decoded.
     * @param transferEncoding {@link TransferEncoding} to be applied for parsing.
     */
    static void notifyTransferEncodingParse(final HttpCodecFilter httpFilter,
            final Connection connection, final HttpHeader header,
            final Buffer buffer, final TransferEncoding transferEncoding) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onTransferEncodingParseEvent(connection, header, buffer,
                        transferEncoding);
            }
        }
    }

    /**
     * Notify registered {@link HttpProbe}s about the "transfer encoding serialize" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}, the event belongs to.
     * @param buffer {@link Buffer} to be serialized/encoded.
     * @param transferEncoding {@link TransferEncoding} to be applied for serializing.
     */
    static void notifyTransferEncodingSerialize(final HttpCodecFilter httpFilter,
            final Connection connection, final HttpHeader header,
            final Buffer buffer, final TransferEncoding transferEncoding) {

        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (HttpProbe probe : probes) {
                probe.onTransferEncodingSerializeEvent(connection, header, buffer,
                        transferEncoding);
            }
        }
    }
    
    /**
     * Notify registered {@link HttpProbe}s about the error.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param httpPacket the <tt>HttpPacket</tt> event occurred on.
     * @param error {@link Throwable}.
     */
    static void notifyProbesError(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpPacket httpPacket,
            Throwable error) {
        final HttpProbe[] probes = httpFilter.monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            if (error == null) {
                error = new IllegalStateException("Error in HTTP semantics");
            }
            
            for (HttpProbe probe : probes) {
                probe.onErrorEvent(connection, httpPacket, error);
            }
        }
    }
}
