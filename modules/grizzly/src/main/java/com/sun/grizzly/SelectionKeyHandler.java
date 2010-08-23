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

import com.sun.grizzly.util.Copyable;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

/**
 * A SelectionKeyHandler is used to handle the life cycle of a SelectionKey.
 * Operations like cancelling, registering or closing are handled by
 * SelectionKeyHandler.
 *
 * @author Jeanfrancois Arcand
 */
public interface SelectionKeyHandler extends Handler, Copyable {
    
    /**
     * Get associated {@link SelectorHandler}
     */
    public SelectorHandler getSelectorHandler();

    
    /**
     * Set associated {@link SelectorHandler}
     */
    public void setSelectorHandler(SelectorHandler selectorHandler);
    
    
    /**
     * {@link SelectionKey} process notification
     * @param key {@link SelectionKey} to process
     */
    public void process(SelectionKey key);
    
    
    /**
     * {@link SelectionKey} post process notification
     * @param key {@link SelectionKey} to process
     */
    public void postProcess(SelectionKey key);

    
    /**
     * Attach a times out to the SelectionKey used to cancel 
     * idle connection. Null when the feature is not required.
     *
     * @param key {@link SelectionKey} to register
     * @param currentTime the System.currentTimeMillis
     * 
     * @deprecated
     */
    public void register(SelectionKey key, long currentTime);
    
    
    /**
     * Register a {@link SelectionKey} on {@link Selector}.
     *
     * @param key {@link SelectionKey} 
     * @param selectionKeyOps The interest set to apply when registering.
     * to register
     */
    public void register(SelectionKey key, int selectionKeyOps);

    /**
     * Register a {@link SelectableChannel} on {@link Selector}.
     *
     * @param channel {@link SelectableChannel} 
     * @param selectionKeyOps The interest set to apply when registering.
     * to register
     */
    public void register(SelectableChannel channel, 
            int selectionKeyOps) throws ClosedChannelException;

    /**
     * Register a {@link SelectableChannel} on {@link Selector}.
     *
     * @param channel {@link SelectableChannel}
     * @param selectionKeyOps The interest set to apply when registering.
     * to register
     * @param attachment attachment
     */
    public void register(SelectableChannel channel,
            int selectionKeyOps, Object attachment) throws ClosedChannelException;

    /**
     * Register a set of {@link SelectionKey}s.
     * Note: After processing each {@link SelectionKey} it should be
     * removed from <code>Iterator</code>
     *
     * @param selectionKeySet <code>Iterator</code> of {@link SelectionKey}s 
     * @param selectionKeyOps The interest set to apply when registering.
     * to register
     */
    public void register(Iterator<SelectionKey> keyIterator, int selectionKeyOps);

    
    /**
     * Expire a {@link SelectionKey}. If a {@link SelectionKey} is 
     * inactive for certain time (timeout),  the {@link SelectionKey} 
     * will be cancelled and its associated Channel closed.
     * @param key {@link SelectionKey} to expire
     * @param currentTime the System.currentTimeMillis
     * @deprecated
     */
    public void expire(SelectionKey key, long currentTime);
    
    
    /**
     * Expire a {@link SelectionKey} set. Method checks 
     * each {@link SelectionKey} from the{@link Set}. And if 
     * a {@link SelectionKey} is inactive for certain time (timeout),
     * the {@link SelectionKey} will be cancelled and its associated Channel closed.
     * @param keyIterator <code>Iterator</code> of {@link SelectionKey}s 
     * to expire
     */
    public void expire(Iterator<SelectionKey> keyIterator);

    
    /**
     * Cancel a SelectionKey and close its Channel.
     * @param key {@link SelectionKey} to cancel
     */
    public void cancel(SelectionKey key);
    
        
    /**
     * Close the SelectionKey's channel input or output, but keep alive
     * the SelectionKey.
     * @param key {@link SelectionKey} to close
     */
    public void close(SelectionKey key);
    
}
