/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.comet;

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncFilter;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.arp.AsyncProcessorTask;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AJAX/Comet support using the Grizzly Asynchronous Request Processing 
 * default mechanism.
 *
 * Instance of this class represent an interrupted/polled/Comet request. 
 * 
 * Note: The request can be resumed anytime, and the polled request is not 
 * holding a thread.
 *
 * @author Jeanfrancois Arcand
 */
public class CometAsyncFilter implements AsyncFilter {       
 
    /**
     * Main logger
     */
    private final static Logger logger = SelectorThread.logger();
        
    
    /**
     * Comet Async Filter.
     */
    public CometAsyncFilter() {
    }

    
    /**
     * Execute the Comet {@link AsyncFilter} by delegating the work to the 
     * {@link CometEngine}. At this stage, the request has already
     * been interrupted.
     */
    public AsyncFilter.Result doFilter(AsyncExecutor asyncExecutor) {
        AsyncProcessorTask apt = (AsyncProcessorTask)asyncExecutor.getAsyncTask();
        CometEngine cometEngine = CometEngine.getEngine();                
        try{
            if (!cometEngine.handle(apt)) {
                return AsyncFilter.Result.FINISH;
            } 
        } catch (IOException ex){
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE,
                           LogMessages.SEVERE_GRIZZLY_COMET_IO_ERROR("CometAsyncFilter.doFilter()"),
                           ex);
            }
        }
        
        return AsyncFilter.Result.INTERRUPT;
    }
}
