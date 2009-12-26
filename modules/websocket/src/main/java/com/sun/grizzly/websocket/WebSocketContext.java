/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2009 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.websocket;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TODO: jdoc
 * @author gustav trede
 */
public class WebSocketContext {

    public final static int TIMEOUTDISABLEDVALUE = 0;

    protected final static int memOverheadPerReadFrame = 100;

    protected final static int maxallowedReadOrWriteQueueBytes = 1<<30;

    /**
     * 2 bytes is needed for an UTF8 or binary data frame without user data.
     */
    protected final static int mindataframelengthneededbyprotocol = 2;
    
    protected final static int maxalloweddataframelength = 1<<29;

    protected final static int initialReadBufferLengthMINVALUE = 16384;

    protected final static int handshakeTimeOutSecondsDefaultValue = 30;

    protected final static int idleTimeoutSecondsDefaultValue = 300;

    private final static ConcurrentHashMap<String,WebSocketContext> contexts
            = new ConcurrentHashMap<String, WebSocketContext>();


    private final String resourcepath;

    /**
       The |WebSocket-Protocol| header is used in the WebSocket handshake.
       It is sent from the client to the server and back from the server to
       the client to confirm the subprotocol of the connection.  This
       enables scripts to both select a subprotocol and be sure that the
       server agreed to serve that subprotocol.
    */
    private final String protocol;

    protected volatile WebSocketListener eventlistener;

    /**
     * Socket connect is not included in this value.<br>
     * Clientside this value includes everything after socket connect
     * (SSL and websocket protocol handshake) until state is open.<br>
     * Serverside this value currently only includes websocket handshake
     * response write.<br>
     * Changing this value will not affect existing connections, only new ones.
     */
    private volatile int handshakeTimeoutSeconds;

    /**
     * This value is used when websocket state is open (all handshake is done).
     * Changing this value will not affect existing connections, only new ones.
     */
    private volatile int idleTimeoutSeconds;

    /**
     * Changing this value will not affect existing connections, only new ones.
     */
    private volatile int initialReadBufferLength;

    /**
     * This value limits the max allowed length for {@link DataFrame}s in both
     * read and send.<br>
     * The value includes the protocol overhead bytes.<br>
     * Changes to this value will propagate immediatly to existing connections.
     * Lowering this value may cause problems in ongoing IO operations that will
     * result closed connections.
     */
    protected volatile int maxDataFramelengthBytes;

    /**
     * Limits the send queue per WebSocket, see
     * {@link WebSocket#getBufferedSendBytes} .<br>
     * Changes to this value will propagate immediatly to existing connections.
     * Lowering this value may cause problems for ongoing write operations that
     * will result in closed connections.
     */
    private volatile int dataFrameSendQueueLimit;

    /**
     * Limits the send queue per WebSocket, see
     * {@link WebSocket#getBufferedSendBytes} .<br>
     */
    protected volatile int dataFrameSendQueueLimitBytes;

    /**
     * Changes to this value will propagate immediatly to existing connections.
     * Lowering this value may cause problems for ongoing write operations that
     * will result in closed connections.
     */
    private volatile int dataFrameReadQueueLimit;

    protected volatile int dataFrameReadQueueLimitBytes;

    /**
     * Must be atomic, websockets can exist in several selectorthreads.
     */
    protected final AtomicInteger readDataThrottledCounter =new AtomicInteger();

    protected final AtomicInteger writeDataThrottledCounter=new AtomicInteger();


    /*
     * True means {@link WebSocketListener#onMessage} will run in non selector
     * thread.    
    protected volatile boolean doOnMessageEventsInThreadPool; */

    /**
     * True means {@link WebSocketListener#onClosed} will run in non selector
     * thread.
     */
    protected volatile boolean doOnCloseEventsInThreadPool;

    /**
     * Total number of {@link WebSocketListener} method calls that failed
     * by emitting a Throwable.
     */
    protected final AtomicInteger eventListenerExceptionCounter
            = new AtomicInteger();

    private final boolean secureOnly;
    private final boolean secureEnabled;

    protected WebSocketContext(
           String resourcepath,String protocol,WebSocketListener eventlistener){
        this(resourcepath, protocol, eventlistener, false, true);
    }

