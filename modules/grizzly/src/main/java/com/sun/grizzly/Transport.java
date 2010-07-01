/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.utils.ExceptionHandler;
import com.sun.grizzly.utils.StateHolder;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * Transport interface describes the transport unit used in Grizzly.
 *
 * Transport implementation could operate over TCP, UDP or other custom
 * protocol, using blocking, NIO or NIO.2 Java API.
 *
 * @author Alexey Stashok
 */
public interface Transport extends ExceptionHandler {
    public enum State {STARTING, START, PAUSE, STOPPING, STOP};

    public enum IOEventReg {REGISTER, DEREGISTER};

    /**
     * Gets the {@link Transport} name.
     * 
     * @return the {@link Transport} name.
     */
    public String getName();

    /**
     * Sets the {@link Transport} name.
     *
     * @param name the {@link Transport} name.
     */
    public void setName(String name);

    /**
     * Return the {@link Transport} state controller. Using the state controller,
     * it is possible to get/set the {@link Transport} state in thread-safe manner.
     * 
     * @return {@link StateHolder} state controller.
     */
    public StateHolder<State> getState();

    /**
     * Returns the {@link Transport} mode.
     * <tt>true</tt>, if {@link Transport} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     * Specific {@link Transport} {@link Connection}s may override this setting
     * by {@link Connection#isBlocking()}.
     * 
     * @return the {@link Transport} mode.
     * <tt>true</tt>, if {@link Transport} is operating in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    public boolean isBlocking();

    /**
     * Sets the {@link Transport} mode.
     * Specific {@link Transport} {@link Connection}s may override this setting
     * by {@link Connection#configureBlocking(boolean)}.
     *
     * @param isBlocking the {@link Transport} mode. <tt>true</tt>,
     * if {@link Transport} should operate in blocking mode, or
     * <tt>false</tt> otherwise.
     */
    public void configureBlocking(boolean isBlocking);

    public void configureStandalone(boolean isStandalone);

    public boolean isStandalone();

    /**
     * Gets the default {@link Processor}, which will process <tt>Transport</tt>
     * {@link Connection}s I/O events in case, if {@link Connection}
     * doesn't have own {@link Processor} preferences.
     * If {@link Transport} associated {@link Processor} is <tt>null</tt>,
     * and {@link Connection} doesn't have any preferred {@link Processor} -
     * then {@link Transport} will try to get {@link Processor} using
     * {@link ProcessorSelector#select(IOEvent, Connection)}.
     *
     * @return the default {@link Processor}, which will process
     * {@link Connection} I/O events, if one doesn't have
     * own {@link Processor} preferences.
     */
    public Processor obtainProcessor(IOEvent ioEvent, Connection connection);

    /**
     * Gets the default {@link Processor}, which will process {@link Connection}
     * I/O events in case, if {@link Connection} doesn't have own
     * {@link Processor} preferences.
     * If {@link Transport} associated {@link Processor} is <tt>null</tt>,
     * and {@link Connection} doesn't have any preferred {@link Processor} -
     * then {@link Transport} will try to get {@link Processor} using
     * {@link ProcessorSelector#select(IOEvent, Connection)}.
     * 
     * @return the default {@link Processor}, which will process
     * {@link Connection} I/O events, if one doesn't have
     * own {@link Processor} preferences.
     */
    public Processor getProcessor();

    /**
     * Sets the default {@link Processor}, which will process {@link Connection}
     * I/O events in case, if {@link Connection} doesn't have own
     * {@link Processor} preferences.
     *
     * @param processor the default {@link Processor}, which will process
     * {@link Connection} I/O events, if one doesn't have own
     * {@link Processor} preferences.
     */
    public void setProcessor(Processor processor);

    /**
     * Gets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Transport}'s {@link Processor} is <tt>null</tt> and
     * {@link Connection} doesn't have neither preferred {@link Processor}
     * nor {@link ProcessorSelector}.
     *
     * {@link Transport}'s {@link ProcessorSelector} is the last place, where
     * {@link Transport} will try to get {@link Processor} to process
     * {@link Connection} I/O event. If {@link ProcessorSelector} is not set -
     * {@link IllegalStateException} will be thrown.
     * 
     * @return the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Transport}'s {@link Processor} is <tt>null</tt> and
     * {@link Connection} doesn't have neither preferred {@link Processor}
     * nor {@link ProcessorSelector}.
     */
    public ProcessorSelector getProcessorSelector();

