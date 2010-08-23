/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReadController class represents {@link Controller},
 * which is not itself independent.
 *
 * Should be used for handling OP_READ operations
 * Supports TCP derived protocols
 *
 * @author Alexey Stashok
 */
public class ReadController extends Controller {

    /**
     * Gets {@link SelectorHandler}'s clone, registered 
     * on this{@link ReadController}
     * 
     * @param selectorHandler original {@link SelectorHandler}
     * @return passed {@link SelectorHandler} clone, registered 
     *          on this{@link ReadController}
     */
    public SelectorHandler getSelectorHandlerClone(SelectorHandler selectorHandler) {
        Iterator<SelectorHandler> it = selectorHandlers.iterator();
        while (it.hasNext()) {
            SelectorHandler cloneSelectorHandler = it.next();
            if (cloneSelectorHandler.getStateHolder() == selectorHandler.getStateHolder()) {
                return cloneSelectorHandler;
            }
        }

        return null;
    }

    /**
     * Removes {@link SelectorHandler}'s clone, registered 
     * on this{@link ReadController}
     * 
     * @param selectorHandler
     */
    public void removeSelectorHandlerClone(SelectorHandler selectorHandler) {
        SelectorHandler cloneSelectorHandler = getSelectorHandlerClone(selectorHandler);
        if (cloneSelectorHandler != null) {
            removeSelectorHandler(cloneSelectorHandler);
        }
    }

    /**
     * Add a {@link Channel}
     * to be processed by{@link ReadController}'s
     * {@link SelectorHandler}
     *
     * @param channel new channel to be managed by ReadController
     * @param protocol name of the protocol channel corresponds to
     */
    public void addChannel(SelectableChannel channel, SelectorHandler selectorHandler) {
        selectorHandler.register(channel, SelectionKey.OP_READ);
    }

    /**
     * Start the Controller. If the thread pool and/or Handler has not been
     * defined, the default will be used.
     */
    @Override
    public void start() throws IOException {
        notifyStarted();

        int selectorHandlerCount = selectorHandlers.size();
        readySelectorHandlerCounter = new AtomicInteger(selectorHandlerCount);
        stoppedSelectorHandlerCounter = new AtomicInteger(selectorHandlerCount);

        for (SelectorHandler selectorHandler : selectorHandlers) {
            startSelectorHandlerRunner(selectorHandler);
        }

        waitUntilSelectorHandlersStop();
        selectorHandlers.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() throws IOException {
        if (stoppedSelectorHandlerCounter != null) {
            waitUntilSelectorHandlersStop();
        }
    }
}
