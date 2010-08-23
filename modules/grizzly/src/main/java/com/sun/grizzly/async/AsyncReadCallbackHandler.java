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
import java.nio.channels.SelectionKey;
import java.util.Queue;

/**
 * Callback handler interface, used by {@link AsyncQueueReader} to notify 
 * custom code either about completion of specific {@link ByteBuffer} reading
 * or about IO problem, which occured when reading data to a {@link ByteBuffer}
 * 
 * @author Alexey Stashok
 */
public interface AsyncReadCallbackHandler {
    /**
     * Method will be called by {@link AsyncQueueReader}, if 
     * data was read to the {@link ByteBuffer} from the 
     * {@link SelectableChannel}, associated with {@link SelectionKey},
     * and read data confirms to the user-specific condition (if any was set).
     * 
     * @param key {@link SelectionKey}, associated with input 
     *            {@link SelectableChannel}
     * @param srcAddress sender's {@link SocketAddress}
     * @param readRecord {@link AsyncWriteQueueRecord}, which was successfuly
     *            read
     */
    public void onReadCompleted(SelectionKey key, SocketAddress srcAddress, 
            AsyncQueueReadUnit readRecord);
    
    /**
     * Method will be called by {@link AsyncQueueReader}, if 
     * error occured when reading from the {@link SelectableChannel}, 
     * which is associated with {@link SelectionKey}
     * 
     * @param exception occurred {@link Exception}
     * @param key {@link SelectionKey}, associated with input 
     *            {@link SelectableChannel}
     * @param buffer {@link ByteBuffer}, which supposed to be used for
     *            asynchronous reading. {@link ByteBuffer} could contain
     *            some data, which was successfully read before error occured
     */
    public void onException(Exception exception, SelectionKey key,
            ByteBuffer buffer, Queue<AsyncQueueReadUnit> remainingQueue);
}