    /**
     * Sets the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Transport}'s {@link Processor} is <tt>null</tt> and
     * {@link Connection} doesn't have neither preferred {@link Processor}
     * nor {@link ProcessorSelector}.
     *
     * {@link Transport}'s {@link ProcessorSelector} is the last place, where
     * {@link Transport} will try to get {@link Processor} to process
     * {@link Connection} I/O event. If {@link ProcessorSelector} is not set -
     * {@link IllegalStateException} will be thrown.
     *
     * @param the default {@link ProcessorSelector}, which will be used to get
     * {@link Processor} to process {@link Connection} I/O events, in case if
     * this {@link Transport}'s {@link Processor} is <tt>null</tt> and
     * {@link Connection} doesn't have neither preferred {@link Processor}
     * nor {@link ProcessorSelector}.
     */
    public void setProcessorSelector(ProcessorSelector selector);

    /**
     * Get the {@link Transport} associated {@link MemoryManager}, which will
     * be used by the {@link Transport}, its {@link Connection}s and by during
     * processing I/O events, occurred on {@link Connection}s.
     * 
     * @return the {@link Transport} associated {@link MemoryManager},
     * which will be used by the {@link Transport}, its {@link Connection}s
     * and by during processing I/O events, occurred on {@link Connection}s.
     */
    public MemoryManager getMemoryManager();

    /**
     * Set the {@link Transport} associated {@link MemoryManager}, which will
     * be used by the {@link Transport}, its {@link Connection}s and by during
     * processing I/O events, occurred on {@link Connection}s.
     *
     * @param memoryManager the {@link Transport} associated
     * {@link MemoryManager}, which will be used by the {@link Transport},
     * its {@link Connection}s and by during processing I/O events, occurred
     * on {@link Connection}s.
     */
    public void setMemoryManager(MemoryManager memoryManager);

    /**
     * Get the {@link Strategy} implementation, which will be used by
     * {@link Transport} to process {@link IOEvent}.
     * {@link Strategy} is responsible for choosing the way, how I/O event
     * will be processed: using current {@link Thread}, worker {@link Thread};
     * or make any other decisions.
     * 
     * @return the {@link Strategy} implementation, which will be used by
     * {@link Transport} to process {@link IOEvent}.
     */
    public Strategy getStrategy();

