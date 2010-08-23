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

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Queue;

/**
 * Callback handler interface, used by {@link AsyncQueueWriter} to notify 
 * custom code either about completion of specific {@link ByteBuffer} writing
 * or about IO problem, which occured when writing {@link ByteBuffer}
 * 
 * @author Alexey Stashok
 */
public interface AsyncWriteCallbackHandler {
    /**
     * Method will be called by {@link AsyncQueueWriter}, if 
     * whole {@link ByteBuffer} data was written to the 
     * {@link SelectableChannel}, associated with {@link SelectionKey}
     * 
     * @param key {@link SelectionKey}, associated with output 
     *            {@link SelectableChannel}
     * @param writtenRecord {@link AsyncWriteQueueRecord}, which was successfuly
     *            written
     */
    public void onWriteCompleted(SelectionKey key,
            AsyncQueueWriteUnit writtenRecord);
    
    /**
     * Method will be called by {@link AsyncQueueWriter}, if 
     * error occured when writing {@link ByteBuffer} to the 
     * {@link SelectableChannel}, associated with {@link SelectionKey}
     * 
     * @param exception occurred {@link Exception}
     * @param key {@link SelectionKey}, associated with output 
     *            {@link SelectableChannel}
     * @param buffer {@link ByteBuffer}, which data was failed to be written
     * @param remainingQueue queue of write records which were not written yet at the
     *          moment, when exception occured
     */
    public void onException(Exception exception, SelectionKey key,
            ByteBuffer buffer, Queue<AsyncQueueWriteUnit> remainingQueue);
}
