/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.ssl;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.logging.Filter;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.filterchain.CodecFilter;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.filterchain.StreamTransformerFilter;
import com.sun.grizzly.streams.StreamReader;
import com.sun.grizzly.streams.StreamWriter;

/**
 * SSL {@link Filter} to operate with SSL encrypted data.
 * 
 * @author Alexey Stashok
 */
public class SSLFilter extends FilterAdapter
        implements CodecFilter, StreamTransformerFilter {
    private Logger logger = Grizzly.logger;
    
    private final SSLEngineConfigurator sslEngineConfigurator;
    private final SSLHandshaker sslHandshaker;
    private final SSLDecoderTransformer decoder;
    private final SSLEncoderTransformer encoder;

    public SSLFilter() {
        this(null);
    }
    
    /**
     * Build <tt>SSLFilter</tt> with the given {@link SSLEngineConfigurator}.
     * 
     * @param sslEngineConfigurator SSLEngine configurator
     */
    public SSLFilter(SSLEngineConfigurator sslEngineConfigurator) {
        this(sslEngineConfigurator, null);
    }

    /**
     * Build <tt>SSLFilter</tt> with the given {@link SSLEngineConfigurator} and
     * custom {@link SSLHandshaker}
     *
     * @param sslEngineConfigurator SSLEngine configurator
     */
    public SSLFilter(SSLEngineConfigurator sslEngineConfigurator,
            SSLHandshaker sslHandshaker) {
        if (sslEngineConfigurator == null) {
            sslEngineConfigurator = new SSLEngineConfigurator(
                    SSLContextConfigurator.DEFAULT_CONFIG.createSSLContext(),
                    false, false, false);
        }
        this.sslEngineConfigurator = sslEngineConfigurator;

        if (sslHandshaker == null) {
            sslHandshaker = new BlockingSSLHandshaker();
        }

        this.sslHandshaker = sslHandshaker;
        this.decoder = new SSLDecoderTransformer();
        this.encoder = new SSLEncoderTransformer();
    }

    /**
     * Wraps {@link FilterChainContext} default {@link StreamReader} and
     * {@link StreamWriter} with SSL aware ones.
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        Connection connection = ctx.getConnection();

        SSLResourcesAccessor sslResourceAccessor =
                SSLResourcesAccessor.getInstance();
        SSLEngine sslEngine = sslResourceAccessor.getSSLEngine(connection);

        if (sslEngine == null) {
            // Initialize SSLEngine
            sslEngine = sslEngineConfigurator.createSSLEngine();
            sslResourceAccessor.setSSLEngine(connection, sslEngine);
        }

        StreamReader parentReader = ctx.getStreamReader();
        StreamWriter parentWriter = ctx.getStreamWriter();

        SSLStreamReader sslStreamReader = new SSLStreamReader(parentReader);
        SSLStreamWriter sslStreamWriter = new SSLStreamWriter(parentWriter);

        ctx.setStreamReader(sslStreamReader);
        ctx.setStreamWriter(sslStreamWriter);

        sslStreamReader.pull();

        if (SSLUtils.isHandshaking(sslEngine)) {
            Future future = sslHandshaker.handshake(sslStreamReader,
                    sslStreamWriter, sslEngineConfigurator);
            if (!future.isDone()) {
                return ctx.getStopAction();
            }
        }


        if (!sslStreamReader.hasAvailableData()) {
            nextAction = ctx.getStopAction();
        }
        return nextAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction postRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        SSLStreamReader sslStreamReader = (SSLStreamReader) ctx.getStreamReader();
        SSLStreamWriter sslStreamWriter = (SSLStreamWriter) ctx.getStreamWriter();

        sslStreamReader.detach();

        ctx.setStreamReader(sslStreamReader.getUnderlyingReader());
        ctx.setStreamWriter(sslStreamWriter.getUnderlyingWriter());
        
        return nextAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        StreamWriter writer = ctx.getStreamWriter();

        Object message = ctx.getMessage();

        if (message instanceof Buffer) {
            writer.writeBuffer((Buffer) message);
        }
        writer.flush();

        return nextAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction postWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        return nextAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NextAction postClose(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        SSLResourcesAccessor.getInstance().clear(ctx.getConnection());
        return nextAction;
    }

    @Override
    public StreamReader getStreamReader(StreamReader parentStreamReader) {
        return new SSLStreamReader(parentStreamReader);
    }

    @Override
    public StreamWriter getStreamWriter(StreamWriter parentStreamWriter) {
        return new SSLStreamWriter(parentStreamWriter);
    }

    public SSLSupport createSSLSupport(
            SSLStreamReader reader, SSLStreamWriter writer) {

        return new SSLSupportImpl(
                sslEngineConfigurator, sslHandshaker, reader, writer);
    }

    @Override
    public Transformer getDecoder() {
        return decoder;
    }

    @Override
    public Transformer getEncoder() {
        return encoder;
    }
}
