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

package com.sun.grizzly.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @deprecated please use {@link GrizzlyExecutorService#createInstance} instead.
 * @author gustav trede
 */
@Deprecated 
public class DefaultThreadPool extends FixedThreadPool{

    /**
     *
     */
    public DefaultThreadPool() {
        this("Grizzly", DEFAULT_MIN_THREAD_COUNT, DEFAULT_MAX_THREAD_COUNT,
                DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    /**
     *
     * @param name 
     * @param corePoolsize
     * @param maxPoolSize
     * @param keepAliveTime
     * @param timeUnit {@link TimeUnit}
     */
    public DefaultThreadPool(String name, int corePoolsize,
            int maxPoolSize, long keepAliveTime, TimeUnit timeUnit){
        this(name, corePoolsize, maxPoolSize, keepAliveTime, timeUnit, null);
    }

    /**
     *
     * @param name
     * @param corePoolsize
     * @param maxPoolSize
     * @param keepAliveTime
     * @param timeUnit  {@link TimeUnit}
     * @param threadFactory {@link ThreadFactory}
     */
    public DefaultThreadPool(String name, int corePoolsize,
            int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
            ThreadFactory threadFactory) {
            this(name, corePoolsize, maxPoolSize, keepAliveTime, timeUnit,
                   threadFactory,DataStructures.getLTQinstance(Runnable.class));
    }

    /**
     *
     * @param name 
     * @param corePoolsize
     * @param maxPoolSize
     * @param keepAliveTime
     * @param timeUnit {@link TimeUnit}
     * @param threadFactory {@link ThreadFactory}
     * @param workQueue {@link BlockingQueue}
     */
    public DefaultThreadPool(String name, int corePoolsize,int maxPoolSize,
            long keepAliveTime, TimeUnit timeUnit, ThreadFactory threadFactory,
            BlockingQueue<Runnable> workQueue) {
        super(name,maxPoolSize,workQueue,threadFactory,null);
        this.corePoolSize = 0;
        this.keepAliveTime = TimeUnit.MILLISECONDS.convert(keepAliveTime, timeUnit);       
    }

    public void start(){
    }

    @Override
    public String toString() {
        return super.toString()+
        ", min-threads="+getCorePoolSize()+
        ", max-threads="+getMaximumPoolSize();
    }
}
