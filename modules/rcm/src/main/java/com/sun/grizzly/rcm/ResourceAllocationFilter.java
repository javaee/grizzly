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

package com.sun.grizzly.rcm;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.NIOContext;
import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.SelectorHandler;
import com.sun.grizzly.filter.ParserProtocolFilter;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.Cloner;
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Queue;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * This ProtocolFilter is an implementation of a Resource Consumption Management
 * (RCM) system. RCM system are allowing you to enable virtualization of system
 * resources per web application, similar to Solaris 10 Zone or the outcome of
 * the upcoming JSR 284.
 *
 * This ProtocolFiler uses a {@link ProtocolParser} to determine which
 * token to use to enable virtualization. As an example, configuring this class
 * to use the <code>ContextRootAlgorithm</code> will allow virtualization
 * and isolation of http request. As an example, if you define:
 *
 * -Dcom.sun.grizzly.rcm.policyMetric="/myApplication|0.9"
 *
 * This ProtocolFilter will allocate 90% of the current threads count to
 * application myApplication, and the remaining 10% to any other context-root
 * (or application). See com.sun.grizzly.rcm.RCM for an example.
 *
 * @author Jeanfrancois Arcand
 */
public class ResourceAllocationFilter extends ParserProtocolFilter{
    
    public final static String BYTE_BUFFER = "byteBuffer";
    
    protected final static String RESERVE = "reserve";
    protected final static String CEILING = "ceiling";
    
    protected final static String ALLOCATION_MODE =
            "com.sun.grizzly.rcm.policyMethod";
    
    protected final static String RULE_TOKENS =
            "com.sun.grizzly.rcm.policyMetric";
    
    private final static String DELAY_VALUE = "com.sun.grizzly.rcm.delay"; // milli-seconds
    
    protected final static String QUERY_STRING="?";
    protected final static String PATH_STRING="/";
    protected final static String ASTERISK_STRING="*";
    
    
    public final static String BYTEBUFFER_INPUTSTREAM = "bbInputStream";
    
    public final static String INVOKE_NEXT = "invokeNextFilter";
    
    
    /**
     * The {@link ExecutorService} configured based on the
     * <code>threadRatio</code>. This {@link ExecutorService} is only used
     * by privileged application.
     */
    protected final static ConcurrentHashMap<String, ExecutorService> threadPools
            = new ConcurrentHashMap<String, ExecutorService>();
    
    
    /**
     * The list of privileged token used to decide if a request can be
     * serviced by the privileged {@link ExecutorService}.
     */
    protected final static ConcurrentHashMap<String,Double>
            privilegedTokens = new ConcurrentHashMap<String,Double>();
    
    
    /**
     * The thread ratio used when an application isn't listed as a privileged
     * application.
     */
    protected static double leftRatio = 1;
    
    
    /**
     * The allocation mode used: celling or Reserve. With Ceiling policy,
     * the strategy is to wait until all apps queus are showing some slack.
     * With Reserve policiy, if 100% reservation is made by other apps,
     * cancel the request processing.
     */
    protected static String allocationPolicy = RESERVE;
    
    
    /**
     * The time this thread will sleep when a rule is delayed.
     */
    private static long delayValue = 5 * 1000; // milli-seconds
    
    
    /**
     * Cache the current statefull ProtocolParser.
     */
    private final Queue<ProtocolParser> protocolParserCache =
            DataStructures.getCLQinstance(ProtocolParser.class);
    
