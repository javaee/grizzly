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

package com.sun.grizzly.suspendable;

import com.sun.grizzly.Context;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.Context.KeyRegistrationState;
import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.NIOContext;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.ThreadAttachment.Mode;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Suspend the processing of the request and associated
 * {@link com.sun.grizzly.ProtocolChain}
 * 
 * <p>
 * When a connection is suspended, the framework will not continue the execution
 * of the ProtocolChain and its associated {@link ProtocolFilter}. Instead the 
 * framework will wait until either {@link Suspendable#resume} is called, 
 * {@link Suspendable#cancel} is called or the passed timeout expires.  
 * 
 * If {@link Suspendable#resume} is called or the timeout expires
 * then the ProtocolChain will be resumed from where it has been suspended.
 * 
 * If {@link Suspendable#cancel} is called, the ProtocolChain execution will
 * not be interrupted and the connection automatically closed.
 * </p>
 * 
 * <p>
 * A connection can be suspended before
 * ({@link SuspendableFilter.Suspend#BEFORE}) the
 * {@link com.sun.grizzly.ProtocolChain} invoke the next {@link ProtocolFilter},
 * or after ({@link SuspendableFilter.Suspend#AFTER}) all {@link ProtocolFilter}
 * has been called (e.g. when {@link #postExecute} is invoked).
 * </p>
 *
 * <p> This {@link ProtocolFilter} <strong>must always</strong> be invoked after
 * some read operation has occurred.</p>
 * 
 * <p> A common usage of {@link SuspendableFilter} could be: </p>
 * <p><pre><code>
  final ProtocolFilter readFilter = new ReadFilter();
  final SuspendableFilter suspendFilter = new SuspendableFilter();
  final ProtocolFilter yourProtocolFilter = new YourProtocolFilter();

  Suspendable suspendable =
      suspendFilter.suspend("YOUR TOKEN", timeout, attachment, new SuspendableHandler() {

      public void interupted(A attachment) {
          // Add if you need write something before the connection get closed
      }

      public void resumed(A attachment) {
          // When the connection is resumed
      }

      public void expired(A attachment) {
          // When the connection is expired.
  }, {@link SuspendableFilter.Suspend);
 * </code></pre></p>
 * <p>
 * This magic <code>"YOUR TOKEN"</code> is ... TODO 
 * </p>
 *
 * <p>
 * As an example, all clients that send the bytes 'grizzly is cool' will be
 * suspended for 5 seconds before the {@link com.sun.grizzly.filter.EchoFilter}
 * is called.
 * </p>
 * <p><pre><code>
        final ProtocolFilter readFilter = new ReadFilter();
        final SuspendableFilter suspendFilter = new SuspendableFilter();
        final ProtocolFilter echoFilter = new EchoFilter();

        suspendable
                = suspendFilter.suspend("grizzly is cool", 5000, null, new SuspendableHandler() {

            public void interupted(Object attachment) {
                System.out.println("interrupted");
            }

            public void resumed(Object attachment) {
                System.out.println("resumed");
            }

            public void expired(Object attachment) {
                System.out.println("expired");
            }
        }, Suspend.BEFORE);
 * </code></pre></p>
 * 
 * TODO: Add a better patern matching algorithm
 *       Allow OP_WRITE operation to be suspended as well.
 * 
 * @author Jeanfrancois Arcand
 * @since 1.7.3
 */
public class SuspendableFilter<T> implements ProtocolFilter {

    /**
     * Suspend the connection BEFORE of AFTER
     */
    public enum Suspend {BEFORE,AFTER}
    
    
    /**
     * Cache the pattern and its associated {@link SuspendableHandler} wrapper.
     */
    private final Map<byte[],SuspendableHandlerWrapper<? extends T>> suspendCandidates
            = new ConcurrentHashMap<byte[],SuspendableHandlerWrapper<? extends T>>();
    
    
    /**
     * The current list of suspended {@link SelectionKey}.
     */
    protected final Map<SelectionKey,KeyHandler> suspendedKeys
            = new ConcurrentHashMap<SelectionKey,KeyHandler>();
        
    
    /**
     * Monitor suspended {@link SelectionKey}
     */
    private final static SuspendableMonitor suspendableMonitor = new SuspendableMonitor();
     
    
    /**
     * Logger
     */
    private final static Logger logger = Controller.logger();
     
    /**
     * Controller
     */
    private volatile Controller controller;
    
    /**
     * {@link DefaultProtocolChain} used to execute resumed connection.
     */
    private DefaultProtocolChain protocolChain;
    
    
    /**
     * The next {@link ProtocolFilter} to invoke when resuming a connection.
     */
    private final int nextFilterPosition;
    
    
    // -------------------------------------------------- Constructor ------- //
    
    
    public SuspendableFilter(){
        this(2);
    }
    
    
    /**
     * Create a new SuspendableFilter, and resume its associated {@link DefaultProtocolChain}
     * from position {@link #nextFilterPosition}
     * @param nextFilterPosition {@link #nextFilterPosition}
     */
    public SuspendableFilter(int nextFilterPosition){
        this.nextFilterPosition = nextFilterPosition;
    }
    
    // -------------------------------------------------- Suspend API ------- //
    
    /**
     * Suspend a connection based on a String. Evevry bytes read from the connection
     * will be inspected and if the String match, the connection will be suspended. 
     * @param match The String used to decide if a connection and its associated
     * bytes need to be suspended. By default, the connection will be resumed
     * after 60 seconds.
     * @return A {@link Suspendable}
     */
    public Suspendable suspend(String match){           
        return suspend(match, 60000, null, null);
    }

    
    /**
     * Suspend a connection based on a String. Evevry bytes read from the connection
     * will be inspected and if the String match, the connection will be suspended. 
     * @param match The String used to decide if a connection and its associated
     * bytes need to be suspended.
     * @param expireTime the time in milliseconds before a connection is resumed.
     * @param attachement The object that will be returned when the {@link SuspendableHandler}
     * methods are invoked.
     * @param sh A {@link SuspendableHandler} used to get notification about the suspended
     * connection state.
     * @return A {@link Suspendable}
     */    
    public Suspendable suspend(String match,long expireTime,
            T attachement,SuspendableHandler<? extends T> sh){   
        return suspend(match,expireTime,attachement,sh,Suspend.AFTER);
    }
    
    
    /**
     * Suspend a connection based on a String. Evevry bytes read from the connection
     * will be inspected and if the String match, the connection will be suspended. 
     * @param match The String used to decide if a connection and its associated
     * bytes need to be suspended.
     * @param expireTime the time in milliseconds before a connection is resumed.
     * @param attachement The object that will be returned when the {@link SuspendableHandler}
     * methods are invoked.
     * @param sh A {@link SuspendableHandler} used to get notification about the suspended
     * connection state.
     * @param suspendWhen Suspend before or after the next ProtocolChain execution,
     * @return A {@link Suspendable}
     */    
    public Suspendable suspend(String match,long expireTime,
            T attachement,SuspendableHandler<? extends T> sh, Suspend suspendWhen){           
        Suspendable s = new Suspendable(this);
        suspendCandidates.put(match.getBytes(),  new SuspendableHandlerWrapper(
                sh,attachement, expireTime,s,suspendWhen));
        return s;
    }
     
    
    // ----------------------------------------------ProtocolFilter API ------- //
    
    
    /**
     * Excute the pattern matching algorithm to determine if a the current
     * connection must be suspended or not, and when.
     * 
     * @param ctx The current {@link Context}
     * @return true if the ProtocolChain should continue its execution, 
     * false if the connection has been suspended.
     * @throws java.io.IOException
     */
    public boolean execute(Context ctx) throws IOException { 
        WorkerThread wt = (WorkerThread)Thread.currentThread();
        ByteBuffer bb = wt.getByteBuffer();
        
        if (controller == null){
            synchronized(this){
                if (controller == null){
                    controller = ctx.getController();
                    controller.executeUsingKernelExecutor(suspendableMonitor);
                }
            }
        }

                
        if (ctx.getProtocol() == Controller.Protocol.TCP){
            ctx.getSelectionKey().attach(null);
        } else {
            wt.getAttachment().setTimeout(Long.MIN_VALUE);
        }
                
        if (protocolChain == null){
            if (ctx.getProtocolChain() instanceof DefaultProtocolChain){
                protocolChain = (DefaultProtocolChain)ctx.getProtocolChain();
            } else {
                throw new IllegalStateException("SuspendableFilter cannot be " +
                        "used without the DefaultProtocolChain");
            }
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Trying to match " + ctx.getSelectionKey());
        }
        
        // This will be quite slow if a lot of registration. 
        // TODO: Need a better algorithm.
        SuspendableHandlerWrapper<? extends T> sh = null;
        Iterator<byte[]> iterator = suspendCandidates.keySet().iterator();
        byte[] matchBytes = null;
        while(iterator.hasNext()){
            matchBytes = iterator.next();
            if (Utils.findBytes(bb,matchBytes) > -1){
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("Find match: " + (new String(matchBytes))
                            + " Suspending: " + ctx.getSelectionKey());
                }
                sh = suspendCandidates.get(matchBytes);
                break;
            }
        }

        if (sh != null){
            KeyHandler kh = new KeyHandler();
            kh.setSuspendableHandler(sh);
            suspendedKeys.put(ctx.getSelectionKey(),kh);
            if (sh.getSuspendWhen() == Suspend.BEFORE){
                suspend(ctx,true);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("-----> " + ctx.getKeyRegistrationState());
                }
                return false;
            }
        }
        
        return true;
    }

    
    /**
     * Excute the pattern matching algorithm to determine if a the current
     * connection must be suspended or not, and when.
     * 
     * @param ctx The current {@link Context}
     * @return true if the ProtocolChain should continue its execution, 
     * false if the connection has been suspended.
     * @throws java.io.IOException
     */
    public boolean postExecute(Context ctx) throws IOException {                
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("<----- " + ctx.getKeyRegistrationState());
        }
        
        if (!suspendedKeys.isEmpty() 
                && ctx.getKeyRegistrationState() == KeyRegistrationState.REGISTER){
            suspend(ctx,false);
            return false;
        }
        return true;
    }

    
    // -------------------------------------------------- Implementation ------- //
    
    /**
     * Suspend the request by creating the appropriate structure so the state
     * of the current connection is not lost.
     * @param ctx The current {@link Context}
     * @param incomingRequest suspend now of after.
     */
    private void suspend(Context ctx, boolean incomingRequest){
        try {
            SelectionKey key = ctx.getSelectionKey();
            KeyHandler kh = suspendedKeys.get(key);
            
            SuspendableHandlerWrapper<? extends T> sh = null;
            if (kh != null){
                sh = kh.getSuspendableHandler();
            } else {
                kh = new KeyHandler(); 
            }
            
            if (kh != null && !incomingRequest){
                if (sh.getSuspendWhen() == Suspend.BEFORE){
                    return;
                }
            }
            
            // If the users didn't want to be notified.
            if (sh == null) {
                // TODO: Configurable.
                sh = new SuspendableHandlerWrapper(new SuspendableHandler() {

                    public void interupted(Object attachment) {
                    }

                    public void expired(Object attachment) {
                    }

                    public void resumed(Object attachment) {
                    }
                }, null, 30000, new Suspendable(this), Suspend.AFTER);
            }
            sh.setSuspendableFilter(this);
            sh.suspendable.setKey(key);
            sh.setSelectorHandler(ctx.getSelectorHandler());
            
            kh.setSuspendableHandler(sh);
            kh.setKey(key);
            WorkerThread workerThread = (WorkerThread) Thread.currentThread();
            ThreadAttachment attachment = workerThread.getAttachment();
            attachment.setMode(Mode.STORE_ALL);
            kh.setThreadAttachment(workerThread.detach());
            ctx.setKeyRegistrationState(KeyRegistrationState.NONE);
           
            suspendableMonitor.suspend(kh);
        } catch (Throwable ex) {
            if (logger.isLoggable(Level.FINE)){
                logger.log(Level.FINE,"suspend",ex);
            }
        }
    }
 
    
    /**
     * Resume the connection by register back the SelectionKey for OP event.
     * @param key
     * @return true if the connection was resumed.
     */
    protected boolean resume(SelectionKey key){ 
        KeyHandler kh = suspendedKeys.remove(key); 
        if (kh == null || kh.getSuspendableHandler() == null){
            return false;
        } 
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Resuming: " + kh.getSuspendableHandler());
        }

        kh.getSuspendableHandler().getSuspendableHandler().
                resumed(kh.getSuspendableHandler().getAttachment());
        
        if (kh.getSuspendableHandler().getSuspendWhen() == Suspend.AFTER){
            kh.getSuspendableHandler().getSelectorHandler()
                    .register(key.channel(), SelectionKey.OP_READ);
        } else {
            NIOContext ctx = (NIOContext)controller.pollContext();
            controller.configureContext(key, null, ctx,
                    kh.getSuspendableHandler().getSelectorHandler());
            ctx.execute(new SuspendableContextTask(
                    protocolChain, kh.getThreadAttachment(),
                    nextFilterPosition));
        }
             
        return true;
    }
    
    
    /**
     * Cancel the connection by internalling cancelling the associated
     * {@link ReadableChannel} and it associated {@link SelectionKey}
     * @param key
     */
    protected void cancel(SelectionKey key){
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Cancelling: " + key);
        }
        KeyHandler kh = suspendedKeys.remove(key); 
        if (kh == null){
            return;
        } 

        if (kh.getSuspendableHandler() == null){
            return;
        } 
        kh.getSuspendableHandler().getSuspendableHandler()
                .interupted(kh.getSuspendableHandler().getAttachment());
        kh.getSuspendableHandler().getSelectorHandler()
                .getSelectionKeyHandler().cancel(key);
        kh.setThreadAttachment(null);
    }
 
        
    
    /**
     * Wrapper class around a {@link SuspendableHandler} with add some connection
     * state.
     */
    protected class SuspendableHandlerWrapper<A>{        
        public SuspendableHandlerWrapper(SuspendableHandler<A> sh, A attachment, 
                long expireTime, Suspendable suspendable, Suspend suspendWhen){
            this.suspendableHandler = sh;
            this.attachment = attachment; 
            this.expireTime = expireTime;
            this.suspendable = suspendable;
            this.suspendWhen = suspendWhen;
        }
        
        private SuspendableHandler<A> suspendableHandler;
        private A attachment;
        private long expireTime = 0L;    
        private SuspendableFilter suspendableFilter;  
        private Suspendable suspendable;
        private SelectorHandler selectorHandler;
        private Suspend suspendWhen;

        protected

        SuspendableHandler<A> getSuspendableHandler() {
            return suspendableHandler;
        }

        protected void setSuspendableHandler(SuspendableHandler<A> suspendableHandler) {
            this.suspendableHandler = suspendableHandler;
        }

        protected A getAttachment() {
            return attachment;
        }

        protected void setAttachment(A attachment) {
            this.attachment = attachment;
        }

        protected long getExpireTime() {
            return expireTime;
        }

        protected void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        protected SuspendableFilter getSuspendableFilter() {
            return suspendableFilter;
        }

        protected void setSuspendableFilter(SuspendableFilter suspendableFilter) {
            this.suspendableFilter = suspendableFilter;
        }

        protected Suspendable getSuspendable() {
            return suspendable;
        }

        protected void setSuspendable(Suspendable suspendable) {
            this.suspendable = suspendable;
        }

        protected SelectorHandler getSelectorHandler() {
            return selectorHandler;
        }

        protected void setSelectorHandler(SelectorHandler selectorHandler) {
            this.selectorHandler = selectorHandler;
        }

        protected Suspend getSuspendWhen() {
            return suspendWhen;
        }

        protected void setSuspendWhen(Suspend suspendWhen) {
            this.suspendWhen = suspendWhen;
        }
    }
    
    
    /**
     * Struc to keep state of the current suspended Connection.
     */
    protected class KeyHandler{        
        private SelectionKey key;
        private SelectionKey foreignKey;
        private SuspendableHandlerWrapper handler;
        private ThreadAttachment threadAttachment;        
        private long registrationTime = 0L;

        protected

        SelectionKey getKey() {
            return key;
        }

        protected void setKey(SelectionKey key) {
            this.key = key;
        }

        protected SelectionKey getForeignKey() {
            return foreignKey;
        }

        protected void setForeignKey(SelectionKey foreignKey) {
            this.foreignKey = foreignKey;
        }

        protected SuspendableHandlerWrapper getSuspendableHandler() {
            return handler;
        }

        protected void setSuspendableHandler(SuspendableHandlerWrapper sh) {
            this.handler = sh;
        }

        protected ThreadAttachment getThreadAttachment() {
            return threadAttachment;
        }

        protected void setThreadAttachment(ThreadAttachment threadAttachment) {
            this.threadAttachment = threadAttachment;
        }

        protected long getRegistrationTime() {
            return registrationTime;
        }

        protected void setRegistrationTime(long registrationTime) {
            this.registrationTime = registrationTime;
        }        
    }
}