    private WebSocketContext(String resourcepath, String protocol,
            WebSocketListener eventlistener,boolean secureonly,
            boolean secureEnabled) {
        validateResourcePath(resourcepath);
        if (eventlistener == null)
            throw new IllegalArgumentException("eventlistener is null");
        this.resourcepath  = resourcepath;
        this.protocol      = protocol;
        this.eventlistener = eventlistener;
        if (secureonly)
            secureEnabled = true;
        this.secureOnly    = secureonly;
        this.secureEnabled = secureEnabled;
        setDefaultValues();
    }

    private void setDefaultValues(){
        setDataFrameLimits(8192, 16, 64);
        setInitialReadBufferLength(initialReadBufferLengthMINVALUE);
        setIdleTimeoutSeconds(idleTimeoutSecondsDefaultValue);
        setHandshakeTimeoutSeconds(handshakeTimeOutSecondsDefaultValue);
        doOnCloseEventsInThreadPool   = true;
    }

    protected WebSocketContext copySomeSettingsFrom(WebSocketContext ctx){
        this.doOnCloseEventsInThreadPool   = ctx.doOnCloseEventsInThreadPool;
        this.initialReadBufferLength       = ctx.initialReadBufferLength;
        this.maxDataFramelengthBytes       = ctx.maxDataFramelengthBytes;
        this.dataFrameSendQueueLimit       = ctx.dataFrameSendQueueLimit;
        this.dataFrameSendQueueLimitBytes  = ctx.dataFrameSendQueueLimitBytes;
        this.dataFrameReadQueueLimit       = ctx.dataFrameReadQueueLimit;
        this.dataFrameReadQueueLimitBytes  = ctx.dataFrameReadQueueLimitBytes;
        //this.handshakeTimeoutSeconds       = ctx.handshakeTimeoutSeconds;
        //this.idleTimeoutSeconds            = ctx.idleTimeoutSeconds;
        return this;
    }

    protected int getInitialReadBufferLength() {
        return initialReadBufferLength;
    }

    protected void setInitialReadBufferLength(int initialReadBufferlength) {
        this.initialReadBufferLength =
                initialReadBufferlength < initialReadBufferLengthMINVALUE ?
            initialReadBufferLengthMINVALUE: initialReadBufferlength;
    }

    public int getReadDataThrottledCounter() {
        return readDataThrottledCounter.get();
    }

    public int getWriteDataThrottledCounter() {
        return writeDataThrottledCounter.get();
    }
    
    /**
     * TODO: add jdoc
     * @param maxDataFramelengthBytes max allowed size if dataframe for both
     * read and send/write operations.
     * @param dataFrameSendQueueLimit number of frames at max size per websocket
     * @param dataFrameReadQueueLimit number of frames at max size per websocket
     * @return 
     */
    public WebSocketContext setDataFrameLimits(
            final int maxDataFramelengthBytes,
            final int dataFrameSendQueueLimit,
            final int dataFrameReadQueueLimit) {
        
        if (maxDataFramelengthBytes < mindataframelengthneededbyprotocol)
            throw new IllegalArgumentException("maxDataFramelengthBytes < "+
                    mindataframelengthneededbyprotocol);
        if (maxDataFramelengthBytes > maxalloweddataframelength)
            throw new IllegalArgumentException("maxDataFramelengthBytes > "+
                    maxalloweddataframelength);
        if (dataFrameSendQueueLimit < 1)
            throw new IllegalArgumentException("dataFrameSendQueueLimit < 1");
        if (dataFrameReadQueueLimit < 1)
            throw new IllegalArgumentException("dataFrameReadQueueLimit < 1");
        long sq =(long)maxDataFramelengthBytes//+memOverheadPerQueuedSendFrame)
            * dataFrameSendQueueLimit;
        if (sq > maxallowedReadOrWriteQueueBytes)
            throw new IllegalArgumentException("Max allowed dataFrameSendQu" +
          "eueLimitBytes exceeded : "+sq+" > "+maxallowedReadOrWriteQueueBytes);
        long rq =((long)maxDataFramelengthBytes + memOverheadPerReadFrame)
                * dataFrameReadQueueLimit;
        if (rq > maxallowedReadOrWriteQueueBytes)
            throw new IllegalArgumentException("Max allowed dataFrameReadQu" +
          "eueLimitBytes exceeded : "+rq+" > "+maxallowedReadOrWriteQueueBytes);
        this.dataFrameSendQueueLimitBytes = (int) sq;
        this.dataFrameReadQueueLimitBytes = (int) rq;
        this.dataFrameSendQueueLimit = dataFrameSendQueueLimit;
        this.dataFrameReadQueueLimit = dataFrameReadQueueLimit;
        this.maxDataFramelengthBytes = maxDataFramelengthBytes;
        return this;
    }

