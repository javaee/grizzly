/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;


/**
 * Filter which dispatches parsed fully constructed Messages.
 * <p/>
 * This could  be a plugin point to write business logic which
 * handles requests made by some Endpoit.
 * <p/>
 * Note:
 * Messages are taken from  Grizzly {@link com.sun.grizzly.util.WorkerThread}
 * and dispatched to a new Thread Instance of an configurable ThreadPool.
 * This enables the new Threads for example to be put in blocking mode and
 * not hurting the Grizzly Selection loop.
 *
 * @author John Vieten 22.06.2008
 * @version 1.0
 */
public abstract class MessageDispatcher implements ProtocolFilter {
    public final static String needMoreDataMessageMapKey = "needMoreDataMessageMap";
    // just a little debug help
    protected  int threadCounter = 0;
    protected final ThreadGroup threadGroup = new ThreadGroup("GrizzlySample");
    private ExecutorService executorService = null;
    private boolean shuttingDown = false;

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public boolean postExecute(Context ctx) throws IOException {
        return true;
    }

    public void stop() {
        if (executorService != null) {
            shuttingDown = true;
            executorService.shutdown();
            try {
                executorService.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {

            }
            executorService.isTerminated();
        }
    }

    public boolean execute(Context ctx) throws IOException {

        final MessageBase incomingMessage = (MessageBase) ctx.removeAttribute(ProtocolParser.MESSAGE);

        switch (incomingMessage.getMessageType()) {
            case Message.Message_Error:
                dispatch(incomingMessage, ctx);
                break;
            case Message.Message_Reply:
            case Message.Message_Request:
                if (incomingMessage.moreFragmentsToFollow()) {
                    attachToConnection(incomingMessage, ctx);
                } else {
                    incomingMessage.allDataParsed();
                }
                dispatch(incomingMessage, ctx);
                break;
            case Message.Message_Fragment:
                InputStreamMessage dispatchedMessage = getFromMessageMap(incomingMessage.getUniqueMessageId());
                if (dispatchedMessage == null) {
                    //maybe key was canceled
                    if (!ctx.getSelectionKey().isValid()) {
                        CustomProtocolHelper.logger().log(Level.WARNING, "DispatchedMessage is null (key cancel)");
                        return false;
                    }
                }
                dispatchedMessage.add((FragmentMessage) incomingMessage);
                if (!incomingMessage.moreFragmentsToFollow()) {
                    dispatchedMessage.allDataParsed();
                    Map map = getMessageMap();
                    if(map!=null)
                        map.remove(dispatchedMessage.getUniqueMessageId());
                }

        }


        return false;
    }

    private void attachToConnection(final MessageBase message, final Context ctx) {
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        AttributeHolder connectionAttrs = ctx.getAttributeHolderByScope(Context.AttributeScope.CONNECTION);
        if (connectionAttrs == null) {
            connectionAttrs = workerThread.getAttachment();
            ctx.getSelectionKey().attach(connectionAttrs);
        }

        Map<Integer, MessageBase> map = getMessageMap();
        if (map == null) {
            map = new HashMap<Integer, MessageBase>();
            connectionAttrs.setAttribute(needMoreDataMessageMapKey, map);
        }
        map.put(message.getUniqueMessageId(), message);

    }


    private Map<Integer, MessageBase> getMessageMap() {
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        AttributeHolder connectionAttrs = workerThread.getAttachment();
        return (Map<Integer, MessageBase>) connectionAttrs.getAttribute(needMoreDataMessageMapKey);
    }

    private InputStreamMessage getFromMessageMap(int uniqueId) {
        Map map=getMessageMap();
        if(map==null) return null;
        return (InputStreamMessage) map.get(uniqueId);
    }

    private void dispatch(final Message msg, final Context workerCtx) {

        workerCtx.incrementRefCount();
        synchronized (this) {
            if (executorService == null) {
                executorService = Executors.newCachedThreadPool(new ThreadFactory() {
                    public Thread newThread(Runnable r) {
                        return new Thread(threadGroup, r, "SampleThread No." + (++threadCounter));
                    }
                });
            }
        }
        try {

            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        switch (msg.getMessageType()) {
                            case Message.Message_Request:
                                onRequestMessage((RequestMessage) msg, workerCtx);
                                break;
                            case Message.Message_Reply:
                                break;
                            case Message.Message_Error:
                                onMessageError((MessageError) msg, workerCtx);
                                break;
                            case Message.Message_Fragment:
                                CustomProtocolHelper.logger().log(Level.SEVERE, "Cannot dispatch Fragment");
                        }
                    } finally {
                        workerCtx.getController().returnContext(workerCtx);
                    }
                }
            });

        } catch (RejectedExecutionException exception) {
            workerCtx.getController().returnContext(workerCtx);
            if (shuttingDown) {
                //Ok do nothing because client is shutting down
            } else {
                exception.printStackTrace();
            }

        }
        catch (Throwable exception) {
            exception.printStackTrace();
            workerCtx.getController().returnContext(workerCtx);
        }


    }


    /**
     * Pluginpoint to handle an Message received from some Endpoint
     *
     * @param msg RequestMessage the message received from an Endpoint
     * @param ctx MessageContext Simple API for writing,quering the connection.
     */
    abstract public void onRequestMessage(RequestMessage msg, Context ctx);


    abstract public void onMessageError(MessageError msg, Context ctx);

}
