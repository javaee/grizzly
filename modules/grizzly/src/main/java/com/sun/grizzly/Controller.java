/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.Cloner;
import com.sun.grizzly.util.ConcurrentLinkedQueuePool;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.Grizzly;
import com.sun.grizzly.util.GrizzlyExecutorService;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.State;
import com.sun.grizzly.util.StateHolder;
import com.sun.grizzly.util.SupportStateHolder;
import com.sun.grizzly.util.ThreadPoolConfig;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.WorkerThreadFactory;
import com.sun.grizzly.util.WorkerThreadImpl;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.sun.grizzly.Context.OpType;

/**
 * <p>
 * Main entry point when using the Grizzly Framework. A Controller is composed
 * of Handlers, ProtocolChain and ExecutorService. All of those components are
 * configurable by client using the Grizzly Framework.
 * </p>
 *
 * <p>
 * A ProtocolChain implement the "Chain of Responsibility" pattern (for more info,
 * take a look at the classic "Gang of Four" design patterns book). Towards
 * that end, the Chain API models a computation as a series of "protocol filter"
 * that can be combined into a "protocol chain".
 * </p>
 * <p>
 * An Handler is a interface that can be implemented
 * by implemented by client of the Grizzly Framework to used to help handling
 * NIO operations. The Grizzly Framework define three Handlers:
 * </p>
 * <p><pre><code>
 * (1) SelectorHandler: A SelectorHandler handles all java.nio.channels.Selector
 *                     operations. One or more instance of a Selector are
 *                     handled by SelectorHandler. The logic for processing of
 *                     SelectionKey interest (OP_ACCEPT,OP_READ, etc.) is usually
 *                     defined using an instance of SelectorHandler.
 * (2) SelectionKeyHandler: A SelectionKeyHandler is used to handle the life
 *                          life cycle of a SelectionKey. Operations like canceling,
 *                          registering or closing are handled by SelectionKeyHandler.
 * (3) ProtocolChainInstanceHandler: An ProtocolChainInstanceHandler is where one or several ProtocolChain
 *                      are created and cached. An ProtocolChainInstanceHandler decide if
 *                      a stateless or statefull ProtocolChain needs to be created.
 * </code></pre></p>
 * <p>
 * By default, the Grizzly Framework bundles implementation for TCP
 * and UPD transport. The TCPSelectorHandler is instanciated by default. As an
 * example, supporting the HTTP protocol should only consist of adding the
 * appropriate ProtocolFilter like:
 * </p>
 * <p><pre><code>
 *       Controller sel = new Controller();
 *       sel.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler(){
 *           public ProtocolChain poll() {
 *               ProtocolChain protocolChain = protocolChains.poll();
 *               if (protocolChain == null){
 *                   protocolChain = new DefaultProtocolChain();
 *                   protocolChain.addFilter(new ReadFilter());
 *                   protocolChain.addFilter(new HTTPParserFilter());
 *               }
 *               return protocolChain;
 *           }
 *       });
 *
 * </code></pre></p>
 * <p>
 * In the example above, a pool of ProtocolChain will be created, and all instance
 * of ProtocolChain will have their instance of ProtocolFilter. Hence the above
 * implementation can be called statefull. A stateless implementation would
 * instead consist of sharing the ProtocolFilter among ProtocolChain:
 * </p>
 * <p><pre><code>
 *       final Controller sel = new Controller();
 *       final ReadFilter readFilter = new ReadFilter();
 *       final LogFilter logFilter = new LogFilter();
 *
 *       sel.setProtocolChainInstanceHandler(new DefaultProtocolChainInstanceHandler(){
 *           public ProtocolChain poll() {
 *               ProtocolChain protocolChain = protocolChains.poll();
 *               if (protocolChain == null){
 *                   protocolChain = new DefaultProtocolChain();
 *                   protocolChain.addFilter(readFilter);
 *                   protocolChain.addFilter(logFilter);
 *               }
 *               return protocolChain;
 *           }
 *       });
 * </code></pre></p>
 *
 * The Controller can be configure at runtime using System properties. For more
 * information, take a look at {@link ControllerConfig} class.
 *
 * @author Jeanfrancois Arcand
 */
