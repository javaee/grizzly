/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import org.glassfish.grizzly.nio.NIOTransport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.util.Random;

/**
 * @since 2.2.19
 */
public abstract class AbstractBindingHandler implements SocketBinder {
    protected static final Random RANDOM = new Random();
    protected final NIOTransport transport;
    protected Processor processor;
    protected ProcessorSelector processorSelector;

    // ------------------------------------------------------------ Constructors


    public AbstractBindingHandler(final NIOTransport transport) {
        this.transport = transport;
        this.processor = transport.getProcessor();
        this.processorSelector = transport.getProcessorSelector();
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * Get the default {@link Processor} to process {@link IOEvent}, occurring
     * on connection phase.
     *
     * @return the default {@link Processor} to process {@link IOEvent},
     *         occurring on connection phase.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the default {@link Processor} to process {@link IOEvent}, occurring
     * on connection phase.
     *
     * @param processor the default {@link Processor} to process
     *                  {@link IOEvent}, occurring on connection phase.
     */
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * Gets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occurring on connection phase.
     *
     * @return the default {@link ProcessorSelector}, which will be used to get
     *         {@link Processor} to process I/O events, occurring on connection phase.
     */
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    /**
     * Sets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process I/O events, occurring on connection phase.
     *
     * @param processorSelector the default {@link ProcessorSelector},
     *                          which will be used to get {@link Processor} to process I/O events,
     *                          occurring on connection phase.
     */
    public void setProcessorSelector(final ProcessorSelector processorSelector) {
        this.processorSelector = processorSelector;
    }


    // ----------------------------------------------- Methods from SocketBinder

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection bind(final int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection bind(final String host, final int port) throws IOException {
        return bind(new InetSocketAddress(host, port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection bind(final String host, final int port, final int backlog)
            throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection bind(final String host, final PortRange portRange,
            final int backlog) throws IOException {
        IOException ioException;
        final int lower = portRange.getLower();
        final int range = portRange.getUpper() - lower + 1;

        int offset = RANDOM.nextInt(range);
        final int start = offset;

        do {
            final int port = lower + offset;

            try {
                return bind(host, port, backlog);
            } catch (IOException e) {
                ioException = e;
            }

            offset = (offset + 1) % range;
        } while (offset != start);

        throw ioException;
    }

    /**
     * This operation is not supported by implementations of {@link AbstractBindingHandler}.
     *
     * @throws UnsupportedOperationException
     */
    @Override
    public final void unbindAll() {
        throw new UnsupportedOperationException();
    }


    // ------------------------------------------------------- Protected Methods


    @SuppressWarnings("unchecked")
    protected <T> T getSystemInheritedChannel(final Class<?> channelType)
    throws IOException {
        final Channel inheritedChannel = System.inheritedChannel();

        if (inheritedChannel == null) {
            throw new IOException("Inherited channel is not set");
        }
        if (!(channelType.isInstance(inheritedChannel))) {
            throw new IOException("Inherited channel is not "
                    + channelType.getName()
                    + ", but "
                    + inheritedChannel.getClass().getName());
        }
        return (T) inheritedChannel;
    }


    // ----------------------------------------------------------- Inner Classes

    /**
     * Builder
     *
     * @param <E>
     */
    @SuppressWarnings("unchecked")
    public abstract static class Builder<E extends Builder> {

        protected Processor processor;
        protected ProcessorSelector processorSelector;

        public E processor(final Processor processor) {
            this.processor = processor;
            return (E) this;
        }

        public E processorSelector(final ProcessorSelector processorSelector) {
            this.processorSelector = processorSelector;
            return (E) this;
        }

        public AbstractBindingHandler build() {
            AbstractBindingHandler bindingHandler = create();
            if (processor != null) {
                bindingHandler.setProcessor(processor);
            }
            if (processorSelector != null) {
                bindingHandler.setProcessorSelector(processorSelector);
            }
            return bindingHandler;
        }

        protected abstract AbstractBindingHandler create();

    }
}
