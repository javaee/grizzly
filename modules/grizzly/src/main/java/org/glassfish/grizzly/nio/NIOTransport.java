/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.util.Random;

/**
 *
 * @author oleksiys
 */
public abstract class NIOTransport extends AbstractTransport {
    protected static final Random RANDOM = new Random();
    
    protected SelectorHandler selectorHandler;
    protected SelectionKeyHandler selectionKeyHandler;

    protected int selectorRunnersCount;
    
    protected SelectorRunner[] selectorRunners;
    
    protected NIOChannelDistributor nioChannelDistributor;

    protected SelectorProvider selectorProvider = SelectorProvider.provider();
    
    public NIOTransport(final String name) {
        super(name);

        selectorRunnersCount = Runtime.getRuntime().availableProcessors();
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
        kernelPoolConfig.setCorePoolSize(selectorRunnersCount);
        kernelPoolConfig.setMaxPoolSize(selectorRunnersCount);
        notifyProbesConfigChanged(this);
    }

    /**
     * Get the {@link SelectorProvider} to be used by this transport.
     * 
     * @return the {@link SelectorProvider} to be used by this transport.
     */    
    public SelectorProvider getSelectorProvider() {
        return selectorProvider;
    }

    /**
     * Set the {@link SelectorProvider} to be used by this transport.
     *
     * @param selectorProvider the {@link SelectorProvider}.
     */
    public void setSelectorProvider(final SelectorProvider selectorProvider) {
        this.selectorProvider = selectorProvider != null
                ? selectorProvider
                : SelectorProvider.provider();
    }

    @Override
    public void start() throws IOException {
        if (selectorProvider == null) {
            selectorProvider = SelectorProvider.provider();
        }
    }
    
    protected synchronized void startSelectorRunners() throws IOException {
        selectorRunners = new SelectorRunner[selectorRunnersCount];
        
        for (int i = 0; i < selectorRunnersCount; i++) {
            final SelectorRunner runner = SelectorRunner.create(this);
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
            }
        }

        selectorRunners = null;
    }

    public NIOChannelDistributor getNIOChannelDistributor() {
        return nioChannelDistributor;
    }

    public void setNIOChannelDistributor(final NIOChannelDistributor nioChannelDistributor) {
        this.nioChannelDistributor = nioChannelDistributor;
        notifyProbesConfigChanged(this);
    }

    /**
     * {@inheritDoc}
     */
    protected SelectorRunner[] getSelectorRunners() {
        return selectorRunners;
    }

    @Override
    protected abstract void closeConnection(Connection connection)
            throws IOException;
}