    static {
        try{
            if (System.getProperty(RULE_TOKENS) != null){
                StringTokenizer privList =
                        new StringTokenizer(System.getProperty(RULE_TOKENS),",");
                StringTokenizer privElement;
                String tokens;
                double countRatio = 0;
                double tokenValue;
                while (privList.hasMoreElements()){
                    privElement = new StringTokenizer(privList.nextToken());
                    
                    while (privElement.hasMoreElements()){
                        tokens = privElement.nextToken();
                        int index = tokens.indexOf("|");
                        String tokenKey = tokens.substring(0, index);
                        // Remove last slash
                        boolean slash = tokenKey.endsWith(PATH_STRING);
                        if (slash){
                            tokenKey = tokenKey.substring(0,tokenKey.length() -1);
                        }
                        // Remove last asterisk. i.g. "/examples/*"
                        boolean asterisk = tokenKey.endsWith(PATH_STRING + ASTERISK_STRING);
                        if( asterisk )
                            tokenKey = tokenKey.substring(0,tokenKey.length() -2);
                        // such as "/" or "/*"
                        if(tokenKey.length() == 0)
                            tokenKey = ASTERISK_STRING; // default key
                        tokenValue = Double.valueOf(tokens.substring(index+1));
                        privilegedTokens.put(tokenKey,tokenValue);
                        countRatio += tokenValue;
                    }
                }
                if ( countRatio > 1 ) {
                    Controller.logger().info("Thread ratio too high. The total must be lower or equal to 1");
                }  else {
                    leftRatio = 1 - countRatio;
                }
            }
        } catch (Exception ex){
            Controller.logger().log(Level.SEVERE,"Unable to set the ratio",ex);
        }
        
        if (System.getProperty(ALLOCATION_MODE) != null){
            allocationPolicy = System.getProperty(ALLOCATION_MODE);
            if ( !allocationPolicy.equals(RESERVE) &&
                    !allocationPolicy.equals(CEILING) ){
                Controller.logger().info("Invalid allocation policy");
                allocationPolicy = RESERVE;
            }
        }

        String delayValueString = System.getProperty( DELAY_VALUE );
        if( delayValueString != null ) {
            long delayValueLong = -1;
            try {
                delayValueLong = Long.parseLong( delayValueString );
            } catch( NumberFormatException e ) {
                Controller.logger().info( "Invalid delay value:" + delayValueString );
            }
            if( delayValueLong > 0 )
                delayValue = delayValueLong;
        }
    }


