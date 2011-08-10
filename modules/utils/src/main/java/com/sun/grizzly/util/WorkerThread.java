/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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
import javax.net.ssl.SSLEngine;

/**
 * Simple interface to allow the addition of <code>Thread</code> attributes.
 *
 * @author Jean-Francois Arcand
 */
public abstract class WorkerThread extends Thread {


    /**
     * The {@link ByteBuffer} used when {@link Task} are executed.
     */
    protected ByteBuffer byteBuffer;
    
    
    /**
     * The encrypted ByteBuffer used for handshaking and reading request bytes.
     */
    protected ByteBuffer inputBB;


    /**
     * The encrypted ByteBuffer used for handshaking and writing response bytes.
     */
    protected ByteBuffer outputBB;


    /**
     * The{@link SSLEngine} used to manage the SSL over NIO request.
     */
    protected SSLEngine sslEngine;

    public WorkerThread() {
    }
    
    public WorkerThread(String name) {
        super(name);
    }

    public WorkerThread(Runnable target) {
        super(target);
    }

    public WorkerThread(Runnable target, String name) {
        super(target, name);
    }

    public WorkerThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public WorkerThread(ThreadGroup group, Runnable target) {
        super(group, target);
    }

    public WorkerThread(ThreadGroup group, Runnable target, String name) {
        super(group, target, name);
    }

    public WorkerThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, target, name, stackSize);
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
     * Updates Thread associated attachment according to the passed mode.
     * 
     * @return updated ThreadAttachment
     */
    public abstract ThreadAttachment updateAttachment(int mode);

    
    /**
     * Get the current set of attributes (state) associated with this instance.
     * Unlike detach(), this method doesn't clear the WorkerThread attributes.
     * 
     * @return the Thread associated ThreadAttachment
     */
    public abstract ThreadAttachment getAttachment();

    
    /**
     * Detach the current set of attributes (state) associated with this instance.
     * Method will re-create all the ByteBuffers associated with this thread.
     * 
     * @return a new ThreadAttachment
     */
    public abstract ThreadAttachment detach();
    
    
    /**
     * Attach the ThreadAttachment to this instance. This will configure this
     * Thread attributes like ByteBuffer, SSLEngine, etc.
     * @param ThreadAttachment the attachment.
     */
    public abstract void attach(ThreadAttachment threadAttachment);
    
    public void reset() {
        if (byteBuffer != null) {
            byteBuffer.clear();
        }
        
        if (inputBB != null) {
            inputBB.clear();
        }
        
        if (outputBB != null) {
            // make sure outputBB doesn't have any available data
            outputBB.position(0);
            outputBB.limit(0);
        }
        
        sslEngine = null;
    }
}
