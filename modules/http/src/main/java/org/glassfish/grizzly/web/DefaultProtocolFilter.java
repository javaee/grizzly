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

package org.glassfish.grizzly.web;

import org.glassfish.grizzly.web.container.util.InputReader;
import org.glassfish.grizzly.web.container.util.Interceptor;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.naming.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterAdapter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Default ProtocolFilter implementation, that allows http request processing.
 *
 * @author Jeanfrancois Arcand
 */
public class DefaultProtocolFilter extends FilterAdapter {
    
    /**
     * The current TCP port.
     */
    private int port;
    
    /**
     * Logger
     */
    protected final static Logger logger = Grizzly.logger;
    
    
    public DefaultProtocolFilter(int port) {
        this.port = port;
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        HttpWorkerThread workerThread =
                ((HttpWorkerThread)Thread.currentThread());
        
        boolean keepAlive = false;
        
        ProcessorTask processorTask = workerThread.getProcessorTask();   
        if (processorTask == null) {
            processorTask = selectorThread.getProcessorTask();
            workerThread.setProcessorTask(processorTask);
        }

        KeepAliveThreadAttachment k = (KeepAliveThreadAttachment) workerThread.getAttachment();
        k.setTimeout(System.currentTimeMillis());
        KeepAliveStats ks = selectorThread.getKeepAliveStats();
        k.setKeepAliveStats(ks);

        // Bind the Attachment to the SelectionKey
        ctx.getSelectionKey().attach(k);

        int count = k.increaseKeepAliveCount();
        if (count > selectorThread.getMaxKeepAliveRequests() && ks != null) {
            ks.incrementCountRefusals();
            processorTask.setDropConnection(true);
        } else {
            processorTask.setDropConnection(false);
        }

        configureProcessorTask(processorTask, ctx, workerThread,
                streamAlgorithm.getHandler());

        try {
            keepAlive = processorTask.process(inputStream, null);
        } catch (Throwable ex) {
            logger.log(Level.INFO, "ProcessorTask exception", ex);
            keepAlive = false;
        }

        Object ra = workerThread.getAttachment().getAttribute("suspend");
        if (ra != null){
            // Detatch anything associated with the Thread.
            workerThread.setInputStream(new InputReader());
            workerThread.setByteBuffer(null);
            workerThread.setProcessorTask(null);
                       
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
            return true;
        }
        
        if (processorTask != null){
            processorTask.recycle();
        }
        
        if (keepAlive){
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.REGISTER);
        } else {
            ctx.setKeyRegistrationState(
                    Context.KeyRegistrationState.CANCEL);
        }
        
        // Last filter.
        return nextAction;
    }

    @Override
    public NextAction postRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        return nextAction;
    }
    
    /**
     * Configure {@link ProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask, 
            FilterChainContext context, HttpWorkerThread workerThread,
            Interceptor handler) {
        processorTask.setConnection(context.getConnection());
        
        if (processorTask.getHandler() == null){
            processorTask.setHandler(handler);
        }
    }
    
    /**
     * Is {@link ProtocolFilter} secured
     * @return is {@link ProtocolFilter} secured
     */
    protected boolean isSecure() {
        return false;
    }
}
