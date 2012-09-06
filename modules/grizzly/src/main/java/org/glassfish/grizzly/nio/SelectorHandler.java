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

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Set;
import org.glassfish.grizzly.CompletionHandler;

/**
 *
 * @author Alexey Stashok
 */
public interface SelectorHandler {

    /**
     * The default {@link SelectorHandler} used by all created builder instances.
     */
    public static SelectorHandler DEFAULT_SELECTOR_HANDLER = new DefaultSelectorHandler();

    long getSelectTimeout();

    boolean preSelect(SelectorRunner selectorRunner) throws IOException;
    
    Set<SelectionKey> select(SelectorRunner selectorRunner) throws IOException;
    
    void postSelect(SelectorRunner selectorRunner) throws IOException;
    
    void registerKeyInterest(SelectorRunner selectorRunner,
                             SelectionKey key,
                             int interest)
    throws IOException;
    
    /**
     * Deregisters SelectionKey interest.
     * 
     * This method must be called from the SelectorRunner's thread only!
     * @throws IOException 
     */
    void deregisterKeyInterest(SelectorRunner selectorRunner,
                               SelectionKey key,
                               int interest)
    throws IOException;

    void registerChannel(SelectorRunner selectorRunner,
                         SelectableChannel channel,
                         int interest,
                         Object attachment)
    throws IOException;

    void registerChannelAsync(
                                    SelectorRunner selectorRunner,
                                    SelectableChannel channel,
                                    int interest,
                                    Object attachment,
                                    CompletionHandler<RegisterChannelResult> completionHandler);

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
     */
    void deregisterChannelAsync(
                                    SelectorRunner selectorRunner,
                                    SelectableChannel channel,
                                    CompletionHandler<RegisterChannelResult> completionHandler);


    /**
     * Execute task in a selector thread.
     * Unlike {@link #enque(org.glassfish.grizzly.nio.SelectorRunner, org.glassfish.grizzly.nio.SelectorHandler.Task, org.glassfish.grizzly.CompletionHandler)},
     * this operation will execute the task immediately if the current
     * is a selector thread.
     * 
     * @param selectorRunner
     * @param task
     * @param completionHandler
     */
    void execute(
                                    final SelectorRunner selectorRunner,
                                    final Task task,
                                    final CompletionHandler<Task> completionHandler);

    /**
     * Execute task in a selector thread.
     * Unlike {@link #execute(org.glassfish.grizzly.nio.SelectorRunner, org.glassfish.grizzly.nio.SelectorHandler.Task, org.glassfish.grizzly.CompletionHandler)},
     * this operation will postpone the task execution if current thread
     * is a selector thread, and execute it during the next
     * {@link #select(org.glassfish.grizzly.nio.SelectorRunner)} iteration.
     * 
     * @param selectorRunner
     * @param task
     * @param completionHandler
     */
    void enque(
                                    final SelectorRunner selectorRunner,
                                    final Task task,
                                    final CompletionHandler<Task> completionHandler);

    boolean onSelectorClosed(SelectorRunner selectorRunner);
    
    public static interface Task {
        public boolean run() throws Exception;
    }        
}
