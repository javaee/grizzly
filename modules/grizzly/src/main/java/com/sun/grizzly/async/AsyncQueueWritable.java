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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

/**
 * Object, which is able to send {@link ByteBuffer} data asynchronously,
 * using queue.
 * 
 * @author Alexey Stashok
 */
public interface AsyncQueueWritable {
    /**
     * Method writes {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to write {@link ByteBuffer}
     * directly (without putting to the queue).
     * If associated write queue is not empty or after direct writing 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise it will be just logged by 
     * Grizzly framework.
     * 
     * @param buffer {@link ByteBuffer}
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer)
            throws IOException;
    
    /**
     * Method writes {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to write {@link ByteBuffer}
     * directly (without putting to the queue).
     * If associated write queue is not empty or after direct writing 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a writing queue - exception notification will come via 
     * <code>AsyncWriteCallbackHandler.onIOException()</code>
     * 
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncWriteCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will be completely written
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler) throws IOException;

    /**
     * Method writes {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to write {@link ByteBuffer}
     * directly (without putting to the queue).
     * If associated write queue is not empty or after direct writing 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a writing queue - exception notification will come via 
     * <code>AsyncWriteCallbackHandler.onIOException()</code>
     * Before data will be written on {@link SelectableChannel}, first it
     * will be passed for preprocessing to <code>AsyncQueueDataProcessor</code>,
     * and then preprocessor result data 
     * (<code>AsyncQueueDataProcessor.getResultByteBuffer()</code>) will be 
     * written on the {@link SelectableChannel}.
     * 
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncWriteCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will be completely written
     * @param writePreProcessor <code>AsyncQueueDataProcessor</code>, which
     *                        will perform data processing, before it will be 
     *                        written on {@link SelectableChannel}
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor) throws IOException;

    /**
     * Method writes {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to write {@link ByteBuffer}
     * directly (without putting to the queue).
     * If associated write queue is not empty or after direct writing 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a writing queue - exception notification will come via 
     * <code>AsyncWriteCallbackHandler.onIOException()</code>
     * Before data will be written on {@link SelectableChannel}, first it
     * will be passed for preprocessing to <code>AsyncQueueDataProcessor</code>,
     * and then preprocessor result data 
     * (<code>AsyncQueueDataProcessor.getResultByteBuffer()</code>) will be 
     * written on the {@link SelectableChannel}.
     * 
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncWriteCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will be completely written
     * @param writePreProcessor <code>AsyncQueueDataProcessor</code>, which
     *                        will perform data processing, before it will be 
     *                        written on {@link SelectableChannel}
     * @param isCloneByteBuffer if true - {@link AsyncQueueWriter} 
     *                          will clone given
     *                          {@link ByteBuffer} before puting it to the 
     *                          {@link AsyncQueue}
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor,
            ByteBufferCloner cloner) throws IOException;

    /**
     * Method sends {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to send {@link ByteBuffer}
     * to the given {@link SocketAddress} directly 
     * (without putting to the queue).
     * If associated write queue is not empty or after direct sending 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise it will be just logged by 
     * Grizzly framework.
     * 
     * @param dstAddress destination {@link SocketAddress} data will 
     *                   be sent to
     * @param buffer {@link ByteBuffer}
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer) throws IOException;
    
    /**
     * Method sends {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to send {@link ByteBuffer}
     * to the given {@link SocketAddress} directly 
     * (without putting to the queue).
     * If associated write queue is not empty or after direct sending 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a writing queue - exception notification will come via 
     * <code>AsyncWriteCallbackHandler.onIOException()</code>
     * 
     * @param dstAddress destination {@link SocketAddress} data will 
     *                   be sent to
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncWriteCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will be completely written
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler) throws IOException;

    /**
     * Method sends {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to send {@link ByteBuffer}
     * to the given {@link SocketAddress} directly 
     * (without putting to the queue).
     * If associated write queue is not empty or after direct sending 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a writing queue - exception notification will come via 
     * <code>AsyncWriteCallbackHandler.onIOException()</code>
     * Before data will be written on {@link SelectableChannel}, first it
     * will be passed for preprocessing to <code>AsyncQueueDataProcessor</code>,
     * and then preprocessor result data 
     * (<code>AsyncQueueDataProcessor.getResultByteBuffer()</code>) will be 
     * written on the {@link SelectableChannel}.
     * 
     * @param dstAddress destination {@link SocketAddress} data will 
     *                   be sent to
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncWriteCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will be completely written
     * @param writePreProcessor <code>AsyncQueueDataProcessor</code>, which
     *                        will perform data processing, before it will be 
     *                        written on {@link SelectableChannel}
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler,
            AsyncQueueDataProcessor writePreProcessor) throws IOException;

    /**
     * Method sends {@link ByteBuffer} using async write queue.
     * First, if write queue is empty - it tries to send {@link ByteBuffer}
     * to the given {@link SocketAddress} directly 
     * (without putting to the queue).
     * If associated write queue is not empty or after direct sending 
     * {@link ByteBuffer} still has ready data to be written - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct writing - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a writing queue - exception notification will come via 
     * <code>AsyncWriteCallbackHandler.onIOException()</code>
     * Before data will be written on {@link SelectableChannel}, first it
     * will be passed for preprocessing to <code>AsyncQueueDataProcessor</code>,
     * and then preprocessor result data 
     * (<code>AsyncQueueDataProcessor.getResultByteBuffer()</code>) will be 
     * written on the {@link SelectableChannel}.
     * 
     * @param dstAddress destination {@link SocketAddress} data will 
     *                   be sent to
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncWriteCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will be completely written
     * @param writePreProcessor <code>AsyncQueueDataProcessor</code>, which
     *                        will perform data processing, before it will be 
     *                        written on {@link SelectableChannel}
     * @param isCloneByteBuffer if true - {@link AsyncQueueWriter} 
     *                          will clone given
     *                          {@link ByteBuffer} before puting it to the 
     *                          {@link AsyncQueue}
     * @throws java.io.IOException
     */
    public Future<AsyncQueueWriteUnit> writeToAsyncQueue(
            SocketAddress dstAddress, ByteBuffer buffer,
            AsyncWriteCallbackHandler callbackHandler, 
            AsyncQueueDataProcessor writePreProcessor,
            ByteBufferCloner cloner) throws IOException;
}
