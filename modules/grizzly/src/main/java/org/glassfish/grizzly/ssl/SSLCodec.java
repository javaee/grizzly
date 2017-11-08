/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.ssl;

import java.io.IOException;
import java.util.concurrent.Future;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Codec;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Transformer;

/**
 * SSL Codec, which contains SSL encoder and decoder {@link Transformer}s.
 * 
 * @author Alexey Stashok
 */
public class SSLCodec implements Codec<Buffer, Buffer> {

    private final SSLEngineConfigurator serverSSLEngineConfig;
    private final SSLEngineConfigurator clientSSLEngineConfig;
    
    private final Transformer<Buffer, Buffer> decoder;
    private final Transformer<Buffer, Buffer> encoder;

    public SSLCodec(SSLContextConfigurator config) {
        this(config.createSSLContext(true));
    }

    public SSLCodec(SSLContext sslContext) {

        decoder = new SSLDecoderTransformer();
        encoder = new SSLEncoderTransformer();

        serverSSLEngineConfig = new SSLEngineConfigurator(sslContext, false,
                false, false);
        clientSSLEngineConfig = new SSLEngineConfigurator(sslContext, true,
                false, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transformer<Buffer, Buffer> getDecoder() {
        return decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Transformer<Buffer, Buffer> getEncoder() {
        return encoder;
    }

    public SSLEngineConfigurator getClientSSLEngineConfig() {
        return clientSSLEngineConfig;
    }

    public SSLEngineConfigurator getServerSSLEngineConfig() {
        return serverSSLEngineConfig;
    }

    public Future<SSLEngine> handshake(Connection connection)
            throws IOException {
        return handshake(connection, clientSSLEngineConfig);
    }

    public Future<SSLEngine> handshake(Connection connection,
            SSLEngineConfigurator configurator) throws IOException {
        return null;
//        return sslHandshaker.handshake(connection, configurator);
    }
}