public class Controller implements Runnable, Lifecycle, Copyable,
        ConnectorHandlerPool, AttributeHolder, SupportStateHolder<State> {

    private int maxAcceptRetries = 5;

    private boolean displayConfiguration = false;

    public enum Protocol {
        UDP, TCP, TLS, CUSTOM
    }
    /**
     * Required number of threads created by a {@link DefaultThreadPool}
     */
    private volatile int requiredThreadsCount = DefaultThreadPool.DEFAULT_MIN_THREAD_COUNT;
    /**
     * A cached list of Context. Context are by default stateless.
     */
    private ConcurrentLinkedQueuePool<NIOContext> contexts;
    /**
     * The ProtocolChainInstanceHandler used by this instance. If not set, and instance
     * of the DefaultInstanceHandler will be created.
     */
    protected ProtocolChainInstanceHandler instanceHandler;
    /**
     * The SelectionKey Handler used by this instance. If not set, and instance
     * of the DefaultSelectionKeyHandler will be created.
     */
    protected SelectionKeyHandler selectionKeyHandler;
    /**
     * The SelectorHandler, which will manage connection accept,
     * if readThreadsCount > 0 and spread connection processing between
     * different read threads
     */
    protected ComplexSelectorHandler multiReadThreadSelectorHandler = null;
    /**
     * The ConnectorHandlerPool, which is responsible for creating/caching
     * ConnectorHandler instances.
     */
    protected ConnectorHandlerPool connectorHandlerPool = null;
    /**
     * The set of {@link SelectorHandler}s used by this instance. If not set, the instance
     * of the TCPSelectorHandler will be added by default.
     */
    protected final Queue<SelectorHandler> selectorHandlers =
            DataStructures.getCLQinstance(SelectorHandler.class);
    /**
     * Current {@link Controller} state
     */
    protected StateHolder<State> stateHolder;
    /**
     * The number of read threads
     */
    protected int readThreadsCount = -1;
    /**
     * The array of {@link Controller}s to be used for reading
     */
    protected ReadController[] readThreadControllers;
    /**
     * Default Logger.
     */
    protected static Logger logger = Logger.getLogger("grizzly");
    /**
     * Default Thread Pool (called ExecutorService).If not set, and instance
     * of the DefaultThreadPool will be created.
     */
    protected ExecutorService threadPool;
    /**
     * Collection of {@link Controller} state listeners, which
     * will are notified on {@link Controller} state change.
     */
    protected final Collection<ControllerStateListener> stateListeners =
            DataStructures.getCLQinstance(ControllerStateListener.class);

    /**
     * Internal countdown counter of {@link SelectorHandler}s, which
     * are ready to process
     */
    protected AtomicInteger readySelectorHandlerCounter;
    /**
     * Internal countdown counter of {@link SelectorHandler}s, which stopped
     */
    protected AtomicInteger stoppedSelectorHandlerCounter;
    /**
     * <tt>true</tt> if OP_READ and OP_WRITE can be handled concurrently.
     */
    private boolean handleReadWriteConcurrently = true;
    /**
     * Attributes, associated with the {@link Controller} instance
     */
    protected Map<String, Object> attributes;

    /**
     * The existing Controller instances.
     */
    private final static WeakHashMap<Controller, Boolean> controllers =
            new WeakHashMap<Controller, Boolean>();
    /**
     * The {@link #controllers} map lock.
     */
    private final static ReadWriteLock controllersLock =
            new ReentrantReadWriteLock();

    // Internal Thread Pool.
    private ExecutorService kernelExecutor;
    
    /**
     * Enable workaround Linux spinning Selector
     */
    static final boolean isLinux =
            "linux".equalsIgnoreCase(System.getProperty("os.name")) &&
                !System.getProperty("java.version").startsWith("1.7");
    
    /**
     * Allow {@link Context} caching.
     */
    private boolean allowContextCaching = false;

    /**
     * Flag, which indicates if {@link SelectorHandlerRunner} should use
     * Leader/Follower strategy.
     */
    private boolean useLeaderFollowerStrategy = false;

    /**
     * Enable/Disable auto-config.
     */
    private boolean autoConfigure = false;

   /**
     * True if calling selector thread should execute the pendingIO event.
     */
    private boolean executePendingIOUsingSelectorThread = false;

    /**
     * Kernel ExecutorService factory
     */
    private KernelExecutorFactory kernelExecutorFactory =
            new KernelExecutorFactory.DefaultFactory();

    // -------------------------------------------------------------------- //
    /**
     * Controller constructor
     */
    public Controller() {
        new ControllerConfig().configure(this);

        contexts = new ConcurrentLinkedQueuePool<NIOContext>() {

            @Override
            public NIOContext newInstance() {
                return new NIOContext();
            }
        };

        stateHolder = new StateHolder<State>(true);
        initializeDefaults();
    }

    /**
     * This method initializes this Controller's default thread pool,
     * default ProtocolChainInstanceHandler, default SelectorHandler(s)
     * and default ConnectorHandlerPool.  These defaults can be overridden
     * after this Controller constructor is called and before calling
     * Controller.start() using this Controller's mutator methods to
     * set a different thread pool, ProtocolChainInstanceHandler,
     * SelectorHandler(s) or ConnectorHandlerPool.
     */
    private void initializeDefaults() {
        if (instanceHandler == null) {
            instanceHandler = new DefaultProtocolChainInstanceHandler();
        }
        if (connectorHandlerPool == null) {
            connectorHandlerPool = new DefaultConnectorHandlerPool(this);
        }
        kernelExecutor = createKernelExecutor();

        controllersLock.writeLock().lock();
        try {
            controllers.put(this, Boolean.TRUE);
        } finally {
            controllersLock.writeLock().unlock();
        }
    }

    private void ensureAppropriatePoolSize( ExecutorService threadPool ) {
        if( threadPool == null )
            return;
        if( threadPool instanceof GrizzlyExecutorService ) {
            final GrizzlyExecutorService grizzlyExecutorService =
                    (GrizzlyExecutorService) threadPool;
            final ThreadPoolConfig config = grizzlyExecutorService.getConfiguration();
            
            if( config.getCorePoolSize() < requiredThreadsCount ) {
                if( config.getMaxPoolSize() < requiredThreadsCount )
                    config.setMaxPoolSize( requiredThreadsCount );
                config.setCorePoolSize( requiredThreadsCount );
                grizzlyExecutorService.reconfigure(config);
            }
        } else if( threadPool instanceof ExtendedThreadPool ) {
            final ExtendedThreadPool extendedThreadPool = (ExtendedThreadPool)threadPool;
            if( extendedThreadPool.getCorePoolSize() < requiredThreadsCount ) {
                if( extendedThreadPool.getMaximumPoolSize() < requiredThreadsCount )
                    extendedThreadPool.setMaximumPoolSize( requiredThreadsCount );
                extendedThreadPool.setCorePoolSize( requiredThreadsCount );
            }
        } else if( threadPool instanceof ThreadPoolExecutor ) {
            final ThreadPoolExecutor jdkThreadPool = (ThreadPoolExecutor)threadPool;
            if( jdkThreadPool.getCorePoolSize() < requiredThreadsCount ) {
                if( jdkThreadPool.getMaximumPoolSize() < requiredThreadsCount )
                    jdkThreadPool.setMaximumPoolSize( requiredThreadsCount );
                jdkThreadPool.setCorePoolSize( requiredThreadsCount );
            }
        }
    }

    /**
     * Auto-configure the number of reader threads based on the core
     * processor.
     */
    private void autoConfigureCore(){
        if (autoConfigure && readThreadsCount == -1){
            readThreadsCount = Runtime.getRuntime().availableProcessors();
            if( readThreadsCount > 0 )
                recalcRequiredThreadsCount();
            if (logger.isLoggable(Level.FINE)){
                logger.fine("Controller auto-configured with 2 ReadController " +
                        "based on underlying cores/processors, with a Thread Pool " +
                        "of required size " + requiredThreadsCount );
            }
        }
    }


    /**
     * Register a SelectionKey.
     * @param key <tt>SelectionKey</tt> to register
     */
    public void registerKey(SelectionKey key) {
        registerKey(key, SelectionKey.OP_READ);
    }

    /**
     * Register a SelectionKey on the first SelectorHandler that was added
     * using the addSelectorHandler().
     * @param key <tt>SelectionKey</tt> to register
     * @param ops - the interest op to register
     */
    public void registerKey(SelectionKey key, int ops) {
        registerKey(key, ops, selectorHandlers.peek().protocol());
    }

    /**
     * Register a SelectionKey.
     * @param key <tt>SelectionKey</tt> to register
     * @param ops - the interest op to register
     * @param protocol specified protocol SelectorHandler key should be registered on
     */
    public void registerKey(SelectionKey key, int ops, Protocol protocol) {
        if (stateHolder.getState() == State.STOPPED) {
            return;
        }

        getSelectorHandler(protocol).register(key, ops);
    }

    /**
     * Cancel a SelectionKey
     * @param key <tt>SelectionKey</tt> to cancel
     * @deprecated
     */
    public void cancelKey(SelectionKey key) {
        if (stateHolder.getState() == State.STOPPED) {
            return;
        }

        SelectorHandler selectorHandler = getSelectorHandler(key.selector());
        if (selectorHandler != null) {
            selectorHandler.getSelectionKeyHandler().cancel(key);
        } else {
            throw new IllegalStateException("SelectionKey is not associated " +
                    "with known SelectorHandler");
        }
    }

    /**
     * Get an instance of a {@link NIOContext}
     * @return {@link Context}
     */
    public Context pollContext() {
        NIOContext ctx = null;
        try{
            if (!allowContextCaching) {
                final Thread thread = Thread.currentThread();
                if (thread instanceof WorkerThreadImpl) {
                    ctx = (NIOContext) ((WorkerThreadImpl) thread).getContext();
                    
                    if (ctx != null) {
                        ((WorkerThreadImpl) thread).setContext(null);
                    } else {
                       ctx = new NIOContext();
                    }
                } else {
                    ctx = new NIOContext();
                }
            } else {
                ctx = contexts.poll();
            }
        } finally {
            ctx.setController(this);
        }
        return ctx;
    }

    /**
     * Configure the {@link Context}
     * @param key {@link SelectionKey}
     * @param opType the current SelectionKey op.
     * @param ctx
     * @param selectorHandler
     */
    public void configureContext(SelectionKey key, OpType opType,
            NIOContext ctx, SelectorHandler selectorHandler) {
        
        ctx.setSelectorHandler(selectorHandler);
        ctx.setThreadPool(selectorHandler.getThreadPool());
        ctx.setAsyncQueueReader(selectorHandler.getAsyncQueueReader());
        ctx.setAsyncQueueWriter(selectorHandler.getAsyncQueueWriter());
        ctx.setSelectionKey(key);
        if (opType != null) {
            ctx.setCurrentOpType(opType);
        } else {
            if (key != null) {
                ctx.configureOpType(key);
            }
        }
    }

    /**
     * Return a {@link Context} to its pool if it is not shared. if 
     * {@link #allowContextCaching} is false, the instance is not cached.
     *
     * @param ctx - the {@link Context}
     */
    public void returnContext(Context ctx) {
        if (ctx.decrementRefCount() > 0) {
            return;
        }
        
        if (!allowContextCaching) {
            final Thread thread = Thread.currentThread();
            if (thread instanceof WorkerThreadImpl) {
                final WorkerThreadImpl wti = (WorkerThreadImpl) thread;
                if (wti.getContext() == null) {
                    ctx.recycle();
                    wti.setContext(ctx);
                }
            }

            return;
        }

        try {
            ctx.recycle();
        } finally {
            contexts.offer((NIOContext) ctx);
        }
    }

    /**
     * Return the current <code>Logger</code> used by this Controller.
     */
    public static Logger logger() {
        return logger;
    }

    /**
     * Set the Logger single instance to use.
     */
    public static void setLogger(Logger l) {
        logger = l;
        LoggerUtils.setLogger(l);
    }

    // ------------------------------------------------------ Handlers ------//
    /**
     * Set the {@link ProtocolChainInstanceHandler} to use for
     * creating instance of {@link ProtocolChain}.
     */
    public void setProtocolChainInstanceHandler(ProtocolChainInstanceHandler instanceHandler) {
        this.instanceHandler = instanceHandler;
    }

    /**
     * Return the {@link ProtocolChainInstanceHandler}
     */
    public ProtocolChainInstanceHandler getProtocolChainInstanceHandler() {
        return instanceHandler;
    }

    /**
     * @deprecated
     * Set the {@link SelectionKeyHandler} to use for managing the life
     * cycle of SelectionKey.
     * Method is deprecated. Use SelectorHandler.setSelectionKeyHandler() instead
     */
    public void setSelectionKeyHandler(SelectionKeyHandler selectionKeyHandler) {
        this.selectionKeyHandler = selectionKeyHandler;
    }

    /**
     * @deprecated
     * Return the {@link SelectionKeyHandler}
     * Method is deprecated. Use SelectorHandler.getSelectionKeyHandler() instead
     */
    public SelectionKeyHandler getSelectionKeyHandler() {
        return selectionKeyHandler;
    }

    /**
     * Add a {@link SelectorHandler}
     * @param selectorHandler - the {@link SelectorHandler}
     */
    public void addSelectorHandler(SelectorHandler selectorHandler) {
        selectorHandlers.add(selectorHandler);
        if (stateHolder.getState(false) != null && State.STOPPED != stateHolder.getState()) {
            addSelectorHandlerOnReadControllers(selectorHandler);
            if (readySelectorHandlerCounter != null) {
                readySelectorHandlerCounter.incrementAndGet();
            }
            if (stoppedSelectorHandlerCounter != null) {
                stoppedSelectorHandlerCounter.incrementAndGet();
            }
            startSelectorHandlerRunner(selectorHandler);
        }
    }

    /**
     * Set the first {@link SelectorHandler}
     * @param selectorHandler - the {@link SelectorHandler}
     */
    public void setSelectorHandler(SelectorHandler selectorHandler) {
        addSelectorHandler(selectorHandler);
    }

    /**
     * Return the {@link SelectorHandler} associated with the protocol.
     * @param protocol - the {@link Controller.Protocol}
     * @return {@link SelectorHandler}
     */
    public SelectorHandler getSelectorHandler(Protocol protocol) {
        for (SelectorHandler selectorHandler : selectorHandlers) {
            if (selectorHandler.protocol() == protocol) {
                return selectorHandler;
            }
        }
        return null;
    }

    /**
     * Return the {@link SelectorHandler} associated
     * with the {@link Selector}.
     * @param selector - the {@link Selector}
     * @return {@link SelectorHandler}
     */
    public SelectorHandler getSelectorHandler(Selector selector) {
        for (SelectorHandler selectorHandler : selectorHandlers) {
            if (selectorHandler.getSelector() == selector) {
                return selectorHandler;
            }
        }
        return null;
    }

    /**
     * Return the list {@link SelectorHandler}
     * @return {@link Queue}
     */
    public Queue getSelectorHandlers() {
        return selectorHandlers;
    }

    /**
     * Shuts down {@link SelectorHandler} and removes it from this
     * {@link Controller} list
     * @param {@link SelectorHandler} to remove
     */
    public void removeSelectorHandler(SelectorHandler selectorHandler) {
        if (selectorHandlers.remove(selectorHandler)) {
            removeSelectorHandlerOnReadControllers(selectorHandler);
            selectorHandler.shutdown();
        }
    }

    /**
     * Return the {@link ExecutorService} (Thread Pool) used by this Controller.
     */
    public ExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * Set the {@link ExecutorService} (Thread Pool).
     */
    public void setThreadPool(ExecutorService threadPool) {
        ensureAppropriatePoolSize( threadPool );
        this.threadPool = threadPool;
    }

    /**
     * Return the number of Reader threads count.
     */
    public int getReadThreadsCount() {
        return readThreadsCount;
    }

    /**
     * Set the number of Reader threads count.
     */
    public void setReadThreadsCount(int readThreadsCount) {
        this.readThreadsCount = readThreadsCount;
        if( readThreadsCount > 0 ) recalcRequiredThreadsCount();
        ensureAppropriatePoolSize( threadPool );
    }

    /**
     * Return the <code>ConnectorHandlerPool</code> used.
     */
    public ConnectorHandlerPool getConnectorHandlerPool() {
        return connectorHandlerPool;
    }

    /**
     * Set the <code>ConnectorHandlerPool</code> used.
     */
    public void setConnectorHandlerPool(ConnectorHandlerPool connectorHandlerPool) {
        this.connectorHandlerPool = connectorHandlerPool;
    }

    // ------------------------------------------------------ Runnable -------//
    /**
     * Execute this Controller.
     */
    public void run() {
        try {
            start();
        } catch (IOException e) {
            notifyException(e);
            throw new RuntimeException(e.getCause());
        }
    }

    // -------------------------------------------------------- Copyable ----//
    /**
     * Copy this Controller state to another instance of a Controller.
     */
    public void copyTo(Copyable copy) {
        Controller copyController = (Controller) copy;
        copyController.contexts = contexts;
        copyController.attributes = attributes;
        copyController.instanceHandler = instanceHandler;
        copyController.threadPool = threadPool;
        copyController.readThreadControllers = readThreadControllers;
        copyController.readThreadsCount = readThreadsCount;
        copyController.selectionKeyHandler = selectionKeyHandler;
        copyController.stateHolder = stateHolder;
        copyController.executePendingIOUsingSelectorThread = executePendingIOUsingSelectorThread;
    }

    // -------------------------------------------------------- Lifecycle ----//
    /**
     * Add controller state listener
     */
    public void addStateListener(ControllerStateListener stateListener) {
        stateListeners.add(stateListener);
    }

    /**
     * Remove controller state listener
     */
    public void removeStateListener(ControllerStateListener stateListener) {
        stateListeners.remove(stateListener);
    }

    /**
     * Notify controller started
     */
    public void notifyStarted() {
        for (ControllerStateListener stateListener : stateListeners) {
            stateListener.onStarted();
        }
    }

    /**
     * Notify controller is ready
     */
    public void notifyReady() {
        if (readySelectorHandlerCounter.decrementAndGet() == 0) {
            for (ControllerStateListener stateListener : stateListeners) {
                stateListener.onReady();
            }
        }
    }

    /**
     * Notify controller stopped
     */
    public void notifyStopped() {
        if (stoppedSelectorHandlerCounter.decrementAndGet() == 0) {
            // Notify internal listeners
            synchronized (stoppedSelectorHandlerCounter) {
                stoppedSelectorHandlerCounter.notifyAll();
            }
        }
    }

    /**
     * Notify exception occured
     */
    public void notifyException(Throwable e) {
        for (ControllerStateListener stateListener : stateListeners) {
            stateListener.onException(e);
        }
    }

    /**
     * Log the current Grizzly version. 
     */
    public void logVersion(){
        if (logger.isLoggable(Level.INFO)){
            logger.info(LogMessages.INFO_GRIZZLY_START(Grizzly.getRawVersion(), new Date()));
        }
    }

    /**
     * Start the Controller. If the thread pool and/or Handler has not been
     * defined, the default will be used.
     */
    public void start() throws IOException {

        stateHolder.getStateLocker().writeLock().lock();

        try {
            if (isStarted()) {
                return;
            }
            logVersion();

            if (kernelExecutor.isShutdown()) {
                // Re-create
                kernelExecutor = createKernelExecutor();
            }

            autoConfigureCore();
            if (threadPool == null) {
                threadPool = GrizzlyExecutorService.createInstance();
            }

            if (threadPool.isShutdown()) {
                threadPool = GrizzlyExecutorService.createInstance();
            }

            ensureAppropriatePoolSize(threadPool);
            final State state = stateHolder.getState(false);
            if (state == null || state == State.STOPPED) {
                // if selectorHandlers were not set by user explicitly,
                // add TCPSelectorHandler by default
                if (selectorHandlers.isEmpty()) {
                    SelectorHandler selectorHandler = new TCPSelectorHandler();
                    selectorHandlers.add(selectorHandler);
                }


                if (readThreadsCount > 0) {
                    initReadThreads();
                    multiReadThreadSelectorHandler =
                            new RoundRobinSelectorHandler(readThreadControllers);
                }

                stateHolder.setState(State.STARTED, false);
                notifyStarted();

                int selectorHandlerCount = selectorHandlers.size();
                readySelectorHandlerCounter = new AtomicInteger(selectorHandlerCount);
                stoppedSelectorHandlerCounter = new AtomicInteger(selectorHandlerCount);

                Iterator<SelectorHandler> it = selectorHandlers.iterator();
                while (it.hasNext() && selectorHandlerCount-- > 0) {
                    SelectorHandler selectorHandler = it.next();
                    if (selectorHandler instanceof TCPSelectorHandler) {
                        ((TCPSelectorHandler) selectorHandler)
                                .setExecutePendingIOUsingSelectorThread(executePendingIOUsingSelectorThread);
                        ((TCPSelectorHandler) selectorHandler).setMaxAcceptRetries(maxAcceptRetries);
                    }
                    startSelectorHandlerRunner(selectorHandler);
                }
            }
        } finally {
            stateHolder.getStateLocker().writeLock().unlock();
        }

        if (displayConfiguration){
            displayConfiguration();
        }

        waitUntilSelectorHandlersStop();

        if (readThreadsCount > 0) {
            multiReadThreadSelectorHandler.shutdown();
            multiReadThreadSelectorHandler = null;

            for (Controller readController : readThreadControllers) {
                try {
                    readController.stop();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Exception occured when stopping read Controller!", e);
                }
            }

            readThreadControllers = null;
        }

        selectorHandlers.clear();
        threadPool.shutdown();
        attributes = null;

        // Notify Controller listeners
        for (ControllerStateListener stateListener : stateListeners) {
            stateListener.onStopped();
        }
    }

    /**
     * Stop the Controller by canceling all the registered keys.
     */
    public void stop() throws IOException {
        stop(false);
    }

    /**
     * Stop the Controller by canceling all the registered keys.
     * @param isAsync, true if controller should be stopped asynchronously and control
     *                 returned immediately. If false - control will be returned
     *                 after Controller will be completely stopped.
     */
    public void stop(boolean isAsync) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        stateHolder.getStateLocker().writeLock().lock();
        try {
            final State state = stateHolder.getState(false);

            if (state == null || state == State.STOPPED) {
                logger.log(Level.FINE, "Controller is already in stopped state");
                return;
            }

            if (!isAsync) {
                addStateListener(new ControllerStateListenerAdapter() {

                    @Override
                    public void onException(Throwable e) {
                        removeStateListener(this);
                        latch.countDown();
                    }

                    @Override
                    public void onStopped() {
                        removeStateListener(this);
                        latch.countDown();
                    }
                });
            }
            stateHolder.setState(State.STOPPED, false);
        } finally {
            stateHolder.getStateLocker().writeLock().unlock();
        }

        if (!isAsync) {
            try {
                latch.await();
            } catch (InterruptedException e) {
            }
        }
        kernelExecutor.shutdownNow();
    }

    /**
     * Pause this {@link Controller} and associated {@link SelectorHandler}s
     */
    public void pause() throws IOException {
        stateHolder.setState(State.PAUSED);
    }

    /**
     * Resume this {@link Controller} and associated {@link SelectorHandler}s
     */
    public void resume() throws IOException {
        if (State.PAUSED != stateHolder.getState(false)) {
            throw new IllegalStateException("Controller is not in PAUSED state, but: " + stateHolder.getState(false));
        }

        stateHolder.setState(State.STARTED);
    }

    /**
     * Gets this {@link Controller}'s {@link StateHolder}
     * @return {@link StateHolder}
     */
    public StateHolder<State> getStateHolder() {
        return stateHolder;
    }

    /**
     * Initialize the number of ReadThreadController.
     */
    private void initReadThreads() {
        // Attributes need to be shared among Controller and its ReadControllers
        if (attributes == null) {
            attributes = new HashMap<String, Object>(2);
        }

        readThreadControllers = new ReadController[readThreadsCount];
        for (int i = 0; i < readThreadsCount; i++) {
            ReadController controller = new ReadController();
            copyTo(controller);
            controller.setReadThreadsCount(0);
            readThreadControllers[i] = controller;
        }

        for (SelectorHandler selectorHandler : selectorHandlers) {
            addSelectorHandlerOnReadControllers(selectorHandler);
        }

        for (ReadController readThreadController : readThreadControllers) {
            kernelExecutor.execute(readThreadController);
        }
    }

    /**
     * Register {@link SelectorHandler} on all read controllers
     * @param selectorHandler
     */
    private void addSelectorHandlerOnReadControllers(SelectorHandler selectorHandler) {
        if (readThreadControllers == null || readThreadsCount == 0) {
            return;
        }

        // Attributes need to be shared among SelectorHandler and its read-copies
        if (selectorHandler.getAttributes() == null) {
            selectorHandler.setAttributes(new HashMap<String, Object>(2));
        }

        for (Controller readController : readThreadControllers) {
            SelectorHandler copySelectorHandler = Cloner.clone(selectorHandler);
            try {
                copySelectorHandler.setSelector(Utils.openSelector());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error opening selector!", e);

            }

            readController.addSelectorHandler(copySelectorHandler);
        }
    }

    /**
     * Starts <code>SelectorHandlerRunner</code>
     * @param selectorHandler
     */
    protected void startSelectorHandlerRunner(SelectorHandler selectorHandler) {
        if (selectorHandler.getThreadPool() == null) {
            selectorHandler.setThreadPool(threadPool);
        }
        Runnable selectorRunner = new SelectorHandlerRunner(this, selectorHandler);
        // check if there is java.nio.Selector already open,
        // if so, just notify the controller onReady() listeners
        if (selectorHandler.getSelector() != null) {
            notifyReady();
        }

        if (useLeaderFollowerStrategy) {
            threadPool.execute(selectorRunner);
        } else {
            kernelExecutor.execute(selectorRunner);
        }
    }

    /**
     * Register {@link SelectorHandler} on all read controllers
     * @param selectorHandler
     */
    private void removeSelectorHandlerOnReadControllers(SelectorHandler selectorHandler) {
        if (readThreadControllers == null) {
            return;
        }

        for (ReadController readController : readThreadControllers) {
            readController.removeSelectorHandlerClone(selectorHandler);
        }
    }

    /**
     * Is this Controller started?
     * @return <code>boolean</code> true / false
     */
    public boolean isStarted() {
        return stateHolder.getState() == State.STARTED;
    }


    // ----------- ConnectorHandlerPool interface implementation ----------- //
    /**
     * Return an instance of a {@link ConnectorHandler} based on the
     * Protocol requested.
     */
    public ConnectorHandler acquireConnectorHandler(Protocol protocol) {
        return connectorHandlerPool.acquireConnectorHandler(protocol);
    }

    /**
     * Return a {@link ConnectorHandler} to the pool of ConnectorHandler.
     * Any reference to the returned must not be re-used as that instance
     * can always be acquired again, causing unexpected results.
     */
    public void releaseConnectorHandler(ConnectorHandler connectorHandler) {
        connectorHandlerPool.releaseConnectorHandler(connectorHandler);
    }

    /**
     * <tt>true</tt> if OP_ERAD and OP_WRITE can be handled concurrently.
     * If <tt>false</tt>, the Controller will first invoke the OP_READ handler and
     * then invoke the OP_WRITE during the next Selector.select() invocation.
     */
    public boolean isHandleReadWriteConcurrently() {
        return handleReadWriteConcurrently;
    }

    /**
     * <tt>true</tt> if OP_ERAD and OP_WRITE can be handled concurrently.
     * If <tt>false</tt>, the Controller will first invoke the OP_READ handler and
     * then invoke the OP_WRITE during the next Selector.select() invocation.
     */
    public void setHandleReadWriteConcurrently(boolean handleReadWriteConcurrently) {
        this.handleReadWriteConcurrently = handleReadWriteConcurrently;
    }

    /**
     * Method waits until all initialized {@link SelectorHandler}s will
     * not get stopped
     */
    protected void waitUntilSelectorHandlersStop() {
        synchronized (stoppedSelectorHandlerCounter) {
            while (stoppedSelectorHandlerCounter.get() > 0 ||
                    !State.STOPPED.equals(stateHolder.getState())) {
                try {
                    stoppedSelectorHandlerCounter.wait(1000);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    // ----------- AttributeHolder interface implementation ----------- //
    /**
     * Remove a key/value object.
     * Method is not thread safe
     *
     * @param key - name of an attribute
     * @return  attribute which has been removed
     */
    public Object removeAttribute(String key) {
        if (attributes == null) {
            return null;
        }

        return attributes.remove(key);
    }

    /**
     * Set a key/value object.
     * Method is not thread safe
     *
     * @param key - name of an attribute
     * @param value - value of named attribute
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }

        attributes.put(key, value);
    }

    /**
     * Return an object based on a key.
     * Method is not thread safe
     *
     * @param key - name of an attribute
     * @return - attribute value for the <tt>key</tt>, null if <tt>key</tt>
     *           does not exist in <tt>attributes</tt>
     */
    public Object getAttribute(String key) {
        if (attributes == null) {
            return null;
        }

        return attributes.get(key);
    }

    /**
     * Set a {@link Map} of attribute name/value pairs.
     * Old {@link AttributeHolder} values will not be available.
     * Later changes of this {@link Map} will lead to changes to the current
     * {@link AttributeHolder}.
     *
     * @param attributes - map of name/value pairs
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Return a {@link Map} of attribute name/value pairs.
     * Updates, performed on the returned {@link Map} will be reflected in
     * this {@link AttributeHolder}
     *
     * @return - {@link Map} of attribute name/value pairs
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Return the Controller which is handling the {@link Handler}
     * @param handler The handler (like {@link SelectorHandler})
     * @return The Controller associated with the Handler, or null if not
     * associated.
     */
    public static Controller getHandlerController(Handler handler) {
        if (handler instanceof SelectorHandler) {
            controllersLock.readLock().lock();
            try {
                for (Controller controller : controllers.keySet()) {
                    if (controller.getSelectorHandlers().contains(handler)) {
                        return controller;
                    }
                }
            } finally {
                controllersLock.readLock().unlock();
            }
        }
        return null;
    }

    /**
     * Execute the {@link Controller#run} using the internal/kernel 
     * {@link Executors}
     */
    protected void executeUsingKernelExecutor() {
        kernelExecutor.submit(this);
    }

    /**
     * Execute the {@link Runnable} using the internal kernel 
     * {@link Executors}. Do not invoke that method for application's task
     * as this is the {@link ExecutorService} used internally to spawn 
     * Thread.
     * @param r a Runnable
     */
    public void executeUsingKernelExecutor(Runnable r) {
        kernelExecutor.execute(r);
    }

    /**
     * Create the {@link ExecutorService} used to execute kernel like operations.
     */
    protected ExecutorService createKernelExecutor() {
        return kernelExecutorFactory.create();
    }

    /**
     * Are {@link Context} instance cached/pooled or a new instance gets created
     * for every transaction?
     * @return true if {@link Context} get cached. Default is <tt>false</tt>
     */
    public boolean isAllowContextCaching() {
        return allowContextCaching;
    }

    /**
     * Set to <tt>true</tt> for enabling caching of {@link Context}.
     * 
     * @param allowContextCaching <tt>true</tt> for enabling caching of {@link Context}.
     */
    public void setAllowContextCaching(boolean allowContextCaching) {
        this.allowContextCaching = allowContextCaching;
    }

    /**
     * Is Leader/Follower strategy used?
     *
     * @return <tt>true</tt>, if Leader/follower strategy is used, or
     * <tt>false</tt> otherwise.
     */
    public boolean useLeaderFollowerStrategy() {
        return useLeaderFollowerStrategy;
    }

    /**
     * Set, if Leader/Follower strategy should be used.
     * 
     * @param useLeaderFollowerStrategy <tt>true</tt>, if Leader/follower
     * strategy should be used, or <tt>false</tt> otherwise.
     */
    public void useLeaderFollowerStrategy(boolean useLeaderFollowerStrategy) {
        this.useLeaderFollowerStrategy = useLeaderFollowerStrategy;
    }


    /**
     * Return <tt>true</tt> if the Controller is auto configuring the
     * number of {@link ReadController} and its associated thread pool size.
     * @return the autoConfigure
     */
    public boolean isAutoConfigure() {
        return autoConfigure;
    }

    /**
     * Set to true <tt>true</tt> if the Controller is auto configuring the
     * number of {@link ReadController} and its associated thread pool size.
     * @param autoConfigure the autoConfigure to set
     */
    public void setAutoConfigure(boolean autoConfigure) {
        this.autoConfigure = autoConfigure;
    }

    private void recalcRequiredThreadsCount() {
        final int selectorHandlersCount = selectorHandlers.size();
        final int clonesNumber = selectorHandlersCount > 0 ? selectorHandlersCount : 1;
        requiredThreadsCount = clonesNumber * (readThreadsCount + 1) * 2;
    }

    /**
     * Return <tt>true</tt>, if selector thread has to be applied to execute I/O
     * operation, or <tt>false</tt> (by default), meaning that I/O operation could
     * be executed in the current thread.
     *
     * @return the executePendingIOUsingSelectorThread
     */
    public boolean isExecutePendingIOUsingSelectorThread() {
        return executePendingIOUsingSelectorThread;
    }

    /**
     * Set <tt>true</tt>, if selector thread has to be applied to execute I/O
     * operation, or <tt>false</tt> (by default), meaning that I/O operation could
     * be executed in the current thread.
     * It's not safe to change this value, when <tt>TCPSelectorHandler</tt> has been already started.
     *
     * @param executePendingIOUsingSelectorThread the executePendingIOUsingSelectorThread to set
     */
    public void setExecutePendingIOUsingSelectorThread(boolean executePendingIOUsingSelectorThread) {
        this.executePendingIOUsingSelectorThread = executePendingIOUsingSelectorThread;
    }

    /**
     * Get the factory, responsible for creating kernel {@link ExecutorService}s.
     *
     * @return the factory, responsible for creating kernel {@link ExecutorService}s.
     */
    public KernelExecutorFactory getKernelExecutorFactory() {
        return kernelExecutorFactory;
    }

    /**
     * Set the factory, responsible for creating kernel {@link ExecutorService}s.
     *
     * @param kernelExecutorFactory the factory, responsible for creating kernel {@link ExecutorService}s.
     */
    public void setKernelExecutorFactory(KernelExecutorFactory kernelExecutorFactory) {
        this.kernelExecutorFactory = kernelExecutorFactory;
    }

    /**
     * Max number of accept() failures before abording.
     * @param maxAcceptRetries
     */
    public void setMaxAcceptRetries(int maxAcceptRetries){
        this.maxAcceptRetries = maxAcceptRetries;
    }

    /**
     * Display the internal configuration of this instance.
     */
    public void setDisplayConfiguration(boolean displayConfiguration) {
        this.displayConfiguration = displayConfiguration;
    }

    private void displayConfiguration(){
       if (displayConfiguration){

           String threadPoolResult;
           if (threadPool instanceof GrizzlyExecutorService) {
               threadPoolResult = ((GrizzlyExecutorService) threadPool).getConfiguration().toString();
           } else {
               threadPoolResult = threadPool.toString();
           }

            logger.log(Level.INFO,
                    LogMessages.INFO_GRIZZLY_CONFIGURATION(
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("java.version"),
                    System.getProperty("java.vendor"),
                    threadPoolResult,
                    readThreadsCount,
                    autoConfigure,
                    useLeaderFollowerStrategy,
                    selectorHandlers.size(),
                    selectionKeyHandler,
                    isAllowContextCaching(),
                    maxAcceptRetries,
                    handleReadWriteConcurrently,
                    getProtocolChainInstanceHandler()));
        }

    }

    public static interface KernelExecutorFactory {

        ExecutorService create();

        public static class DefaultFactory implements KernelExecutorFactory {

            public ExecutorService create() {
                return create("grizzly-kernel", "Grizzly-kernel-thread");
            }

            /**
             * Create the {@link ExecutorService} used to execute kernel operations.
             * Passed paratemeters let us cusomize thread pool group name and
             * name pattern for an individual threads.
             */
            protected final ExecutorService create(
                    final String threadGroupName, final String threadNamePattern) {

                return Executors.newCachedThreadPool(new WorkerThreadFactory(threadGroupName) {

                    private AtomicInteger counter = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        final Thread newThread = super.newThread(r);
                        newThread.setName(threadNamePattern + "("
                                + counter.incrementAndGet() + ")");
                        return newThread;
                    }
                });
            }
        }
    }
}
