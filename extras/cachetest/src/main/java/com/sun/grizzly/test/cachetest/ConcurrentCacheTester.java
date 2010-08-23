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


import com.sun.grizzly.test.cachetest.testobjects.SelectionKeyOP;
import com.sun.grizzly.util.DataStructures;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


/**
 * Tests the throughput for object instance caching in a concurrent environment.<br>
 *
 * Please provide feedback, opinions.<br><br>
 *
 * Only test the situation where the threads do the operation of interest and no work inbetween.<br>
 * Reson is that the basic mechanism we want to test is how the caching algoritm <br>
 * and new Instance with its GC compares during maximal load.<br>
 * its only then their strengths and weaknesses really shows,<br>
 * its also worst cases that often are of interest for real life designs.<br>
 * the differences between caching or new Instance converges to 0 when the workinbetween increaes,<br>
 * making it a very specific result. <br>
 * <br><br>
 *  TODO add long lived Objects test, that will move Objects into oldgen<br><br>
 *
 *  -showversion -server -Xms512m -Xmx512m -XX:+AggressiveOpts -XX:+UseParallelOldGC -XX:+UseParallelGC
 *
 * @author Gustav Trede 
 */
public class ConcurrentCacheTester {    
       
    protected final ThreadPoolExecutor threadPool;
    protected final double calibrationTargetError = 0.1d;

    /**
     * performs a standard set of long running tests.
     * @param args used for minutesPerTest, default is 3
     * @throws java.lang.Throwable
     */
    public static void main(String[] argv) throws Throwable {
        int minutes = argv!=null&&argv.length>0 ? Integer.parseInt(argv[0]) : 3;
        ConcurrentCacheTester cct = new ConcurrentCacheTester();
        cct.performRangeOfTests(minutes,new InstanceFactory<SelectionKeyOP>(){
            public SelectionKeyOP create() {
                return new SelectionKeyOP(SelectionKey.OP_READ);
            }
            public void init(SelectionKeyOP instance) {
            }
            public void recycle(SelectionKeyOP instance) {
                instance.recycle();
            }
        });        
        /*cct.performRangeOfTests(minutes,new InstanceFactory<ConnectSelectionKeyOP>(){
            public ConnectSelectionKeyOP create() {
                return new ConnectSelectionKeyOP();
            }
            public void init(ConnectSelectionKeyOP instance) {
            }
            public void recycle(ConnectSelectionKeyOP instance) {
                instance.recycle();
            }
        });*/
        cct.threadPool.shutdownNow();
    }


    public ConcurrentCacheTester() {        
        threadPool = new ThreadPoolExecutor(1, 1, 0L,TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>()); 
    }


    /**
     *
     * @param factory {@link InstanceFactory}
     * @param minutesPerTest
     * @return List{@link List} {@link TestAndResult}
     * @throws java.lang.Throwable
     */
    public List<TestAndResult> performRangeOfTests(int minutesPerTest,
            final InstanceFactory factory) throws Throwable{
        Integer[] ia;
        int cpus = Runtime.getRuntime().availableProcessors();
        if (cpus <=8)
            ia = new Integer[]{1,8,64};
        else
            ia = new Integer[]{1,8,64,cpus*8};
        return performRangeOfTests(minutesPerTest,factory,
            new Queue[]{
                 DataStructures.getCLQinstance(Object.class),
                DataStructures.getLTQinstance(),
                //new LinkedBlockingQueue()
            }, ia, false);
    }

    /**
     * @param minutesPerTest {@link int}
     * @param objfactory {@link InstanceFactory}
     * @param cacheImplementations instances of {@link Queue}     
     * @param threadcounts {@link int}
     * @param calibrationInfoToSystemErr {@link boolean}
     * @return List{@link List} {@link TestAndResult}
     * @throws java.lang.Throwable
     */
    public List<TestAndResult> performRangeOfTests(int minutesPerTest,
            InstanceFactory objfactory,Queue[] cacheImplementations,
            Integer[] threadcounts,boolean calibrationInfoToSystemErr) throws Throwable{
        
        List<TestAndResult> tests = new ArrayList();       
        for (Integer threads:threadcounts){
            for (Queue cache:cacheImplementations){
                tests.add(new TestAndResult(cache, objfactory, threads, minutesPerTest, calibrationInfoToSystemErr));
            }
            tests.add(new TestAndResult(null     , objfactory, threads, minutesPerTest, calibrationInfoToSystemErr));
        }
        RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
        System.err.println("JVM: "+rmx.getVmVendor()+" "+rmx.getVmName()+" "+rmx.getVmVersion());
        System.err.println("JVM params: "+rmx.getInputArguments());
        System.err.println("Starting calibration.");
        for (TestAndResult test:tests){
            calibrate(test);
        }        
        System.err.println("Starting tests. estimated running time is "+tests.size()*minutesPerTest+" minutes +-"+(int)(calibrationTargetError*100)+"%");
        for (TestAndResult test:tests){
            executeTest(test);
            System.err.println(test);
        }
        return tests;
    }

    /**
     * calibrates and runs a single test.
     * @param test {@link TestAndResult}
     * @throws java.lang.Throwable
     */
    public void calibrateAndExecuteTest(TestAndResult test) throws Throwable{
        calibrate(test);
        executeTest(test);        
    }    

    protected void calibrate(TestAndResult test) throws Throwable{
        threadPool.setCorePoolSize(test.threadcount+1);
        threadPool.prestartAllCoreThreads();
        test.iterationsPerformed = 150000;
        executeTest(test);//warmup
        calcThreadOverhead(test);
        calcThreadOverhead(test);
        test.iterationsPerformed = 50000;
        calibrate0(test,test.minutesTorun*60,10);
    }

    protected void calcThreadOverhead(TestAndResult test) throws Throwable{
        long temp = test.iterationsPerformed;
        test.iterationsPerformed = 0;
        int iter = 25;
        double avg = 0;
        for (int i=0;i<iter;i++){
            avg+=executeTest(test);
        }
        test.threadpoolStartStopOverhead = (avg/=iter);
        test.iterationsPerformed = temp;
    }

    protected void calibrate0(TestAndResult test,int targetsec, int calibsec) throws Throwable{
        double ratio = 0;
        while (Math.abs(1-ratio)>calibrationTargetError){
            executeTest(test);
            if (test.systemErrprintCalibration)
                System.err.println("CALIBRATION: "+test);
            ratio = (test.actualRunningTimeSec-test.threadpoolStartStopOverhead)/calibsec ;
            test.iterationsPerformed*= (1d/ratio);
        }
        test.iterationsPerformed*= targetsec/calibsec;
    }
    
    protected double executeTest(final TestAndResult test) throws Throwable{
        threadPool.setCorePoolSize(test.threadcount+1);
        threadPool.prestartAllCoreThreads();
        TestLogic.testfinished = new CountDownLatch(test.threadcount);
        TestLogic.cache = test.cacheimpl;
        long t1 = System.nanoTime();
        for (int i=0;i<test.threadcount;i++){
            threadPool.execute(new TestLogic(test));
        }
        TestLogic.testfinished.await();
        test.actualRunningTimeSec = (System.nanoTime() - t1)/1000000000d;
        return test.actualRunningTimeSec;
    }

}
