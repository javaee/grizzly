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
package com.sun.grizzly.comet.concurrent;

import com.sun.grizzly.Controller;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.comet.DefaultNotificationHandler;
import java.io.IOException;
import java.util.LinkedList;
import java.util.logging.Logger;

/**
 * we queue events in each comethandler to lower the probability
 * that slow or massive IO for one comethandler severly delays events to others.<br>
 * <br>
 * only streaming mode can benefit from buffering messages like this.  <br>
 * only 1 thread at a time is allowed to do IO,
 * other threads put events in the queue and return to the thread pool.<br>
 *<br>
 * a thread initially calls enqueueEvent and stay there until there are no more
 * events in the queue, calling the onEVent method in synchronized context for each Event.<br>
 *<br>
 * on IOE in onEvent we terminate.<br>
 * we have a limit, to keep memory usage under control.<br>
 * <br>
 * if queue limit is reached onQueueFull is called, and then we terminate.<br>
 *<br>
 *<br>
 * whats not optimal is that a worker thread is sticky to the client depending
 * uppon available events in the handlers local queue,
 * that can in theory allow a few clients to block all threads for extended time.<br>
 * that effect can make this implementation unusable depending on the scenario,
 * its not a perfect design be any means.
 * <br>
 * The potential improvement is that only 1 worker thread is tied up to a client instead of several
 *  being blocked by synchronized io wait for one comethandler .<br>
 *
 * @author Gustav Trede 
 */
public abstract class DefaultConcurrentCometHandler<E> implements CometHandler<E>{

    protected final static Logger logger = Controller.logger();

    /**
     *  used for preventing othe worker threads from the executor event queue from adding events
     *  to the comethandlers local queue or starting IO logic after shuttdown.<br>
     */
    private  boolean shuttingdown;

    /**
     *  max number of events to locally queue for this comethandler.<br>
     * (a global event queue normally exsists in form of a threadpool too)
     */
    private final int messageQueueLimit;

    /**
     *  current queue size
     */
    private int queuesize;

    /**
     *  true means that no thread is currently active on this comethandlers queue logic
     */
    private boolean isreadyforwork = true;
    
    /**
     * todo replace with non array copying list for non rezing add situations, 
     * using internal index to keep track of state , not a linked list,
     * it has  too much overhead and eats memory.
     */
    protected final LinkedList<CometEvent> messageQueue = new LinkedList<CometEvent>();

    protected E attachment;

    public DefaultConcurrentCometHandler() {
        this(100);
    }
    
    /**
     *
     * @param messageQueueLimit
     */
    public DefaultConcurrentCometHandler(int messageQueueLimit) {
        this.messageQueueLimit = messageQueueLimit;
    }
    
    /**
     *  enqueues event if another thread is currently working on this handler.<br>
     * the first thread to start working will keep doing so until there are no
     *  further events in the internal queue.
     */
    public void EnQueueEvent(CometEvent event){
        synchronized(messageQueue){
            if (!isreadyforwork){
                if (!shuttingdown && queuesize < messageQueueLimit){
                    messageQueue.add(event);
                    queuesize++;
                }
                return;
             }
             isreadyforwork = false;
        }
         
        boolean queuefull = false;
        while(!shuttingdown){
            if(!event.getCometContext().isActive(this)){
                shuttingdown = true;
                return; 
            }
            try{
                //move synchronized outside the while loop ?
                synchronized(this){
                    onEvent(event);
                }
            } catch (IOException ex) {
                shuttingdown = true;
            }finally{
                if (shuttingdown){
                    event.getCometContext().resumeCometHandler(this);
                    return;                    
                }                
            }

            synchronized(messageQueue){
                if (queuesize == messageQueueLimit){
                    queuefull = true;
                }else
                if (queuesize == 0){
                    isreadyforwork = true;
                    return;
                }else{
                    event = messageQueue.poll();
                    queuesize--;
                }
            }
            if (queuefull){
                shuttingdown = true;
                onQueueFull(event); //todo is onqeuefull needed ? or just terminate, it would simplify to just terminate =)
            }
        }
    }

    /**
     * called in synchronized context.
     * when the comethandler's local event queue is full.<br>
     * default impl resumes the comethandler
     * @param event {@link CometEvent}
     */
    public void onQueueFull(CometEvent event){
        event.getCometContext().resumeCometHandler(this);
    }
    
    /**
     * returns the attachment
     */
    public E attachment() {
        return attachment;
    }

    /**
     * {@inheritDoc}
     */
    //@Override // cant use override as long as we are compiling on jdk 1.5
    public void attach(E attachment) {
        this.attachment = attachment;
    }


    /**
     * {@inheritDoc}
     * <br>
     * default impl calls terminate()
     */
    //@Override // cant use override as long as we are compiling on jdk 1.5
    public void onInterrupt(CometEvent event) throws IOException {
        terminate();
    }   

    /**
     * {@inheritDoc}
     * <br>
     * default impl calls terminate()
     */
    //@Override // cant use override as long as we are compiling on jdk 1.5
    public void onTerminate(CometEvent event) throws IOException {
        terminate();
    }

    /**
     *  
     */
    protected void terminate(){
       /* if (attachment() instanceof Closeable){
            try {
                ((Closeable) attachment()).close();
            } catch (IOException ex) { }
        }*/
    }

}