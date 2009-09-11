/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package com.sun.grizzly;

import com.sun.grizzly.Context.OpType;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.SelectedKeyAttachmentLogic;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import com.sun.grizzly.util.StateHolder.ConditionListener;
import com.sun.grizzly.util.WorkerThreadImpl;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class is responsible for processing certain (single)
 * {@link SelectorHandler}
 * 
 * @author Alexey Stashok
 */
public class SelectorHandlerRunner implements Runnable {
    /**
     * Default Logger.
     */
    protected static Logger logger = Logger.getLogger("grizzly");

    /**
     * The threshold for detecting selector.select spin on linux,
     * used for enabling workaround to prevent server from hanging.
     */
    private final static int spinRateTreshold = 2000;

    private final SelectorHandler selectorHandler;
    private final Controller controller;

    private boolean isPostponed;

    private SelectionKey key;
    private Set<SelectionKey> readyKeys;
    private NIOContext serverContext;
    private StateHolder<State> controllerStateHolder;
    private StateHolder<State> selectorHandlerStateHolder;

    public SelectorHandlerRunner(Controller controller, SelectorHandler selectorHandler) {
        this.controller = controller;
        this.selectorHandler = selectorHandler;
    }
    
    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }
    
    public void run() {
        if (!isPostponed) {
            serverContext = (NIOContext)controller.pollContext();
            serverContext.setSelectorHandler(selectorHandler);

            controllerStateHolder = controller.getStateHolder();
            selectorHandlerStateHolder = selectorHandler.getStateHolder();
        }
        
        ((WorkerThreadImpl)Thread.currentThread()).setPendingIOhandler(selectorHandler);
        boolean isPostponedInThread = false;
        
        try {
            if (isPostponed) {
                isPostponed = false;

                isPostponedInThread = !continueSelect(selectorHandler, serverContext);
                if (isPostponedInThread) {
                    return;
                }
            } else {
                selectorHandler.getStateHolder().setState(State.STARTED);
            }

            State controllerState;
            State selectorHandlerState;
            while ((controllerState = controllerStateHolder.getState(false)) != State.STOPPED &&
                    (selectorHandlerState = selectorHandlerStateHolder.getState(false)) != State.STOPPED) {
                
                if (controllerState != State.PAUSED &&
                        selectorHandlerState != State.PAUSED) {
                    isPostponedInThread = !doSelect(selectorHandler, serverContext);
                    if (isPostponedInThread) {
                        return;
                    }
                    
                } else {
                    doSelectorPaused(controllerState, selectorHandlerState);
                }
            }
        } finally {
            if (!isPostponedInThread) {
                selectorHandler.shutdown();
                controller.notifyStopped();
            }
        }
    }

    protected boolean continueSelect(final SelectorHandler selectorHandler,
            final NIOContext serverCtx) {
        try {

            if (key != null && !handleSelectedKey(key, selectorHandler, serverCtx)) {
                return false;
            }

            if (!handleSelectedKeys(readyKeys, selectorHandler, serverCtx)) {
                return false;
            }

            selectorHandler.postSelect(serverCtx);
        } catch (Throwable e) {
            handleSelectException(e, selectorHandler, null);
        }
        
        return true;
    }

    /**
     * This method handle the processing of all Selector's interest op
     * (OP_ACCEPT,OP_READ,OP_WRITE,OP_CONNECT) by delegating to its Handler.
     * By default, all java.nio.channels.Selector operations are implemented
     * using SelectorHandler. All SelectionKey operations are implemented by
     * SelectionKeyHandler. Finally, ProtocolChain creation/re-use are implemented
     * by InstanceHandler.
     * @param selectorHandler - the {@link SelectorHandler}
     */
    protected boolean doSelect(final SelectorHandler selectorHandler,
            final NIOContext serverCtx) {
        try {
            if (selectorHandler.getSelectionKeyHandler() == null) {
                initSelectionKeyHandler(selectorHandler);
            }

            selectorHandler.preSelect(serverCtx);

            readyKeys = selectorHandler.select(serverCtx);

            final boolean isSpinWorkaround = Controller.isLinux &&
                    selectorHandler instanceof LinuxSpinningWorkaround;

            if (readyKeys.size() != 0) {
                if (isSpinWorkaround) {
                    ((LinuxSpinningWorkaround) selectorHandler).resetSpinCounter();
                }
                
                if (!handleSelectedKeys(readyKeys, selectorHandler, serverCtx)) {
                    return false;
                }

            } else if (isSpinWorkaround) {
                long sr = ((LinuxSpinningWorkaround) selectorHandler).getSpinRate();
                if (sr > spinRateTreshold) {
                    ((LinuxSpinningWorkaround) selectorHandler).workaroundSelectorSpin();
                }
            }

            selectorHandler.postSelect(serverCtx);
        } catch (Throwable e) {
            handleSelectException(e, selectorHandler, null);
        }

        return true;
    }


    /**
     * Init SelectionKeyHandler
     * @param selectorHandler
     */
    private void initSelectionKeyHandler(SelectorHandler selectorHandler) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "Set DefaultSelectionKeyHandler to SelectorHandler: "
                    + selectorHandler);
        }
        SelectionKeyHandler assgnSelectionKeyHandler = null;
        if (selectorHandler.getPreferredSelectionKeyHandler() != null) {
            Class<? extends SelectionKeyHandler> keyHandlerClass =
                    selectorHandler.getPreferredSelectionKeyHandler();
            try {
                assgnSelectionKeyHandler = keyHandlerClass.newInstance();
                assgnSelectionKeyHandler.setSelectorHandler(selectorHandler);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "Exception initializing preffered SelectionKeyHandler '" +
                            keyHandlerClass + "' for the SelectorHandler '" +
                            selectorHandler + "'");
                }
            }
        }
        if (assgnSelectionKeyHandler == null) {
            assgnSelectionKeyHandler = new DefaultSelectionKeyHandler(selectorHandler);
        }
        selectorHandler.setSelectionKeyHandler(assgnSelectionKeyHandler);
    }

    /**
     * logic performed on the selected keys
     * @param readyKeys
     * @param selectorHandler
     * @param serverCtx
     */
    private boolean handleSelectedKeys(final Set<SelectionKey> readyKeys,
            final SelectorHandler selectorHandler, final NIOContext serverCtx) {
        final Iterator<SelectionKey> it = readyKeys.iterator();
        while(it.hasNext()) {
            key = it.next();
            it.remove();
            if (!handleSelectedKey(key, selectorHandler, serverCtx)) {
                return false;
            }
        }

        return true;
    }

    private boolean handleSelectedKey(final SelectionKey key,
            final SelectorHandler selectorHandler, final NIOContext serverCtx) {

        final boolean isLogLevelFine = logger.isLoggable(Level.FINE);
        try {

            Object attachment = key.attachment();
            if (attachment instanceof SelectedKeyAttachmentLogic) {
                ((SelectedKeyAttachmentLogic) attachment).handleSelectedKey(key);
                return true;
            }

            if (!key.isValid()) {
                selectorHandler.addPendingKeyCancel(key);
                return true;
            }

            final int readyOps = key.readyOps();
            if ((readyOps & SelectionKey.OP_ACCEPT) != 0) {
                if (controller.getReadThreadsCount() > 0 &&
                        controller.multiReadThreadSelectorHandler.supportsProtocol(selectorHandler.protocol())) {
                    if (isLogLevelFine) {
                        dolog("OP_ACCEPT passed to multi readthread handler on ", key);
                    }
                    controller.multiReadThreadSelectorHandler.onAcceptInterest(key, serverCtx);
                } else {
                    if (isLogLevelFine) {
                        dolog("OP_ACCEPT on ", key);
                    }
                    selectorHandler.onAcceptInterest(key, serverCtx);
                }
                return true;
            }
            if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
                if (isLogLevelFine) {
                    dolog("OP_CONNECT on ", key);
                }
                selectorHandler.onConnectInterest(key, serverCtx);
                return true;
            }

            boolean delegateToWorker = false;
            OpType opType = null;
            boolean skipOpWrite = false;
            // OP_READ will always be processed first, then
            // based on the handleReadWriteConcurrently, the OP_WRITE
            // might be processed just after or during the next
            // Selector.select() invocation.
            if ((readyOps & SelectionKey.OP_READ) != 0) {
                if (isLogLevelFine) {
                    dolog("OP_READ on ", key);
                }
                delegateToWorker = selectorHandler.onReadInterest(key, serverCtx);
                if (delegateToWorker) {
                    opType = OpType.OP_READ;
                }
                if (!controller.isHandleReadWriteConcurrently()) {
                    skipOpWrite = true;
                }
            }
            // The OP_READ processing might have closed the
            // Selection, hence we must make sure the
            // SelectionKey is still valid.
            if (!skipOpWrite && (readyOps & SelectionKey.OP_WRITE) != 0 && key.isValid()) {
                if (isLogLevelFine) {
                    dolog("OP_WRITE on ", key);
                }
                boolean opWriteDelegate = selectorHandler.onWriteInterest(key, serverCtx);
                delegateToWorker |= opWriteDelegate;
                if (opWriteDelegate) {
                    if (opType == OpType.OP_READ) {
                        opType = OpType.OP_READ_WRITE;
                    } else {
                        opType = OpType.OP_WRITE;
                    }
                }
            }

            if (delegateToWorker) {
                if (controller.useLeaderFollowerStrategy()) {
                    /*
                     * If Leader/follower strategy is used - we postpone this
                     * SelectorHandlerRunner and pass it for execution to a
                     * worker thread pool.
                     * The current Context task will be executed in current
                     * Thread and after that the Thread will be released.
                     */
                    selectorHandler.getThreadPool().execute(postpone());
                    NIOContext context = (NIOContext)controller.pollContext();
                    controller.configureContext(key, opType, context,
                            selectorHandler);
                    ((WorkerThreadImpl) Thread.currentThread()).reset();
                    context.execute(context.getProtocolChainContextTask(), false);
                    return false;
                } else {
                    NIOContext context = (NIOContext)controller.pollContext();
                    controller.configureContext(key, opType, context,
                            selectorHandler);
                    context.execute(context.getProtocolChainContextTask());
                }
            }
        } catch (Throwable e) {
            handleSelectException(e, selectorHandler, key);
        }

        return true;
    }

    private SelectorHandlerRunner postpone() {
        key = null;
        isPostponed = true;
        ((WorkerThreadImpl)Thread.currentThread()).setPendingIOhandler(null);
        return this;
    }

    private void dolog(String msg, SelectionKey key) {
        if (logger.isLoggable(Level.FINE)){
            logger.log(Level.FINE, msg + key + " attachment: " + key.attachment());
        }
    }

    /**
     *
     * @param e
     * @param selectorHandler
     * @param key
     */
    private void handleSelectException(Throwable e,
            SelectorHandler selectorHandler, SelectionKey key) {
        final StateHolder<State> stateHolder = controller.getStateHolder();
        if (e instanceof CancelledKeyException) {
            // Exception occurs, when channel is getting asynchronously closed
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "The key is cancelled asynchronously!");
            }
        } else if (e instanceof ClosedSelectorException) {
            // TODO: This could indicate that the Controller is
            //       shutting down. Hence, we need to handle this Exception
            //       appropriately. Perhaps check the state before logging
            //       what's happening ?
            if (stateHolder.getState() == State.STARTED &&
                    selectorHandler.getStateHolder().getState() == State.STARTED) {
                logger.log(Level.WARNING, "Selector was unexpectedly closed.", e);
                controller.notifyException(e);
                try {
                    if (Controller.isLinux && selectorHandler instanceof LinuxSpinningWorkaround) {
                        ((LinuxSpinningWorkaround) selectorHandler).workaroundSelectorSpin();
                    } else {
                        switchToNewSelector(selectorHandler);
                    }
                } catch (Exception ee) {
                    logger.log(Level.SEVERE, "Can not workaround Selector close.", ee);
                }
            } else {
                logger.log(Level.FINE, "doSelect Selector closed");
            }

        } else if (e instanceof ClosedChannelException) {
            // Don't use stateLock. This case is not strict
            if (stateHolder.getState() == State.STARTED &&
                    selectorHandler.getStateHolder().getState() == State.STARTED) {
                logger.log(Level.WARNING, "Channel was unexpectedly closed");
                if (key != null) {
                    selectorHandler.getSelectionKeyHandler().cancel(key);
                }
                controller.notifyException(e);
            }
        } else if (e instanceof IOException) {
            // TODO: This could indicate that the Controller is
            //       shutting down. Hence, we need to handle this Exception
            //       appropriately. Perhaps check the state before logging
            //       what's happening ?
            if (stateHolder.getState() == State.STARTED &&
                    selectorHandler.getStateHolder().getState() == State.STARTED) {
                logger.log(Level.SEVERE, "doSelect IOException", e);
                controller.notifyException(e);
            } else {
                logger.log(Level.FINE, "doSelect IOException", e);
            }

        } else {
            try {
                if (key != null) {
                    selectorHandler.getSelectionKeyHandler().cancel(key);
                }
                controller.notifyException(e);
                logger.log(Level.SEVERE, "doSelect exception", e);
            } catch (Throwable t2) {
                // An unexpected exception occured, most probably caused by
                // a bad logger. Since logger can be externally configurable,
                // just output the exception on the screen and continue the
                // normal execution.
                t2.printStackTrace();
            }
        }
    }
    
    /**
     * handle Selector paused state
     */
    private void doSelectorPaused(State controllerState, State selectorHandlerState){
        CountDownLatch latch = new CountDownLatch(1);
        ConditionListener<State> controllerConditionListener =
                registerForNotification(controllerState,
                controller.getStateHolder(), latch);
        ConditionListener<State> selectorHandlerConditionListener =
                registerForNotification(selectorHandlerState,
                 selectorHandler.getStateHolder(), latch);

        try {
            latch.await(5000, TimeUnit.MILLISECONDS);
        } catch(InterruptedException e) {
        } finally {
            controller.getStateHolder().removeConditionListener(controllerConditionListener);
            selectorHandler.getStateHolder().removeConditionListener(selectorHandlerConditionListener);
        }
    }

    /**
     * Register <code>CountDownLatch</code> to be notified, when either Controller
     * or SelectorHandler will change their state to correspondent values
     * @param currentState initial/current {@link StateHolder} state
     * @param stateHolder
     * @param latch
     * @return <code>ConditionListener</code> if listener is registered, null if
     *        condition is true right now
     */
    private ConditionListener<State> registerForNotification(State currentState, 
            StateHolder<State> stateHolder, CountDownLatch latch) {
        if (currentState == State.PAUSED) {
            return stateHolder.notifyWhenStateIsNotEqual(State.PAUSED, latch);
        } else {
            return stateHolder.notifyWhenStateIsEqual(State.STOPPED, latch);
        }
    }

    protected static void switchToNewSelector(SelectorHandler selectorHandler)
            throws IOException {
        Selector oldSelector = selectorHandler.getSelector();
        Selector newSelector = Utils.openSelector();

        Set<SelectionKey> keys = oldSelector.keys();
        for (SelectionKey key : keys) {
            try {
                key.channel().register(newSelector, key.interestOps(), key.attachment());
            } catch (Exception e) {
                logger.log(Level.FINE, "Error switching channel to a new selector", e);
            }
        }

        selectorHandler.setSelector(newSelector);

        try {
            oldSelector.close();
        } catch (Exception e) {
        }
    }
}
