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
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Contains the configuration parameters and the result
 *
 * @author Gustav Trede
 */
public class TestAndResult {

    protected final Queue cacheimpl;
    protected final InstanceFactory  objfactory;
    //protected final int workinbetween;
    protected final int threadcount;
    protected final int minutesTorun;
    protected final boolean systemErrprintCalibration;

    protected double threadpoolStartStopOverhead;
    protected double actualRunningTimeSec;
    protected long iterationsPerformed;

    /**
     *
     * @param cacheimpl {@link Queue} null if no cache is to be used
     * @param objfactory  {@link InstanceFactory}
     * @param threadcount
     * @param minutesTorun no smaller time unit then minutes is allowed, we want as valid results as possible
     */
    public TestAndResult(Queue cacheimpl, InstanceFactory  objfactory,
             int threadcount, int minutesTorun){
        this(cacheimpl, objfactory, threadcount, minutesTorun,false);
    }
    /**
     *
     * @param cacheimpl  null if no cache is to be used
     * @param objfactory {@link InstanceFactory}
     * @param threadcount 
     * @param minutesTorun no smaller time unit then minutes is allowed, we want as valid results as possible
     * @param printCalibration
     */
    public TestAndResult(Queue cacheimpl, InstanceFactory objfactory,
             int threadcount, int minutesTorun, boolean systemErrprintCalibration) {
        this.cacheimpl        = cacheimpl;
        this.objfactory       = objfactory;
        this.threadcount      = threadcount;
        this.minutesTorun     = minutesTorun;
        this.systemErrprintCalibration = systemErrprintCalibration;
    }

    /**
     * Kilo instances/sec
     * @return
     */
    public long getKiloThroughput(){
        return (long) (iterationsPerformed*threadcount / 
                ((actualRunningTimeSec-threadpoolStartStopOverhead) * 1000) + 500);
    }

    /**
     *
     * @return
     */
    public String getResultInText(){
        int ccl = ConcurrentLinkedQueue.class.getSimpleName().length()+1;
        int cpus = Runtime.getRuntime().availableProcessors();
        return "CPU:"+pad(cpus,4)+"Threads:"+pad(threadcount,5)+
              // "work("+pad(workinbetween+")",5)+
               s(getKiloThroughput(),6)+" K "+objfactory.create().getClass().getSimpleName()+"/sec "+
               (cacheimpl!=null?pad(cacheimpl.getClass().getSimpleName(),ccl):pad("new Instance",ccl))+
               "time "+(int)(actualRunningTimeSec+0.5)+" sec. ";
               //" overhead: "+ threadpoolStartStopOverhead +" sec.";
    }

    private String pad(Object o, int length){
        String s=o.toString();
        while (s.length()<length)
            s+=" ";
        return s;
    }

    private String s(Object o, int length){
        String s=o.toString();
        while (s.length()<length)
            s=" "+s;
        return s;
    }

    @Override
    public String toString() {
        return getResultInText();
    }

    /**
     * the overhead in seconds for the start to wait for an empty test.
     *
     * @return
     */
    public double getThreadpoolStartStopOverhead() {
        return threadpoolStartStopOverhead;
    }

    /**
     *
     * @return
     */
    public double getActualRunningTimeSec() {
        return actualRunningTimeSec;
    }

    /**
     *  null if no cache is used
     * @return
     */
    public Queue getCacheimpl() {
        return cacheimpl;
    }


    /**
     * number of actual iterations performed during the test.
     * (allocate/fetc, init, recycly, release)
     * @return
     */
    public long getIterationsPerformed() {
        return iterationsPerformed;
    }

    /**
     * configured running time in minutes
     * @return
     */
    public int getMinutesTorun() {
        return minutesTorun;
    }

    /**
     *
     * @return {@link InstanceFactory}
     */
    public InstanceFactory getObjfactory() {
        return objfactory;
    }

    /**
     * 
     * @return
     */
    public int getThreadcount() {
        return threadcount;
    }
}
