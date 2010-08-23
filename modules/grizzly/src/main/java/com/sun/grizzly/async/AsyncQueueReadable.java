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
import java.util.concurrent.Future;

/**
 * Object, which is able to read data to the {@link ByteBuffer} 
 * asynchronously, using queue.
 * 
 * @author Alexey Stashok
 */
public interface AsyncQueueReadable {
    /**
     * Method reads data to the {@link ByteBuffer} using async read queue.
     * First, if read queue is empty - it tries to read to the 
     * {@link ByteBuffer} directly (without putting to the queue).
     * If associated read queue is not empty or after direct reading
     * {@link ByteBuffer} still has remaining place for next read - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct reading - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a reading queue - exception notification will come via 
     * <code>AsyncReadCallbackHandler.onIOException()</code>
     * 
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncReadCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will get full
     * @return true, if {@link ByteBuffer} was read completely, 
     *         false if read operation was put to queue
     * @throws java.io.IOException
     */
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler) throws IOException;

    /**
     * Method reads data to the {@link ByteBuffer} using async read queue.
     * First, if read queue is empty - it tries to read to the 
     * {@link ByteBuffer} directly (without putting to the queue).
     * If associated read queue is not empty or after direct reading
     * {@link ByteBuffer} still has remaining place for next read - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct reading - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a reading queue - exception notification will come via 
     * <code>AsyncReadCallbackHandler.onIOException()</code>
     * 
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncReadCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will get full
     * @param condition {@link AsyncReadCondition}, which will be called to
     *                  check if read data is complete, and callbackHandler could
     *                  be called
     * @return true, if {@link ByteBuffer} was read completely, 
     *         false if read operation was put to queue
     * @throws java.io.IOException
     */
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition) throws IOException;
    
    /**
     * Method reads data to the {@link ByteBuffer} using async read queue.
     * First, if read queue is empty - it tries to read to the 
     * {@link ByteBuffer} directly (without putting to the queue).
     * If associated read queue is not empty or after direct reading
     * {@link ByteBuffer} still has remaining place for next read - 
     * {@link ByteBuffer} will be added to {@link AsyncQueue}.
     * If an exception occurs, during direct reading - it will be propagated 
     * to the caller directly, otherwise, if the {@link ByteBuffer} is 
     * added to a reading queue - exception notification will come via 
     * <code>AsyncReadCallbackHandler.onIOException()</code>
     * 
     * @param buffer {@link ByteBuffer}
     * @param callbackHandler {@link AsyncReadCallbackHandler}, 
     *                        which will get notified, when 
     *                        {@link ByteBuffer} will get full
     * @param condition {@link AsyncReadCondition}, which will be called to
     *                  check if read data is complete, and callbackHandler could
     *                  be called
     * @param readPostProcessor post processor, to be called to process read data
     * 
     * @return true, if {@link ByteBuffer} was read completely, 
     *         false if read operation was put to queue
     * @throws java.io.IOException
     */
    public Future<AsyncQueueReadUnit> readFromAsyncQueue(ByteBuffer buffer,
            AsyncReadCallbackHandler callbackHandler, 
            AsyncReadCondition condition, 
            AsyncQueueDataProcessor readPostProcessor) throws IOException;
}
