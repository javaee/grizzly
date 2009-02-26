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

package org.glassfish.grizzly.nio;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Transport.State;
import org.glassfish.grizzly.util.ExceptionHandler.Severity;
import org.glassfish.grizzly.util.StateHolder;
import org.glassfish.grizzly.util.conditions.ConditionListener;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.util.LinkedTransferQueue;

/**
 * Class is responsible for processing certain (single) {@link SelectorHandler}
 * 
 * @author Alexey Stashok
 */
public class SelectorRunner implements Runnable {
    private final static Logger logger = Grizzly.logger;

    private final NIOTransport transport;
    private final StateHolder<State> stateHolder;
    
    private LinkedTransferQueue pendingOperations;
    private Selector selector;
    private Thread selectorRunnerThread;
    
    public SelectorRunner(NIOTransport transport) {
        this(transport, null);
    }

    public SelectorRunner(NIOTransport transport, Selector selector) {
        this.transport = transport;
        this.selector = selector;
        stateHolder = new StateHolder<State>(false, State.STOP);
    }

    public NIOTransport getTransport() {
        return transport;
    }

    public Selector getSelector() {
        return selector;
    }

    public void setSelector(Selector selector) {
        this.selector = selector;
    }

    public Thread getRunnerThread() {
        return selectorRunnerThread;
    }

    public StateHolder<State> getStateHolder() {
        return stateHolder;
    }

    public State getState() {
        return stateHolder.getState();
    }
    
    public void start() {
        if (stateHolder.getState() != State.STOP) {
            Grizzly.logger.log(Level.WARNING,
                    "SelectorRunner is not in the stopped state!");
        }
        
        pendingOperations = new LinkedTransferQueue();
        
        stateHolder.setState(State.STARTING);
        transport.getInternalThreadPool().execute(this);
    }
    
    public void startBlocking(int timeout) throws TimeoutException {
        start();
        waitUntilStateNotEqual(stateHolder, State.START, timeout);
    }

    public void stop() {
        stateHolder.setState(State.STOPPING);
        
        if (selector != null) {
            selector.wakeup();
        }
    }
        
    public void stopBlocking(int timeout) throws TimeoutException {
        stop();
        waitUntilStateNotEqual(stateHolder, State.STOP, timeout);
    }
    
    public void run() {
        selectorRunnerThread = Thread.currentThread();
        selectorRunnerThread.setName(selectorRunnerThread.getName() +
                " SelectorRunner");

        stateHolder.setState(State.START);
        StateHolder<State> transportStateHolder = transport.getState();
        
        try {
            while (!isStop(false)) {
                if (transportStateHolder.getState(false) != State.PAUSE) {
                    doSelect();
                } else {
                    try {
                        waitUntilStateEqual(transportStateHolder, State.PAUSE,
                                5000);
                    } catch (TimeoutException e) {
                    }
                }
            }
        } finally {
            stateHolder.setState(State.STOP);
            selectorRunnerThread = null;
        }
    }
    
