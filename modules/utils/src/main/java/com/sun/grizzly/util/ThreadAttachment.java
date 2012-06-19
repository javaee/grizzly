/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLEngine;

/**
 * This object represent the state of a {@link WorkerThread}. This include
 * the ByteBuffer binded to the WorkerThread, application data etc.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class ThreadAttachment extends SelectionKeyActionAttachment
        implements AttributeHolder {

    /**
     * The maximum time this object can be associated with an active {@link Thread}
     */
    private long transactionTimeout = UNSET_TIMEOUT;

    public static class Mode {
        public static final int ATTRIBUTES_ONLY = 0;
        public static final int BYTE_BUFFER = 2;
        public static final int INPUT_BB = 4;
        public static final int OUTPUT_BB = 8;
        public static final int SECURE_BUFFERS = 12;
        public static final int SSL_ENGINE = 16;
        public static final int SSL_ARTIFACTS = 28;
        public static final int STORE_ALL = 31;
    }

    private final ReentrantLock threadLock = new ReentrantLock();

    private String threadName;


    private Map<String, Object> attributes;


    private ByteBuffer byteBuffer;


    /**
     * The encrypted ByteBuffer used for handshaking and reading request bytes.
     */
    private ByteBuffer inputBB;


    /**
     * The encrypted ByteBuffer used for handshaking and writing response bytes.
     */
    private ByteBuffer outputBB;


    /**
     * The{@link SSLEngine} used to manage the SSL over NIO request.
     */
    private SSLEngine sslEngine;

    /**
     * ThreadAttachment store mode
     */
    private int mode;


    /**
     * The current {@link Thread} used to execute this instance.
     */
    private Thread activeThread = null;


    public ThreadAttachment(){
        attributes = new HashMap<String,Object>();
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }


    public void setAttribute(String key, Object value){
        attributes.put(key,value);
    }


    public Object getAttribute(String key){
        return attributes.get(key);
    }


    public Object removeAttribute(String key){
        return attributes.remove(key);
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * Set the {@link ByteBuffer} shared this thread
     */
    public void setByteBuffer(ByteBuffer byteBuffer){
        this.byteBuffer = byteBuffer;
    }


    /**
     * Return the {@link ByteBuffer} shared this thread
     */
    public ByteBuffer getByteBuffer(){
        return byteBuffer;
    }


    /**
     * Return the encrypted {@link ByteBuffer} used to handle request.
     * @return {@link ByteBuffer}
     */
    public ByteBuffer getInputBB(){
        return inputBB;
    }


    /**
     * Set the encrypted {@link ByteBuffer} used to handle request.
     * @param inputBB {@link ByteBuffer}
     */
    public void setInputBB(ByteBuffer inputBB){
        this.inputBB = inputBB;
    }


    /**
     * Return the encrypted {@link ByteBuffer} used to handle response.
     * @return {@link ByteBuffer}
     */
    public ByteBuffer getOutputBB(){
        return outputBB;
    }


    /**
     * Set the encrypted {@link ByteBuffer} used to handle response.
     * @param outputBB {@link ByteBuffer}
     */
    public void setOutputBB(ByteBuffer outputBB){
        this.outputBB = outputBB;
    }


    /**
     * Set the{@link SSLEngine}.
     * @return{@link SSLEngine}
     */
    public SSLEngine getSSLEngine() {
        return sslEngine;
    }


    /**
     * Get the{@link SSLEngine}.
     * @param sslEngine{@link SSLEngine}
     */
    public void setSSLEngine(SSLEngine sslEngine) {
        this.sslEngine = sslEngine;
    }


    /**
     * Return the name of the Thread on which this instance is binded.
     */
    public String getThreadId() {
        return threadName;
    }


    /**
     * Set the Thread's name on which this instance is binded.
     */
    public void setThreadId(String threadName) {
        this.threadName = threadName;
    }

    /**
     * Associates ThreadAttachment with the current thread
     */
    public void associate() {
        if (!threadLock.isHeldByCurrentThread()) {
            threadLock.lock();
            activeThread = Thread.currentThread();
        }
    }

    /**
     * Releases ThreadAttachment association with the current thread
     */
    public void deassociate() {
        if (threadLock.isHeldByCurrentThread()) {
            activeThread = null;
            threadLock.unlock();
        }
    }

    /**
     * SelectionKey attachment processing
     * @param selectionKey
     */
    public void process(SelectionKey selectionKey) {
        ((WorkerThread) Thread.currentThread()).attach(this);
    }

    /**
     * SelectionKey attachment postProcessing
     * @param selectionKey
     */
    public void postProcess(SelectionKey selectionKey) {
        ((WorkerThread) Thread.currentThread()).detach();
    }

    public void reset() {
        mode = Mode.ATTRIBUTES_ONLY;
        byteBuffer = null;
        sslEngine = null;
        inputBB = null;
        outputBB = null;
        transactionTimeout = UNSET_TIMEOUT;
    }

    @Override
    public void release(SelectionKey selectionKey) {
        attributes.clear();
        reset();

        deassociate();
        super.release(selectionKey);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("ThreadAttachment[mode=").append(mode);
        sb.append(", threadName=").append(threadName);
        sb.append(", byteBuffer=").append(byteBuffer);
        sb.append(", timeout=").append(getTimeout());
        sb.append(", sslEngine=").append(sslEngine);
        sb.append(", inputBB=").append(inputBB);
        sb.append(", outputBB=").append(outputBB);
        sb.append(", attributes=");
        if (threadLock.tryLock()) {
            sb.append(attributes);
            threadLock.unlock();
        } else {
            sb.append("inaccessible");
        }
        
        sb.append(']');
        return sb.toString();
    }

    /**
     * Return the current {@link Thread} which is executing this object.
     * @return the current {@link Thread} which is executing this object.
     */
    public Thread activeThread(){
        return activeThread;
    }

    /**
     * Return the time, in milliseconds, this object can be attached to a {@link Thread}
     * @return the time, in milliseconds, this object can be attached to a {@link Thread}
     */
    public long getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * Set the time, in milliseconds, this object can be attached to a {@link Thread}
     * @param transactionTimeout the time, in milliseconds, this object can be attached to a {@link Thread}
     */
    public void setTransactionTimeout(long transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    @Override
    public void setIdleTimeoutDelay(long idleTimeoutDelay) {
        super.setIdleTimeoutDelay(idleTimeoutDelay);
    }


    @Override
    public long getIdleTimeoutDelay() {
        if (isNotSetTimeout(idleTimeoutDelay)) { // the most often case, if async is not used
            return transactionTimeout;
        } else if (isNotSetTimeout(transactionTimeout)) {
            return idleTimeoutDelay;
        }

        if (transactionTimeout >= 0 && idleTimeoutDelay >= 0) {
            return Math.min(transactionTimeout, idleTimeoutDelay);
        } else if (idleTimeoutDelay >= 0) {
            return idleTimeoutDelay;
        } else {
            return transactionTimeout;
        }
    }

    protected static boolean isUnlimitedTimeout(long timeout) {
        return timeout == SelectionKeyAttachment.UNLIMITED_TIMEOUT;
    }

    protected static boolean isNotSetTimeout(long timeout) {
        return timeout == SelectionKeyAttachment.UNSET_TIMEOUT;
    }
}
