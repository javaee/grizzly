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

package com.sun.grizzly.aio.http;

import com.sun.grizzly.http.HttpWorkerThread;
import java.util.concurrent.ThreadFactory;

/**
 *
 * @author Jeanfrancois Arcand
 */
public class WorkerThreadFactory implements ThreadFactory{
    
    /**
     * The {@link ThreadGroup} used.
     */
    private final static ThreadGroup threadGroup = new ThreadGroup("Grizzly");
    
    public Thread newThread(Runnable r){
        HttpWorkerThread t = new HttpWorkerThread(threadGroup,r);
        t.setName("GrizzlyWorker-" + t.getName());
        t.setDaemon(true);
        return t;
    }    

}