    /**
     * This method handle the processing of all Selector's interest op
     * (OP_ACCEPT,OP_READ,OP_WRITE,OP_CONNECT) by delegating to its Handler.
     * By default, all java.nio.channels.Selector operations are implemented
     * using SelectorHandler. All SelectionKey operations are implemented by
     * SelectionKeyHandler. Finally, ProtocolChain creation/re-use are implemented
     * by InstanceHandler.
     * @param selectorHandler - the <code>SelectorHandler</code>
     */
    protected void doSelect() {
        Collection<IOEvent> ioEventsChain = new ArrayList<IOEvent>();
        
        SelectorHandler selectorHandler = transport.getSelectorHandler();
        SelectionKeyHandler selectionKeyHandler =
                transport.getSelectionKeyHandler();
        
        SelectionKey key = null;
        Set<SelectionKey> readyKeys;
        Iterator<SelectionKey> iterator;
        int selectorState;
        
        try {
            selectorState = 0;
            
            selectorHandler.preSelect(this);
            
            readyKeys = selectorHandler.select(this);
            selectorState = readyKeys.size();
            
            if (selectorState != 0) {
                iterator = readyKeys.iterator();
                while (iterator.hasNext()) {
                    try {
                        key = iterator.next();
                        iterator.remove();
                        if (key.isValid()) {
                            Collection<IOEvent> execIoEventsChain =
                                    selectionKeyHandler.onKeyEvent(key,
                                    ioEventsChain);
                            Connection connection =
                                    selectionKeyHandler.getConnectionForKey(key);
                            
                            if (execIoEventsChain != null) {
                                for(IOEvent ioEvent : execIoEventsChain) {
                                    transport.fireIOEvent(ioEvent, connection);
                                }
                            }
                        } else {
                            selectionKeyHandler.cancel(key);
                        }
                    } catch (IOException e) {
                        notifyConnectionException(key, 
                                "Unexpected IOException. Channel " + 
                                    key.channel() + " will be closed.", e, 
                                    Severity.CONNECTION, Level.WARNING,
                                    Level.FINE);
                    } catch (CancelledKeyException e) {
                        notifyConnectionException(key, 
                                "Unexpected CancelledKeyException. Channel " + 
                                    key.channel() + " will be closed.", e, 
                                    Severity.CONNECTION, Level.WARNING,
                                    Level.FINE);
                    } finally {
                        ioEventsChain.clear();
                    }
                }
            }
            
            selectorHandler.postSelect(this);
        } catch (ClosedSelectorException e) {
            notifyConnectionException(key,
                    "Selector was unexpectedly closed", e,
                    Severity.TRANSPORT, Level.SEVERE, Level.FINE);
        } catch (Exception e) {
            notifyConnectionException(key,
                    "doSelect exception", e,
                    Severity.UNKNOWN, Level.SEVERE, Level.FINE);
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"doSelect exception", t);
            transport.notifyException(Severity.FATAL, t);
        }
    }

    public LinkedTransferQueue getPendingOperations() {
        return pendingOperations;
    }

    private boolean isStop(boolean isBlockingCompare) {
        State state = stateHolder.getState(isBlockingCompare);

        if (state == State.STOP
                    || state == State.STOPPING) return true;
        
        state = transport.getState().getState(isBlockingCompare);
        
        return state == State.STOP
                    || state == State.STOPPING; 
    }
    
    private boolean isRunning(boolean isBlockingCompare) {
        return stateHolder.getState(isBlockingCompare) == State.START &&
                transport.getState().getState(isBlockingCompare) == State.START;
    }
    
    private static void waitUntilStateNotEqual(StateHolder<State> stateHolder, 
            State patternState, int timeout) throws TimeoutException {

        Object locker = new Object();
        ConditionListener<State, Object> listener =
                stateHolder.notifyWhenStateIsEqual(patternState, locker);

        if (listener != null) {
            synchronized (locker) {
                try {
                    if (stateHolder.getState(false) != patternState) {
                        locker.wait(timeout);
                    }
                } catch (InterruptedException e) {
                } finally {
                    stateHolder.removeConditionListener(listener);
                }
            }
        }

        if (stateHolder.getState(false) != patternState) {
            throw new TimeoutException();
        }
    }
    
    private static void waitUntilStateEqual(StateHolder<State> stateHolder, 
            State patternState, int timeout) throws TimeoutException {

        Object locker = new Object();
        ConditionListener<State, Object> listener =
                stateHolder.notifyWhenStateIsNotEqual(patternState, locker);

        if (listener != null) {
            synchronized (locker) {
                try {
                    if (stateHolder.getState(false) == patternState) {
                        locker.wait(timeout);
                    }
                } catch (InterruptedException e) {
                } finally {
                    stateHolder.removeConditionListener(listener);
                }
            }
        }

        if (stateHolder.getState(false) == patternState) {
            throw new TimeoutException();
        }
    }
    
    /**
     * Notify transport about the {@link Exception} happened, log it and cancel
     * the {@link SelectionKey}, if it is not null
     * 
     * @param key {@link SelectionKey}, which was processed, when the {@link Exception} occured
     * @param description error description
     * @param e {@link Exception} occured
     * @param severity {@link Severity} of occured problem
     * @param runLogLevel logger {@link Level} to use, if transport is in running state
     * @param stoppedLogLevel logger {@link Level} to use, if transport is not in running state
     */
    private void notifyConnectionException(SelectionKey key, String description, 
            Exception e, Severity severity, Level runLogLevel,
            Level stoppedLogLevel) {
        if (isRunning(false)) {
            logger.log(runLogLevel, description, e);

            if (key != null) {
                try {
                    transport.getSelectionKeyHandler().cancel(key);
                } catch (IOException cancelException) {
                    logger.log(Level.FINE, "IOException during cancelling key",
                            cancelException);
                }
            }

            transport.notifyException(severity, e);
        } else {
            logger.log(stoppedLogLevel, description, e);
        }
    }
}
