/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.test.cachetest;

import java.util.Queue;
import java.util.concurrent.CountDownLatch;

/**
 *
 * @author Gustav Trede
 */
public class TestLogic implements Runnable{

    protected static Queue  cache;
    protected static CountDownLatch testfinished;
    private static TestAndResult test;
    private final InstanceFactory objfactory;
    
    /**
     * used to prevent hotspot in 1.7 from being too clever whith -XX:+AggressiveOpts
     */
    protected int    dummy;
    protected Object dummy2;

   // private final Random rand = new Random();

    public TestLogic(TestAndResult test) {
        TestLogic.test = test;
        this.objfactory = test.objfactory;
    }

    public void run() {
         /* starttest.countDown();
            try {
                starttest.await();
            } catch (InterruptedException ex) {
                Logger.getLogger(ConcurrentCacheTester.class.getName()).log(Level.SEVERE, null, ex);
                System.exit(-1);
            }*/
        if (test.cacheimpl != null)
            doItCached();
        else
            doItNew();
        testfinished.countDown();
    }

    private void doItCached(){
        long allocs = test.iterationsPerformed;
        Object obj = null;
        while(allocs-->0){
             obj = cache.poll();
             if (obj == null){
                 obj = objfactory.create();
                 objfactory.init(obj);
             }
             objfactory.recycle(obj);
             cache.offer(obj);
        }
        dummy2 = obj;
    }

    private void doItNew(){
        long allocs = test.iterationsPerformed;
        Object obj = null;
        while(allocs-->0){
             obj = objfactory.create();
             objfactory.init(obj);
        }
        dummy2 = obj;
    }

   /* private void performWorkInbetween(){
        //int max = 0 + rand.nextInt(20000);
        int max = test.workinbetween*1000;
        while(max-->0){
            dummy+=max;
        }
    }*/
}
