/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.test.cachetest.testobjects;

import com.sun.grizzly.CallbackHandler;
import com.sun.grizzly.util.ConcurrentLinkedQueuePool;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * copy pasted from com.sun.grizzly so we could add public constructors without changing the class in trunk.
 *
 * @author Alexey Stashok
 */
public class SelectionKeyOP {
    private int op;
    private SelectionKey key;
    private SelectableChannel channel;
    
    private static ConcurrentLinkedQueuePool<SelectionKeyOP> readWritePool =
            new ConcurrentLinkedQueuePool<SelectionKeyOP>() {
                @Override
                public SelectionKeyOP newInstance() {
                    return new SelectionKeyOP();
                }
            };
    
    private static ConcurrentLinkedQueuePool<SelectionKeyOP> connectPool =
            new ConcurrentLinkedQueuePool<SelectionKeyOP>() {
                @Override
                public SelectionKeyOP newInstance() {
                    return new ConnectSelectionKeyOP();
                }
            };
    
    public static SelectionKeyOP aquireSelectionKeyOP(int op) {
        if (op == SelectionKey.OP_READ || op == SelectionKey.OP_WRITE ||
                op == (SelectionKey.OP_WRITE | SelectionKey.OP_READ)) {
            SelectionKeyOP operation = readWritePool.poll();
            return operation;
        } else if (op == SelectionKey.OP_CONNECT) {
            SelectionKeyOP operation = connectPool.poll();
            return operation;
        }
        
        throw new IllegalStateException("Unknown operation or operation is not supported");
    }
    
    public static void releaseSelectionKeyOP(SelectionKeyOP operation) {
        int op = operation.op;
        operation.recycle();
        
        if (op == SelectionKey.OP_READ || op == SelectionKey.OP_WRITE ||
                op == (SelectionKey.OP_WRITE | SelectionKey.OP_READ)) {
            readWritePool.offer(operation);
            return;
        } else if (op == SelectionKey.OP_CONNECT) {
            connectPool.offer(operation);
            return;
        }
        
        throw new IllegalStateException("Unknown operation or operation is not supported");
    }

    public SelectionKeyOP(int op) {
        this.op = op;
    }

    private SelectionKeyOP() {
    }
    
    public int getOp() {
        return op;
    }

    public void setOp(int op) {
        this.op = op;
    }

    public SelectionKey getSelectionKey() {
        return key;
    }

    public void setSelectionKey(SelectionKey key) {
        this.key = key;
    }

    public SelectableChannel getChannel() {
        return channel;
    }

    public void setChannel(SelectableChannel channel) {
        this.channel = channel;
    }
    
    public void recycle() {
        op = 0;
        key = null;
        channel = null;
    }
    
    public static class ConnectSelectionKeyOP extends SelectionKeyOP {
        private SocketAddress localAddress;
        private SocketAddress remoteAddress;
        private CallbackHandler callbackHandler;

        public ConnectSelectionKeyOP(){

        }
        
        public ConnectSelectionKeyOP(int op) {
            super(op);
        }

        public SocketAddress getLocalAddress() {
            return localAddress;
        }

        public void setLocalAddress(SocketAddress localAddress) {
            this.localAddress = localAddress;
        }

        public SocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        public void setRemoteAddress(SocketAddress remoteAddress) {
            this.remoteAddress = remoteAddress;
        }

        public CallbackHandler getCallbackHandler() {
            return callbackHandler;
        }

        public void setCallbackHandler(CallbackHandler callbackHandler) {
            this.callbackHandler = callbackHandler;
        }
        
        @Override
        public void recycle() {
            localAddress = null;
            remoteAddress = null;
            callbackHandler = null;
        }
    }
}
