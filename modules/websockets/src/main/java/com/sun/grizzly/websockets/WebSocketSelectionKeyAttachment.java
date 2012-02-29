/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.util.SelectedKeyAttachmentLogic;
import com.sun.grizzly.websockets.draft76.Draft76Handler;

import java.nio.channels.SelectionKey;
import java.util.logging.Level;

public class WebSocketSelectionKeyAttachment extends SelectedKeyAttachmentLogic implements Runnable {
    private final ProtocolHandler handler;
    private final ServerNetworkHandler networkHandler;
    private final ProcessorTask processorTask;
    private final AsyncProcessorTask asyncProcessorTask;
    private SelectionKey key;

    public WebSocketSelectionKeyAttachment(ProtocolHandler snh, ServerNetworkHandler nh, ProcessorTask task, AsyncProcessorTask asyncTask) {
        handler = snh;
        networkHandler = nh;
        processorTask = task;
        asyncProcessorTask = asyncTask;
    }

    @Override
    public boolean timedOut(SelectionKey key) {
        return false;
    }

    public AsyncProcessorTask getAsyncProcessorTask() {
        return asyncProcessorTask;
    }

    @Override
    public boolean handleSelectedKey(SelectionKey key) {
        if (key.isReadable()) {
            this.key = key;
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
            asyncProcessorTask.getThreadPool().execute(this);
        }

        return false;
    }

    public void run() {
        try {
            networkHandler.read();
            handler.readFrame();
            enableRead();
        } catch (WebSocketException e) {
            if (handler.maskData || handler instanceof Draft76Handler) {
                handler.getWebSocket().onClose(null);
            } else {
                try {
                    handler.close(1011, e.getMessage());
                } catch (Exception ee) {
                    // dropped connection most likely
                    handler.getWebSocket().onClose(null);
                }
            }
            
            networkHandler.close();
            WebSocketEngine.logger.log(Level.FINEST, e.getMessage(), e);
        }
    }

    public final void enableRead() {
        processorTask.getSelectorHandler().register(getSelectionKey(), SelectionKey.OP_READ);
    }

    public SelectionKey getSelectionKey() {
        return asyncProcessorTask.getAsyncExecutor().getProcessorTask().getSelectionKey();
    }

    public ProtocolHandler getHandler() {
        return handler;
    }
}