    /**
     * Set the {@link Strategy} implementation, which will be used by
     * {@link Transport} to process {@link IOEvent}.
     * {@link Strategy} is responsible for choosing the way, how I/O event
     * will be processed: using current {@link Thread}, worker {@link Thread};
     * or make any other decisions.
     *
     * @param strategy the {@link Strategy} implementation, which will be used
     * by {@link Transport} to process {@link IOEvent}.
     */
    public void setStrategy(Strategy strategy);

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Transport}'s {@link Connection}s.
     * For particullar {@link Connection}, this setting could be overriden by
     * {@link Connection#getReadBufferSize()}.
     * 
     * @return the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Transport}'s {@link Connection}s.
     */
    public int getReadBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * reading data from {@link Transport}'s {@link Connection}s.
     * For particullar {@link Connection}, this setting could be overriden by
     * {@link Connection#setReadBufferSize(int)}.
     *
     * @param readBufferSize the default size of {@link Buffer}s, which will
     * be allocated for reading data from {@link Transport}'s
     * {@link Connection}s.
     */
    public void setReadBufferSize(int readBufferSize);

    /**
     * Get the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Transport}'s {@link Connection}s.
     * For particullar {@link Connection}, this setting could be overriden by
     * {@link Connection#getWriteBufferSize()}.
     *
     * @return the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Transport}'s {@link Connection}s.
     */
    public int getWriteBufferSize();

    /**
     * Set the default size of {@link Buffer}s, which will be allocated for
     * writing data to {@link Transport}'s {@link Connection}s.
     * For particullar {@link Connection}, this setting could be overriden by
     * {@link Connection#setWriteBufferSize(int)}.
     *
     * @param writeBufferSize the default size of {@link Buffer}s, which will
     * be allocated for writing data to {@link Transport}'s
     * {@link Connection}s.
     */
    public void setWriteBufferSize(int writeBufferSize);

    /**
     * Get a thread pool, which will process transport internal tasks like
     * NIO {@link Selector} polling etc.
     *
     * @return {@link ExecutorService} transport thread pool.
     */
    public ExecutorService getThreadPool();

    /**
     * Set a thread pool, which will process transport internal tasks like
     * NIO {@link Selector} polling etc.
     *
     * @param threadPool {@link ExecutorService} transport thread pool.
     */
    public void setThreadPool(ExecutorService threadPool);

    /**
     * Get {@link Transport} associated {@link AttributeBuilder}, which will
     * be used by {@link Transport} and its {@link Connection}s to store custom
     * {@link Attribute}s.
     * 
     * @return {@link Transport} associated {@link AttributeBuilder}, which will
     * be used by {@link Transport} and its {@link Connection}s to store custom
     * {@link Attribute}s.
     */
    public AttributeBuilder getAttributeBuilder();

    /**
     * Set {@link Transport} associated {@link AttributeBuilder}, which will
     * be used by {@link Transport} and its {@link Connection}s to store custom
     * {@link Attribute}s.
     *
     * @param attributeBuilder {@link Transport} associated
     * {@link AttributeBuilder}, which will be used by {@link Transport} and
     * its {@link Connection}s to store custom {@link Attribute}s.
     */
    public void setAttributeBuilder(AttributeBuilder attributeBuilder);
    
    /**
     * Add {@link ExceptionHandler} to handle errors, occurred during the
     * {@link Transport} execution.
     * 
     * @param handler {@link ExceptionHandler} to be added.
     */
    public void addExceptionHandler(ExceptionHandler handler);
    
    /**
     * Remove {@link ExceptionHandler} from the list of {@link Transport}
     * {@link ExceptionHandler}s.
     *
     * @param handler {@link ExceptionHandler} to be removed.
     */
    public void removeExceptionHandler(ExceptionHandler handler);

    /**
     * Notify about error, occurred during {@link Transport} execution.
     * 
     * @param severity the error severity.
     * @param throwable the error description.
     */
    @Override
    public void notifyException(Severity severity, Throwable throwable);
    
    /**
     * Starts the transport
     * 
     * @throws java.io.IOException
     */
    public void start() throws IOException;
    
    /**
     * Stops the transport and closes all the connections
     * 
     * @throws java.io.IOException
     */
    public void stop() throws IOException;
    
    /**
     * Pauses the transport
     * 
     * @throws java.io.IOException
     */
    public void pause() throws IOException;
    
    /**
     * Resumes the transport after a pause
     * 
     * @throws java.io.IOException
     */
    public void resume() throws IOException;
    
    /**
     * Fires specific {@link IOEvent} on the {@link Connection}
     *
     * @param ioEvent I/O event
     * @param connection {@link Connection}, on which we fire the event.
     */
    public IOEventReg fireIOEvent(IOEvent ioEvent, Connection connection,
            PostProcessor postProcessor) throws IOException;

//    /**
//     * Fires the {@link IOEvent} on the {@link Connection} according to the
//     * passed {@link Context}
//     *
//     * @param context I/O event processing context
//     */
//    public IOEventReg fireIOEvent(Context context) throws IOException;

    /**
     * Returns <tt>true</tt>, if this <tt>Transport</tt> is in stopped state,
     *         <tt>false</tt> otherwise.
     * @return <tt>true</tt>, if this <tt>Transport</tt> is in stopped state,
     *         <tt>false</tt> otherwise.
     */
    public boolean isStopped();

    public boolean isPaused();

    public Reader getReader(Connection connection);

    public Writer getWriter(Connection connection);
}
