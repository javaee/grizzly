/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.utils.Futures;

/**
 * Server {@link org.glassfish.grizzly.Connection} implementation
 * for the {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
public class UDPNIOServerConnection extends UDPNIOConnection {
    private static final Logger LOGGER = Grizzly.logger(UDPNIOServerConnection.class);

    public UDPNIOServerConnection(UDPNIOTransport transport, DatagramChannel channel) {
        super(transport, channel);
    }

    @Override
    public Processor getProcessor() {
        if (processor == null) {
            return transport.getProcessor();
        }

        return processor;
    }

    @Override
    public ProcessorSelector getProcessorSelector() {
        if (processorSelector == null) {
            return transport.getProcessorSelector();
        }

        return processorSelector;
    }

    public void register() throws IOException {

        final FutureImpl<RegisterChannelResult> future =
                Futures.createSafeFuture();

        transport.getNIOChannelDistributor().registerServiceChannelAsync(
                channel,
                SelectionKey.OP_READ, this,
                Futures.toCompletionHandler(future,
                ((UDPNIOTransport) transport).registerChannelCompletionHandler
                ));

        try {
            future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Error registering server channel key", e);
        }
        
        notifyReady();
    }
    
    @Override
    protected void closeGracefully0(
            final CompletionHandler<Closeable> completionHandler,
            final CloseReason closeReason) {
        terminate0(completionHandler, closeReason);
    }


    @Override
    protected void terminate0(final CompletionHandler<Closeable> completionHandler,
            final CloseReason closeReason) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("UDPNIOServerConnection might be only closed by calling unbind().");
        }

        if (completionHandler != null) {
            completionHandler.completed(this);
        }
    }
    
    public void unbind(
            final CompletionHandler<Closeable> completionHandler) {
        super.terminate0(completionHandler, CloseReason.LOCALLY_CLOSED_REASON);
    }

    @Override
    protected void preClose() {
        transport.unbind(this);
        super.preClose();
    }


}
