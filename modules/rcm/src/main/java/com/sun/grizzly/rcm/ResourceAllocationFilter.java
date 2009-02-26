/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.rcm;

import com.sun.grizzly.Context;
import com.sun.grizzly.Controller;
import com.sun.grizzly.Controller.Protocol;
import com.sun.grizzly.DefaultPipeline;
import com.sun.grizzly.Pipeline;
import com.sun.grizzly.ProtocolChainContextTask;
import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.filter.ParserProtocolFilter;
import com.sun.grizzly.util.ByteBufferFactory;
import com.sun.grizzly.util.ByteBufferInputStream;
import com.sun.grizzly.util.WorkerThread;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * This ProtocolFilter is an implementation of a Resource Consumption Management
 * (RCM) system. RCM system are allowing you to enable virtualization of system
 * resources per web application, similar to Solaris 10 Zone or the outcome of
 * the upcoming JSR 284.
 *
 * This ProtocolFiler uses a <code>ProtocolParser</code> to determine which
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
    
    private final static String DELAY_VALUE = "com.sun.grizzly.rcm.delay";
    
    protected final static String QUERY_STRING="?";
    protected final static String PATH_STRING="/";
    
    
    public final static String BYTEBUFFER_INPUTSTREAM = "bbInputStream";
    
    public final static String INVOKE_NEXT = "invokeNextFilter";
    
    
    /**
     * The <code>Pipeline</code> configured based on the
     * <code>threadRatio</code>. This <code>Pipeline</code> is only used
     * by privileged application.
     */
    protected final static ConcurrentHashMap<String,Pipeline> pipelines
            = new ConcurrentHashMap<String,Pipeline>();
    
    
    /**
     * The list of privileged token used to decide if a request can be
     * serviced by the privileged <code>Pipeline</code>.
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
    private static long delayValue = 5 * 1000;
    
    
    /**
     * Cache the current statefull ProtocolParser.
     */
    private ConcurrentLinkedQueue<ProtocolParser> protocolParserCache
            = new ConcurrentLinkedQueue<ProtocolParser>();
    
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
                        tokenValue = Double.valueOf(tokens.substring(index+1));
                        privilegedTokens.put
                                (tokens.substring(0, index),tokenValue);
                        countRatio += tokenValue;
                    }
                }
                if ( countRatio > 1 ) {
                    System.out.println("Thread ratio too high. The total must be lower or equal to 1");
                }  else {
                    leftRatio = 1 - countRatio;
                }
            }
        } catch (Exception ex){
            System.out.println("Unable to parse thread ratio");
        }
        
        if (System.getProperty(ALLOCATION_MODE) != null){
            allocationPolicy = System.getProperty(ALLOCATION_MODE);
            if ( !allocationPolicy.equals(RESERVE) &&
                    !allocationPolicy.equals(CEILING) ){
                System.out.println("Invalid allocation policy");
                allocationPolicy = RESERVE;
            }
        }
    }
    
    
    public ResourceAllocationFilter() {
    }
    
    
    /**
     * Invoke the <code>ProtocolParser</code>. If more bytes are required,
     * register the <code>SelectionKey</code> back to its associated
     * <code>SelectorHandler</code>
     * @param ctx the Context object.
     * @return <tt>true</tt> if no more bytes are needed.
     */
    @Override
    public boolean invokeProtocolParser(Context ctx, 
            ProtocolParser protocolParser){
        
        // We already executed this one, so pass control to the next filter.
        if (ctx.getAttribute(INVOKE_NEXT) != null){
            return true;
        }
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
        while (leftRatio == 0 && privilegedTokens.get(token) == null) {
            if ( allocationPolicy.equals(RESERVE) ){
                delay(ctx);
                delayCount++;
            } else if ( allocationPolicy.equals(CEILING) ) {
                
                // If true, then we need to wait for free space. If false, then
                // we can go ahead and let the task execute with its default
                // pipeline
                if (isPipelineInUse()){
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
        Pipeline pipeline = pipelines.get(token);
        if (pipeline == null){
            pipeline = filterRequest(token, ctx.getPipeline());
            pipelines.put(token,pipeline);
        }
        ctx.setPipeline(pipeline);
        
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
            ctx.execute(ProtocolChainContextTask.poll());
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
     * Filter the request and decide which Pipeline to use.
     */
    public Pipeline filterRequest(String token, Pipeline p){
        int maxThreads = p.getMaxThreads();
        
        Double threadRatio = privilegedTokens.get(token);
        if (threadRatio == null) {
            threadRatio = (leftRatio == 0? 0.5:leftRatio);
        }
        
        int privilegedCount  = (threadRatio==1 ? maxThreads :
            (int) (maxThreads * threadRatio) + 1);
        
        return newPipeline(privilegedCount,p);
    }
    
    
    /**
     * Creates a new <code>Pipeline</code>
     */
    protected Pipeline newPipeline(int threadCount,Pipeline p){
        if (threadCount == 0){
            return null;
        }
        Pipeline pipeline = new DefaultPipeline();
        pipeline.setMinThreads(1);
        pipeline.setMaxThreads(threadCount);
        pipeline.setName("RCM_" + threadCount);
        pipeline.initPipeline();
        pipeline.startPipeline();
        return pipeline;
    }
    
    
    /**
     * Check to see if the privileged pipeline are in-use right now.
     */
    protected boolean isPipelineInUse(){
        Collection<Pipeline> collection = pipelines.values();
        for (Pipeline pipeline: collection){
            if (pipeline.size() > 0) {
                return true;
            }
        }
        return false;
    }
    
    
    /***
     * Get the context-root from the <code>ByteBuffer</code>
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
                    if (!byteBuffer.hasRemaining()){
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
                                        start = byteBuffer.position() + 1;
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
}
