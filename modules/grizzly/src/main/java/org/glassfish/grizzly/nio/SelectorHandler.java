/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Set;

/**
 *
 * @author Alexey Stashok
 */
public interface SelectorHandler {
    long getSelectTimeout();

    void preSelect(SelectorRunner selectorRunner) throws IOException;
    
    Set<SelectionKey> select(SelectorRunner selectorRunner) throws IOException;
    
    void postSelect(SelectorRunner selectorRunner) throws IOException;
    
    void registerKeyInterest(SelectorRunner selectorRunner,
                             SelectionKey key,
                             int interest)
    throws IOException;
    
    void deregisterKeyInterest(SelectorRunner selectorRunner,
                               SelectionKey key,
                               int interest)
    throws IOException;

    void registerChannel(SelectorRunner selectorRunner,
                         SelectableChannel channel,
                         int interest,
                         Object attachment)
    throws IOException;

    GrizzlyFuture<RegisterChannelResult> registerChannelAsync(
                                    SelectorRunner selectorRunner,
                                    SelectableChannel channel,
                                    int interest,
                                    Object attachment,
                                    CompletionHandler<RegisterChannelResult> completionHandler)
    throws IOException;

    /**
     * Deregister the channel from the {@link SelectorRunner}'s Selector.
     * @param selectorRunner {@link SelectorRunner}
     * @param channel {@link SelectableChannel} channel to deregister
     * @throws IOException
     */
    void deregisterChannel(SelectorRunner selectorRunner,
                           SelectableChannel channel) throws IOException;

    /**
     * Deregister the channel from the {@link SelectorRunner}'s Selector.
     * @param selectorRunner {@link SelectorRunner}
     * @param channel {@link SelectableChannel} channel to deregister
     * @param completionHandler {@link CompletionHandler}
     * @throws IOException
     */
    GrizzlyFuture<RegisterChannelResult> deregisterChannelAsync(
                                    SelectorRunner selectorRunner,
                                    SelectableChannel channel,
                                    CompletionHandler<RegisterChannelResult> completionHandler)
    throws IOException;


    GrizzlyFuture<Runnable> executeInSelectorThread(
                                    final SelectorRunner selectorRunner,
                                    final Runnable runnableTask,
                                    final CompletionHandler<Runnable> completionHandler);

    boolean onSelectorClosed(SelectorRunner selectorRunner);
}
