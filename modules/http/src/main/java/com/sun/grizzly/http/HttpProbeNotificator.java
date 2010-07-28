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

/**
 * Utility class, which has notificator methods for different
 * {@link HttpMonitoringProbe} events.
 *
 * @author Alexey Stashok
 */
final class HttpProbeNotificator {

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "data received" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param buffer {@link Buffer}.
     */
    static void notifyDataReceived(final HttpCodecFilter httpFilter,
            final Connection connection,
            final Buffer buffer) {

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onDataReceivedEvent(connection, buffer);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "data sent" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param buffer {@link Buffer}.
     */
    static void notifyDataSent(final HttpCodecFilter httpFilter,
            final Connection connection,
            final Buffer buffer) {

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onDataSentEvent(connection, buffer);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "header parsed" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}s been parsed.
     */
    static void notifyHeaderParse(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpHeader header) {

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onHeaderParseEvent(connection, header);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "header serialized" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param header HTTP {@link HttpHeader}s been serialized.
     */
    static void notifyHeaderSerialize(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpHeader header) {

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onHeaderSerializeEvent(connection, header);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "content chunk parsed" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param content HTTP {@link HttpContent}s been parsed.
     */
    static void notifyContentChunkParse(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpContent content) {

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onContentChunkParseEvent(connection, content);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "content chunk serialize" event.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param content HTTP {@link HttpContent}s to be serialized.
     */
    static void notifyContentChunkSerialize(final HttpCodecFilter httpFilter,
            final Connection connection,
            final HttpContent content) {

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onContentChunkSerializeEvent(connection, content);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "content encoding parse" event.
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

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onContentEncodingParseEvent(connection, header, buffer,
                        contentEncoding);
            }
        }
    }
    
    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "content encoding serialize" event.
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

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onContentEncodingSerializeEvent(connection, header, buffer,
                        contentEncoding);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "transfer encoding parse" event.
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

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onTransferEncodingParseEvent(connection, header, buffer,
                        transferEncoding);
            }
        }
    }

    /**
     * Notify registered {@link HttpMonitoringProbe}s about the "transfer encoding serialize" event.
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

        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            for (HttpMonitoringProbe probe : probes) {
                probe.onTransferEncodingSerializeEvent(connection, header, buffer,
                        transferEncoding);
            }
        }
    }
    
    /**
     * Notify registered {@link HttpMonitoringProbe}s about the error.
     *
     * @param httpFilter the <tt>HttpCodecFilter</tt> event occurred on.
     * @param connection the <tt>Connection</tt> event occurred on.
     * @param error {@link Throwable}.
     */
    static void notifyProbesError(final HttpCodecFilter httpFilter,
            final Connection connection,
            Throwable error) {
        final HttpMonitoringProbe[] probes = httpFilter.monitoringProbes.getArray();
        if (probes != null) {
            if (error == null) {
                error = new IllegalStateException("Error in HTTP semantics");
            }
            
            for (HttpMonitoringProbe probe : probes) {
                probe.onErrorEvent(connection, error);
            }
        }
    }
}