    public int getMaxDataFramelengthBytes() {
        return maxDataFramelengthBytes;
    }

    /**
     * Max number of incoming {@link DataFrame}s at
     * {@link maxDataFramelengthBytes} (or a larger number of smaller that
     * has the rougly total memory usage )allowed to be queued
     * before reads are temporary halted.
     * For more info see {@link WebSocket#getBufferedOnMessageBytes}.
     * @return
     */
    public int getDataFrameReadQueueLimit() {
        return dataFrameReadQueueLimit;
    }

    /**
     * Max number of outgoing {@link DataFrame}s at
     * {@link #maxDataFramelengthBytes} (or a larger number of smaller that
     * has the rougly total GC preventable memory) allowed to be queued before 
     * connection is closed.<br>
     * For more info see {@link WebSocket#getBufferedSendBytes}.
     * @return
     */
    public int getDataFrameSendQueueLimit() {
        return dataFrameSendQueueLimit;
    }

    /**
     *
     * @return
     */
    public String getResourcepath() {
        return resourcepath;
    }

    /**
     * true if only secure connections is allowed.
     * @return
     */
    public boolean isSecureOnly() {
        return secureOnly;
    }

    public boolean isSecureEnabled() {
        return secureEnabled;
    }

    /*Sets the {@link WebSocketContext#doOnMessageEventsInThreadPool}
    public void setDoOnMessageEventsInThreadPool(
            boolean doOnMessageEventsInThreadPool) {
        this.doOnMessageEventsInThreadPool = doOnMessageEventsInThreadPool;
    }   
     * Returns the {@link WebSocketContext#doOnMessageEventsInThreadPool}
    public boolean isDoOnMessageEventsInThreadPoo(){
        return doOnMessageEventsInThreadPool;
    }*/

    /**
     * Sets the {@link WebSocketContext#doOnCloseEventsInThreadPool}
     * @param doOnCloseEventsInThreadPool
     */
    public void setDoOnCloseEventsInThreadPool(
            boolean doOnCloseEventsInThreadPool) {
        this.doOnCloseEventsInThreadPool = doOnCloseEventsInThreadPool;
    }

    /**
     * retuns the {@link WebSocketContext#doOnCloseEventsInThreadPool}
     * @return
     */
    public boolean isDoOnCloseEventsInThreadPool() {
        return doOnCloseEventsInThreadPool;
    }

    /**
     * Returns the {@link WebSocketContext#handshakeTimeoutSeconds}
     * @return
     */
    public int getHandshakeTimeoutSeconds() {
        return handshakeTimeoutSeconds;
    }

    /**
     * Sets the {@link WebSocketContext#handshakeTimeoutSeconds}
     * @param handshakeTimeoutSeconds
     */
    public void setHandshakeTimeoutSeconds(int handshakeTimeoutSeconds) {
        this.handshakeTimeoutSeconds = handshakeTimeoutSeconds <= 0 ?
                TIMEOUTDISABLEDVALUE : handshakeTimeoutSeconds;
    }

