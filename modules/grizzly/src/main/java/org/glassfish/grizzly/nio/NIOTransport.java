/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio;

import org.glassfish.grizzly.AbstractTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransportProbe;
import java.io.IOException;
import java.nio.channels.Selector;

/**
 *
 * @author oleksiys
 */
public abstract class NIOTransport extends AbstractTransport {
    protected SelectorHandler selectorHandler;
    protected SelectionKeyHandler selectionKeyHandler;

    protected int selectorRunnersCount;
    
    protected SelectorRunner[] selectorRunners;
    
    protected NIOChannelDistributor nioChannelDistributor;

    public NIOTransport(final String name) {
        super(name);
    }

    public SelectionKeyHandler getSelectionKeyHandler() {
        return selectionKeyHandler;
    }

    public void setSelectionKeyHandler(final SelectionKeyHandler selectionKeyHandler) {
        this.selectionKeyHandler = selectionKeyHandler;
        notifyProbesConfigChanged(this);
    }

    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }

    public void setSelectorHandler(final SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
        notifyProbesConfigChanged(this);
    }

    public int getSelectorRunnersCount() {
        return selectorRunnersCount;
    }

    public void setSelectorRunnersCount(final int selectorRunnersCount) {
        this.selectorRunnersCount = selectorRunnersCount;
        notifyProbesConfigChanged(this);
    }

    
    protected synchronized void startSelectorRunners() throws IOException {
        selectorRunners = new SelectorRunner[selectorRunnersCount];
        
        for (int i = 0; i < selectorRunnersCount; i++) {
            SelectorRunner runner =
                    new SelectorRunner(this, SelectorFactory.instance().create());
            runner.start();
            selectorRunners[i] = runner;
        }
    }
    
    protected synchronized void stopSelectorRunners() throws IOException {
        if (selectorRunners == null) {
            return;
        }

        for (int i = 0; i < selectorRunners.length; i++) {
            SelectorRunner runner = selectorRunners[i];
            if (runner != null) {
                runner.stop();
                selectorRunners[i] = null;

                Selector selector = runner.getSelector();
                if (selector != null) {
                    try {
                        selector.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        selectorRunners = null;
    }

    public NIOChannelDistributor getNioChannelDistributor() {
        return nioChannelDistributor;
    }

    public void setNioChannelDistributor(final NIOChannelDistributor
            nioChannelDistributor) {
        this.nioChannelDistributor = nioChannelDistributor;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyTransportError(final Throwable error) {
        notifyProbesError(this, error);
    }

    protected SelectorRunner[] getSelectorRunners() {
        return selectorRunners;
    }

    /**
     * Notify registered {@link TransportProbe}s about the error.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesError(final NIOTransport transport,
            final Throwable error) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onErrorEvent(transport, error);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the start event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesStart(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onStartEvent(transport);
            }
        }
    }
    
    /**
     * Notify registered {@link TransportProbe}s about the stop event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesStop(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onStopEvent(transport);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the pause event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesPause(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onPauseEvent(transport);
            }
        }
    }

    /**
     * Notify registered {@link TransportProbe}s about the resume event.
     *
     * @param transport the <tt>Transport</tt> event occurred on.
     */
    protected static void notifyProbesResume(final NIOTransport transport) {
        final TransportProbe[] probes =
                transport.transportMonitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (TransportProbe probe : probes) {
                probe.onResumeEvent(transport);
            }
        }
    }

    @Override
    protected abstract void closeConnection(Connection connection)
            throws IOException;
}
