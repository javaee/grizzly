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

package com.sun.grizzly;

import com.sun.grizzly.async.AsyncQueueReader;
import com.sun.grizzly.async.AsyncQueueWritable;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.Copyable;
import java.nio.channels.SelectionKey;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * This interface is used to share information between the Grizzly Framework
 * classes and {@link ProtocolFilter} implementation. Since {@link Context}
 * is a pooled resource 
 * {@link Controller#pollContext(java.nio.channels.SelectionKey)} 
 * transactions using {@link Context} outside its {@link ProtocolChain}
 * must invoke {@link #incrementRefCount()} and
 * {@link Controller#returnContext(com.sun.grizzly.Context)} to keep its pooling
 * intact.
 * @author Jeanfrancois Arcand
 */
public interface Context extends AttributeHolder, Copyable {
     /**
     * Constant 'throwable' String
     */
    public final static String THROWABLE = "throwable";
    
    
    public enum AttributeScope {

        REQUEST,
        CONNECTION,
        SELECTOR,
        CONTROLLER
    }

    /**
     * A {@link SelectionKey}'s registration state.
     */
    public enum KeyRegistrationState {

        /** A cancelled {@link SelectionKey} registration state. */
        CANCEL,
        /** A registered {@link SelectionKey} registration state. */
        REGISTER,
        /** A {@link SelectionKey} with no registration state. */
        NONE
    }

    /**
     * The list of possible {@link SelectionKey}.OP_XXXX.
     */
    public enum OpType {

        OP_READ,
        OP_WRITE,
        OP_CONNECT,
        OP_READ_WRITE,
        OP_ACCEPT
    }
    
    
    /**
     * Return the current {@link IOEvent} associated with this
     * instance.
     * @return IOEvent the current {@link IOEvent} associated with this
     * instance.
     */
    public IOEvent getIOEvent();
    
    
    /**
     * Get the current {@link SelectionKey} interest ops this instance is executing.
     * @return OpType the currentOpType.
     */
    public OpType getCurrentOpType();
    
    
    /**
     * Return the current {@link Controller}.
     * @return - this Context's current {@link Controller}
     */
    public Controller getController();   
    
    
    /**
     * Execute this Context using the Controller's thread pool
     * @deprecated
     */
    public void execute();

    
    /**
     * Execute this Context using the Controller's thread pool
     * @param contextTask {@link ContextTask}, which will be 
     *                    executed by {@link ExecutorService}
     */
    public void execute(ContextTask contextTask);

    
    /**
     * Execute this Context using either Controller's thread pool
     * or current thread
     * @param contextTask {@link ContextTask}, which will be 
     *                    executed by {@link ExecutorService}
     * @param runInSeparateThread if true - {@link ContextTask} will
     *      be executed in separate thread, false - in current thread.
     */
    @SuppressWarnings("unchecked")
    public void execute(ContextTask contextTask, boolean runInSeparateThread);
    
    
    /**
     * Recycle this instance. 
     */
    public void recycle();
    
    
    /**
     * Return the current {@link SelectionKey} or null if 
     * {@link SelectionKey aren't supported.
     * @return - this Context's SelectionKey
     */
    public SelectionKey getSelectionKey();    
    
    
    /**
     * Return the current {@link Controller#Protocol} this instance is executing.
     * @return the current Controller.Protocol this instance is executing.
     */
    public Controller.Protocol getProtocol();
    
    
    /**
     * Cancel a {@link #suspend}ed {@link Context}. Invoking this method will
     * automatically clean the state of this Context and mark it as a candidate
     * for being re-used by another Thread and connection. 
     * 
     * <strong>Important. When cancelled, all operations done on this
     * object are not thread-safe and there is probability that another
     * thread is already using this object. Never use this object once cancelled.</strong>
     * 
     * 
     * When invoked this method will automatically close the underlying 
     * connection (represented by its {@link SelectionKey}.
     * 
     * If the Context hasn't been suspended, calling that method has no effet.
     */
    public void cancel();    
    
    
    /**
     * Return {@link ProtocolChain} executed by this instance.
     * @return {@link ProtocolChain} instance
     */
    public ProtocolChain getProtocolChain();

    
    /**
     * Set an optional CallbackHandler.
     * @param ioEvent  the {@link IOEvent} to set
     */
    public void setIOEvent(IOEvent<Context> ioEvent);

     
    /**
     * Return {@link SelectionKey}'s next registration state, or null
     * if not supported.
     * @return this Context's SelectionKey registration state
     */
    public KeyRegistrationState getKeyRegistrationState();
       
    
    /**
     * Return the current {@link SelectorHandler} this instance is executing, or
     * null if not supported.
     * @return the current {@link SelectorHandler} this instance is executing.
     */
    public SelectorHandler getSelectorHandler();    
    
    
    /**
     * Remove a key/value object.
     * @param key - name of an attribute
     * @return  attribute which has been removed
     */
    public Object removeAttribute(String key);
    
    
    /**
     * Set a {@link Map} of attribute name/value pairs.
     * Old {@link AttributeHolder} values will not be available.
     * Later changes of this {@link Map} will lead to changes to the current
     * {@link AttributeHolder}.
     * 
     * @param attributes - map of name/value pairs
     */
    public void setAttributes(Map<String, Object> attributes);

    
    /**
     * Return a {@link Map} of attribute name/value pairs.
     * Updates, performed on the returned {@link Map} will be reflected in
     * this {@link AttributeHolder}
     * 
     * @return - {@link Map} of attribute name/value pairs
     */
    public Map<String, Object> getAttributes();
    
    
    /**
     * Set the {@link SelectionKey}'s next registration state
     * @param {@link keyRegistrationState} - set this Context's SelectionKey
     *        registration state
     */
    public void setKeyRegistrationState(KeyRegistrationState keyRegistrationState); 
    
    
    /**
     * Returns {@link AsyncQueueWritable}, assciated with the current
     * {@link Context}. This method is not threadsafe.
     * 
     * @return {@link AsyncQueueWritable}
     */
    public AsyncQueueWritable getAsyncQueueWritable();

    
    /**
     * Return the {@linkAsyncQueueReader}
     * @return the {@linkAsyncQueueReader}
     */
    public AsyncQueueReader getAsyncQueueReader();
    
    
    /**
     * Return {@link AttributeHolder}, which corresponds to the 
     * given {@link AttributeScope}>
     * 
     * @param scope - {@link AttributeScope}>
     * @return - {@link AttributeHolder} instance, which contains
     *           {@link AttributeScope}> attributes
     */
    public AttributeHolder getAttributeHolderByScope(AttributeScope scope);
    
    
    /**
     * Return the {@link ExecutorService} executing this instance.
     * @return {@link ExecutorService}
     */
    public ExecutorService getThreadPool();

    
    /**
     * Set the {@link ExecutorService} that will execute this instance.
     * @param thread pool  the {@link ExecutorService} to set
     */
    public void setThreadPool(ExecutorService threadPool);
    
    
    /**
     * Set the {@link ProtocolChain} used by this {@link Context}.
     * @param protocolChain instance of {@link ProtocolChain} to be used by the Context
     */
    public void setProtocolChain(ProtocolChain protocolChain);

    
    /**
     * Suspend the execution of this {@link Context}. Suspending the execution
     * allow application to store the current instance, and re-use it later
     * by not only the  Thread used when called suspend, but also from any other Thread.
     * A suspended Context will not be re-used by any other transaction and Thread. 
     * A suspended Context will keep its current state intact, meaning its
     * SelectionKey, attributes, SelectorHandler, etc, will not change. Internally,
     * The Context will not be recyled and will not be re-used by any Thread.
     * 
     * When invoked this method will automatically set the 
     * {@link Context#setKeyRegistrationState} to {@link KeyRegistrationState}
     * to KeyRegistrationState.NONE.
     * 
     * Invoking this method many times as not effect once suspended. 
     */
    public void suspend();

    
    /**
     * Return <tt>true</tt> if this Context has been suspended by
     * invoking {@link suspend}. When suspended, invoking {@link Context#recycle}
     * will throw an {@link IllegalStateException}
     * @return <tt>true</tt> if this Context has been suspended
     */
    public boolean isSuspended();

    
    /**
     * Resume a {@link #suspend}ed {@link Context}. 
     *  <strong>Resume will not call {@link Context#recycle}</strong>. So
     * after the caller is finished using Context caller must
     * call {@link  Controller#returnContext(com.sun.grizzly.Context)}
     * to  mark it as a candidate  for being re-used by another Thread and connection.
     * 
     * <strong>Important. When resumed, all operations done on this
     * object are not thread-safe and there is probability that another
     * thread is already using this object. Never use this object once resumed.</strong>
     * 
     * When invoked this method will automatically set the 
     * {@link Context#setKeyRegistrationState} to {@link KeyRegistrationState}
     * to KeyRegistrationState.REGISTER and automatically re-enable read and 
     * write operations. 
     * 
     * If the Context hasn't been suspended, calling that method has no effet.
     */
    public void resume();

    
    /**
     * Called by outer Threads that are not instances of {@link WorkerThread} to
     * indicate that this {@link Context} should not be
     * {@link #recycle()} or offered back to its pool.
     * 
     * When a outer Thread is done with {@link Context} it must call
     * {@link Controller#returnContext(com.sun.grizzly.Context) to
     * ensure that {@link Context} will be properly recycled.
     * 
     * @return Current Thread reference count
     */
    public void incrementRefCount();

    
    /**
     * Decrements the reference count of this {@link Context}.
     * Threads wanting to release {@link Context} should not call
     * this method but instead use 
     * {@link Controller#returnContext(com.sun.grizzly.Context)}
     * @return return decremented reference count
     */
    public int decrementRefCount();
}
