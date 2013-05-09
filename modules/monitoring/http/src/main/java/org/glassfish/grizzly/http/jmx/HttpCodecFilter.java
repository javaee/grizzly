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

package org.glassfish.grizzly.http.jmx;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.ContentEncoding;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpProbe;
import org.glassfish.grizzly.http.TransferEncoding;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import java.util.concurrent.atomic.AtomicLong;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;

/**
 * JMX management object for the {@link HttpCodecFilter}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("This Filter is responsible for the parsing incoming HTTP packets and serializing high level objects back into the HTTP protocol format.")
public class HttpCodecFilter extends JmxObject {

    private final org.glassfish.grizzly.http.HttpCodecFilter httpCodecFilter;

    private final AtomicLong httpContentReceived = new AtomicLong();
    private final AtomicLong httpContentWritten = new AtomicLong();
    private final AtomicLong httpCodecErrorCount = new AtomicLong();

    private final HttpProbe probe = new JmxHttpProbe();


    // ------------------------------------------------------------ Constructors


    public HttpCodecFilter(org.glassfish.grizzly.http.HttpCodecFilter httpCodecFilter) {
        this.httpCodecFilter = httpCodecFilter;
    }


    // -------------------------------------------------- Methods from JmxObject


    /**
     * {@inheritDoc}
     */
    @Override
    public String getJmxName() {
        return "HttpCodecFilter";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        httpCodecFilter.getMonitoringConfig().addProbes(probe);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        httpCodecFilter.getMonitoringConfig().removeProbes(probe);
    }


    // -------------------------------------------------------------- Attributes


    /**
     * @return total number of bytes received by this
     *  {@link org.glassfish.grizzly.http.HttpCodecFilter}.
     */
    @ManagedAttribute(id="total-bytes-received")
    @Description("The total number of bytes this filter has processed as part of the HTTP protocol parsing process.")
    public long getTotalContentReceived() {
        return httpContentReceived.get();
    }


    /**
     * @return total number of bytes written by this
     *  {@link org.glassfish.grizzly.http.HttpCodecFilter}.
     */
    @ManagedAttribute(id="total-bytes-written")
    @Description("The total number of bytes that have been written as part of the serialization process to the HTTP protocol.")
    public long getTotalContentWritten() {
        return httpContentWritten.get();
    }


    /**
     * @return total number of HTTP codec errors.
     */
    @ManagedAttribute(id="http-codec-error-count")
    @Description("The total number of protocol errors that have occurred during either the parsing or serialization process.")
    public long getHttpCodecErrorCount() {
        return httpCodecErrorCount.get();
    }


    // ---------------------------------------------------------- Nested Classes


    private final class JmxHttpProbe implements HttpProbe {

        @Override
        public void onDataReceivedEvent(Connection connection, Buffer buffer) {
            httpContentReceived.addAndGet(buffer.remaining());
        }

        @Override
        public void onDataSentEvent(Connection connection, Buffer buffer) {
            httpContentWritten.addAndGet(buffer.remaining());
        }

        @Override
        public void onErrorEvent(Connection connection, HttpPacket httpPacket,
                Throwable error) {
            httpCodecErrorCount.incrementAndGet();
        }

        @Override
        public void onHeaderParseEvent(Connection connection, HttpHeader header,
                int size) {
        }

        @Override
        public void onHeaderSerializeEvent(Connection connection, HttpHeader header, Buffer buffer) {
        }

        @Override
        public void onContentChunkParseEvent(Connection connection, HttpContent content) {
        }

        @Override
        public void onContentChunkSerializeEvent(Connection connection, HttpContent content) {
        }

        @Override
        public void onContentEncodingParseEvent(Connection connection, HttpHeader header, Buffer buffer, ContentEncoding contentEncoding) {
        }

        @Override
        public void onContentEncodingSerializeEvent(Connection connection, HttpHeader header, Buffer buffer, ContentEncoding contentEncoding) {
        }

        @Override
        public void onTransferEncodingParseEvent(Connection connection, HttpHeader header, Buffer buffer, TransferEncoding transferEncoding) {
        }

        @Override
        public void onTransferEncodingSerializeEvent(Connection connection, HttpHeader header, Buffer buffer, TransferEncoding transferEncoding) {
        }

    } // End JmxHttpProbe

}
