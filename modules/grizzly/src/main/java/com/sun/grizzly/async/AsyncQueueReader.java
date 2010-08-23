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

package com.sun.grizzly.async;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Future;

/**
 *
 * @author Alexey Stashok
 */
public interface AsyncQueueReader {
    public Future<AsyncQueueReadUnit> read(SelectionKey key, ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler) throws IOException;

    public Future<AsyncQueueReadUnit> read(SelectionKey key, ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition) throws IOException;
    
    public Future<AsyncQueueReadUnit> read(SelectionKey key, ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition, 
            AsyncQueueDataProcessor readPostProcessor) throws IOException;

    /**
     * Checks whether {@link AsyncQueueReader} is registered
     * for the {@link SelectableChannel}, associated with the given 
     * {@link SelectionKey}
     * 
     * @param key {@link SelectionKey} associated with {@link SelectableChannel}
     * @return true, if there is ready data. False otherwise.
     */
    public boolean isReady(SelectionKey key);
    
    /**
     * Gets ready asynchronous queue elements to be read from the
     * {@link SelectableChannel}, associated with the
     * given {@link SelectionKey}
     *
     * @param key {@link SelectionKey} associated with {@link SelectableChannel}
     * @return ready asynchronous queue elements to be read to the
     * {@link SelectableChannel}, associated with the
     * given {@link SelectionKey}/
     */
    public AsyncQueue.AsyncQueueEntry getAsyncQueue(SelectionKey key);

    /**
     * Callback method, which should be called by {@link SelectorHandler} to
     * notify, that {@link SelectableChannel}, associated with the given
     * {@link SelectableChannel} has ready data for reading.
     * 
     * @param key {@link SelectionKey} associated with {@link SelectableChannel}
     * @throws <code>java.io.IOException</code>
     */
    public void onRead(SelectionKey key) throws IOException;
    
    /**
     * Callback method, which should be called by {@link SelectorHandler} to
     * notify, that given {@link SelectableChannel} is going to be closed, so
     * related data could be released from 
     * {@link AsyncQueueReader}
     * 
     * @param {@link SelectableChannel}
     * @throws java.io.IOException
     */
    public void onClose(SelectableChannel channel);
    
    /**
     * Close {@link AsyncQueueReader} and release its resources
     */
    public void close();
    
}