    public ResourceAllocationFilter() {
    }
    
    
    @Override
    public boolean execute(Context ctx) throws IOException {
         
        // We already executed this one, so pass control to the next filter.
        if (ctx.getAttribute(INVOKE_NEXT) != null){
            return true;
        }
        return super.execute(ctx);
    }
    
    
    /**
     * Invoke the {@link ProtocolParser}. If more bytes are required,
     * register the <code>SelectionKey</code> back to its associated
     * {@link SelectorHandler}
     * @param ctx the Context object.
     * @return <tt>true</tt> if no more bytes are needed.
     */
    @Override
    public boolean invokeProtocolParser(Context ctx, 
            ProtocolParser protocolParser){

        super.invokeProtocolParser(ctx, protocolParser);
        
        WorkerThread workerThread = (WorkerThread)Thread.currentThread();
        ByteBuffer byteBuffer = workerThread.getByteBuffer();
        Protocol protocol = ctx.getProtocol();
        
        ByteBufferInputStream inputStream = (ByteBufferInputStream)
        ctx.getAttribute(BYTEBUFFER_INPUTSTREAM);
        if (inputStream == null){
            inputStream = new ByteBufferInputStream();
            ctx.setAttribute(BYTEBUFFER_INPUTSTREAM,inputStream);
        }
        
        if (protocol == Controller.Protocol.TLS){
            inputStream.setSecure(true);
        }
        
        String token = getContextRoot(byteBuffer);
        int delayCount = 0;
        while (leftRatio == 0 && getAppropriateThreadRatio(token) == null) {
            if ( allocationPolicy.equals(RESERVE) ){
                delay(ctx);
                delayCount++;
            } else if ( allocationPolicy.equals(CEILING) ) {
                
                // If true, then we need to wait for free space. If false, then
                // we can go ahead and let the task execute with its default
                // thread pool
                if (isThreadPoolInUse()){
                    delay(ctx);
                    delayCount++;
                } else {
                    break;
                }
            }
            if (delayCount > 5){
                closeConnection(ctx);
                return false;
            }
        }
        
        // Lazy instanciation
        ExecutorService threadPool = getAppropriateThreadPool(token);
        if (threadPool == null){
            threadPool = filterRequest(token, ctx.getThreadPool());
            threadPools.put(token,threadPool);
        }
        
        // Switch the ByteBuffer as we are about to leave this thread.
        ByteBuffer nBuf = (ByteBuffer)ctx.getAttribute(BYTE_BUFFER);
        if (nBuf != null){
            // Switch the bytebuffer
            nBuf.clear();
            workerThread.setByteBuffer(nBuf);
        } else {
            workerThread.setByteBuffer(ByteBufferFactory.allocateView(false));
        }
        
        // Reset the byteBuffer to the same position it was before
        // parsing the request line.
        protocolParser.releaseBuffer();
        ctx.setAttribute(BYTE_BUFFER,byteBuffer);
        ctx.setAttribute(INVOKE_NEXT,"true");
        
        // We are ready to execute on a new thread so tell
        // other filter to not register or cancel the key.
        ctx.setKeyRegistrationState(Context.KeyRegistrationState.NONE);
        try{
            NIOContext copyContext = (NIOContext) Cloner.clone(ctx);
            copyContext.setThreadPool(threadPool);
            copyContext.execute(copyContext.getProtocolChainContextTask());
        } catch (Throwable t){
            ctx.setAttribute(Context.THROWABLE,t);
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.CANCEL);
        }
        // Do not continue as the ctx.execute() will take care of it.
        return false;
    }

    
    /**
     * Delay the request processing.
     */
    private void delay(Context ctx){
        // This is sub optimal. Grizzly ARP should be used instead
        // tp park the request.
        try{
            Thread.sleep(delayValue);
        } catch (InterruptedException ex) {
            Controller.logger().
                    log(Level.SEVERE,"Delay exception",ex);
        }
    }
    
    
    /**
     * Filter the request and decide which thread pool to use.
     */
    public ExecutorService filterRequest(String token, ExecutorService p) {
        ExecutorService es = null;
        if (p instanceof ExtendedThreadPool) {
            ExtendedThreadPool threadPool = (ExtendedThreadPool) p;
            int maxThreads = threadPool.getMaximumPoolSize();
            
            // Set the number of dispatcher thread to 1.
            threadPool.setCorePoolSize(1);

            Double threadRatio = getAppropriateThreadRatio(token);
            boolean defaultThreadPool = false;
            if (threadRatio == null) {
                es = threadPools.get(ASTERISK_STRING);
                if (es != null){
                    return es;
                }
                // before setting the thread ratio to be leftRatio, checks the "*" ratio
                threadRatio = getAppropriateThreadRatio(ASTERISK_STRING);
                if(threadRatio == null) {
                    threadRatio = (leftRatio == 0 ? 0.5 : leftRatio);
                }
                defaultThreadPool = true;
            }

            int privilegedCount = (threadRatio == 1 ? maxThreads :
                (int) (maxThreads * threadRatio) + 1);

            es = newThreadPool(privilegedCount, p);
            if (defaultThreadPool){
                threadPools.put(ASTERISK_STRING, es);
            }
        }

        return es;
    }
    
    
    /**
     * Creates a new {@link ExecutorService}
     */
    protected ExecutorService newThreadPool(int threadCount, ExecutorService p) {
        if (threadCount == 0){
            return null;
        }
        DefaultThreadPool threadPool = new DefaultThreadPool();
        threadPool.setCorePoolSize(1);
        threadPool.setMaximumPoolSize(threadCount);
        threadPool.setName("RCM_" + threadCount);
        threadPool.start();
        return threadPool;
    }
    
    
    /**
     * Check to see if the privileged thread pool are in-use right now.
     */
    protected boolean isThreadPoolInUse(){
        Collection<ExecutorService> collection = threadPools.values();
        for (ExecutorService threadPool: collection){
            if (threadPool instanceof ExtendedThreadPool) {
                ExtendedThreadPool extThreadPool =
                        (ExtendedThreadPool) threadPool;
                if (extThreadPool.getQueueSize() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
    
    
    /***
     * Get the context-root from the {@link ByteBuffer}
     */
    protected String getContextRoot(ByteBuffer byteBuffer){
        // (1) Get the token the Algorithm has processed for us.
        byte[] chars = new byte[byteBuffer.limit() - byteBuffer.position()];
        
        byteBuffer.get(chars);
        
        String token = new String(chars);

        // Remove query string.
        int index = token.indexOf(QUERY_STRING);
        if ( index != -1){
            token = token.substring(0,index);
        }
        
        boolean slash = token.endsWith(PATH_STRING);
        if ( slash ){
            token = token.substring(0,token.length() -1);
        }
        return token;
    }
    
    
    /**
     * Close the connection.
     */
    public void closeConnection(Context ctx){
        ctx.setKeyRegistrationState(Context.KeyRegistrationState.CANCEL);
    }

    
    /**
     * Return a new <code>ProtocolParser>/code> or a cached 
     */
    public ProtocolParser newProtocolParser() {
        ProtocolParser protocolParser = protocolParserCache.poll();
        if (protocolParser == null){
            protocolParser = new ProtocolParser() {
                private ByteBuffer byteBuffer;
                private boolean isExpectingMoreData = false;
                private int nextStartPosition;
                private int nextEndPosition;
                
                public boolean hasMoreBytesToParse() {
                    return false;
                }
                
                public boolean isExpectingMoreData() {
                    return isExpectingMoreData;
                }
                
                public Object getNextMessage() {
                    return null;
                }

                public boolean hasNextMessage() {
                    if (byteBuffer ==null || !byteBuffer.hasRemaining()){
                        isExpectingMoreData = true;
                        return false;
                    }
                    
                    int state =0;
                    int start =0;
                    int end = 0;
                    
                    try {
                        byte c;
                        
                        // Rule b - try to determine the context-root
                        while(byteBuffer.hasRemaining()) {
                            c = byteBuffer.get();
                            
                            // State Machine
                            // 0 - Search for the first SPACE ' ' between the method and the
                            //     the request URI
                            // 1 - Search for the second SPACE ' ' between the request URI
                            //     and the method
                            switch(state) {
                                case 0: // Search for first ' '
                                    if (c == 0x20){
                                        state = 1;
                                        start = byteBuffer.position();
                                    }
                                    break;
                                case 1: // Search for next ' '
                                    if (c == 0x20){
                                        end = byteBuffer.position() - 1;
                                        byteBuffer.position(start);
                                        byteBuffer.limit(end);
                                        isExpectingMoreData = false;
                                        return true;
                                    }
                                    break;
                                default:
                                    throw new IllegalArgumentException("Unexpected state");
                            }
                        }
                        isExpectingMoreData = true;
                    } catch (BufferUnderflowException bue) {
                        isExpectingMoreData = true;
                    }
                    
                    return false;
                }

                public void startBuffer(ByteBuffer bb) {
                    nextStartPosition = bb.position();
                    nextEndPosition = bb.limit();

                    bb.flip();
                    byteBuffer = bb;
                }

                public boolean releaseBuffer() {
                    byteBuffer.limit(nextEndPosition);
                    byteBuffer.position(nextStartPosition);
                    
                    return false;
                }
            };
        }
        return protocolParser;
    }

    /**
     * Returns an appropriate thread pool with corresponding to a given token
     *
     * ex) Assuming that <code>threadPools</code> have 3 key entries as (1)"/examples", (2)"/examples/index.html"and (3)"/foo/bar",
     *     if a given param is "/examples/index.jsp", returns (1).
     *     if a given param is "/examples/index.html", returns (2).
     *     if a given param is "/foo/bar", returns (3).
     *     if a given param is "/foo/bar/index.html", returns (3).
     *     if a given param is "/foo/bar2", returns <code>null</code>.
     *  
     * @param token a request uri. i.g. /examples/index.html or /examples
     * @return if found, the appropriate thread pool is returned. if not found, <code>null</code> is returned.
     */
    private static ExecutorService getAppropriateThreadPool( String token ) {
        if( token == null || token.length() == 0 )
            return null;
        ExecutorService threadPool = null;
        threadPool = threadPools.get( token );
        if( threadPool == null ) {
            int index = token.lastIndexOf( PATH_STRING );
            if( index > 0 )
                return getAppropriateThreadPool( token.substring( 0, index ) );
        }
        return threadPool;
    }

    /**
     * Returns an appropriate thread ratio with corresponding to a given token
     *
     * ex) similar to {@link #getAppropriateThreadPool(String)}
     *
     * @param token a request uri. i.g. /examples/index.html or /examples
     * @return if found, the appropriate thread ratio is returned. if not found, <code>null</code> is returned.
     */
    private static Double getAppropriateThreadRatio( String token ) {
        if( token == null || token.length() == 0 )
            return null;
        Double threadRatio = null;
        threadRatio = privilegedTokens.get( token );
        if( threadRatio == null ) {
            int index = token.lastIndexOf( PATH_STRING );
            if( index > 0 )
                return getAppropriateThreadRatio( token.substring( 0, index ) );
        }
        return threadRatio;
    }
}
