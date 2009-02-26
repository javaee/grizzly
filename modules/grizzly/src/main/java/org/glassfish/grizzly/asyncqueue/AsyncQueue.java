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
package org.glassfish.grizzly.asyncqueue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.glassfish.grizzly.util.LinkedTransferQueue;

/**
 * Class represents common implementation of asynchronous processing queue.
 *
 * @author Alexey Stashok
 */
public class AsyncQueue<E> {

    /**
     * The queue of tasks, which will be processed asynchrounously
     */
    protected LinkedTransferQueue<E> queue;
    /**
     * Current processing task
     */
    protected AtomicReference<E> currentElement;
    /**
     * Locker object, which could be used by a queue processors
     */
    protected ReentrantLock queuedActionLock;

    public AsyncQueue() {
        queue = new LinkedTransferQueue<E>();
        currentElement = new AtomicReference<E>();
        queuedActionLock = new ReentrantLock();
    }

    /**
     * Get the current processing task
     * @return the current processing task
     */
    public AtomicReference<E> getCurrentElement() {
        return currentElement;
    }

    /**
     * Set the current processing task
     * @param currentElement the current processing task
     */
    public void setCurrentElement(AtomicReference<E> currentElement) {
        this.currentElement = currentElement;
    }

    /**
     * Get the queue of tasks, which will be processed asynchrounously
     * @return the queue of tasks, which will be processed asynchrounously
     */
    public LinkedTransferQueue<E> getQueue() {
        return queue;
    }

    /**
     * Set the queue of tasks, which will be processed asynchrounously
     * @param queue the queue of tasks, which will be processed asynchrounously
     */
    public void setQueue(LinkedTransferQueue<E> queue) {
        this.queue = queue;
    }

    /**
     * Get the locker object, which could be used by a queue processors
     * @return the locker object, which could be used by a queue processors
     */
    public ReentrantLock getQueuedActionLock() {
        return queuedActionLock;
    }

    /**
     * Set the locker object, which could be used by a queue processors
     * @param queuedActionLock the locker object, which could be used by a
     *        queue processors
     */
    public void setQueuedActionLock(ReentrantLock queuedActionLock) {
        this.queuedActionLock = queuedActionLock;
    }
}