    /**
     * Returns the {@link WebSocketContext#idleTimeoutSeconds}
     * @return
     */
    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    /**
     * Sets the {@link WebSocketContext#idleTimeoutSeconds}
     * @param idleTimeoutSeconds
     */
    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds <= 0 ?
                TIMEOUTDISABLEDVALUE : idleTimeoutSeconds;
    }

    /**
       The |WebSocket-Protocol| header is used in the WebSocket handshake.
       It is sent from the client to the server and back from the server to
       the client to confirm the subprotocol of the connection.  This
       enables scripts to both select a subprotocol and be sure that the
       server agreed to serve that subprotocol.
     * @return
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Returns the {@link WebSocketListener}
     * @return
     */
    public WebSocketListener getEventlistener() {
        return eventlistener;
    }

    /**
     * Sets the {@link WebSocketListener} if the new value is not null.
     * @param eventlistener
     */
    public void setEventlistener(WebSocketListener eventlistener) {
        if (eventlistener == null){
            throw new IllegalArgumentException("eventlistener is null");
        }
        this.eventlistener = eventlistener;
    }

    /**
     * Returns {@link WebSocketContext#eventListenerExceptionCounter}
     * @return
     */
    public int getEventListenerExceptionCounter() {
        return eventListenerExceptionCounter.get();
    }

    /**
     * Returns null if no [@link WebScocketContext} exists for the name.
     * @param name
     * @return
     */
    public static WebSocketContext getWebScocketContext(String name){
        return contexts.get(name);
    }

    /**
     * Removes the [@link WebScocketContext} with the corresponding name and
     * returns the conect if it existed, null if not.
     * @param name
     * @return
     */
    public static WebSocketContext remove(String name){
        return contexts.remove(name);
    }

    /**
     * The returned collection is live, modifications to it
     * will be reflected in the backing datastructure.
     * @return
     */
    public static Map<String,WebSocketContext> getAll(){
        return contexts;
    }

    public static WebSocketContext create(String resourcepath,String protocol,
            WebSocketListener eventlistener,boolean secureonly,
            boolean secureEnabled)  throws IOException{
            return create(resourcepath, protocol, eventlistener, secureonly,
                    secureEnabled, handshakeTimeOutSecondsDefaultValue,
                    idleTimeoutSecondsDefaultValue);
    }

    public static WebSocketContext create(String resourcepath,String protocol,
            WebSocketListener eventlistener,boolean secureonly
            ,boolean secureEnabled,int handshakeTimeoutSeconds,
            int idleTimeoutSeconds) {
        WebSocketContext ctx = new WebSocketContext(
                resourcepath,protocol,eventlistener,secureonly,secureEnabled);
        ctx.setHandshakeTimeoutSeconds(handshakeTimeoutSeconds);
        ctx.setIdleTimeoutSeconds(idleTimeoutSeconds);
        if (contexts.putIfAbsent(resourcepath, ctx) == null){
            return ctx;
        }
        throw new IllegalArgumentException("WebSocketContext " +
                "resourcepath is already in use :"+resourcepath);
    }

    private static void validateResourcePath(String resourcepath){
        if (resourcepath == null)
            throw new IllegalArgumentException(
                    "websocketcontext resourcepath is null");
        if (resourcepath.length() == 0)
            throw new IllegalArgumentException(
                    "websocketcontext resourcepath length is 0");
        //todo add more checks
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
        +" resourcepath:"+resourcepath+" protocol:"+protocol
        +" doOnCloseEventsInThreadPool:"+doOnCloseEventsInThreadPool
      //+" doOnMessageEventsInThreadPool:"+doOnMessageEventsInThreadPool
        //+" doOnOpenEventsInThreadPool:"+doOnOpenEventsInThreadPool+"\r\n"
        +" eventListenerExceptionCounter:"+eventListenerExceptionCounter
        +" eventistener:"+eventlistener+"\r\n"
        +" initialReadBufferLengthBytes:"+initialReadBufferLength
        +" maxDataFramelengthBytes:"+maxDataFramelengthBytes+"\r\n"
        +" writeDataThrottledCounter:"+writeDataThrottledCounter
        +" dataFrameSendQueueLimit:"+dataFrameSendQueueLimit
        +" dataFrameSendQueueLimitBytes:"+dataFrameSendQueueLimitBytes+"\r\n"
        +" readDataThrottledCounter:"+readDataThrottledCounter
        +" dataFrameReadQueueLimit:"+dataFrameReadQueueLimit
        +" dataFrameReadQueueLimitBytes:"+dataFrameReadQueueLimitBytes+"\r\n"
        +" handshakeTimeoutSeconds:"+handshakeTimeoutSeconds
        +" idleTimeoutSeconds:"+idleTimeoutSeconds
        ;
    }

}
