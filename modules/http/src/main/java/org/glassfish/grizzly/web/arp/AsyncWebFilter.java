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

package org.glassfish.grizzly.web.arp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.StopAction;
import org.glassfish.grizzly.web.HttpWorkerThread;
import org.glassfish.grizzly.web.ProcessorTask;
import org.glassfish.grizzly.web.TaskEvent;
import org.glassfish.grizzly.web.TaskListener;
import org.glassfish.grizzly.web.WebFilter;
import org.glassfish.grizzly.web.container.util.Interceptor;

/**
 *
 * @author Alexey Stashok
 */
public class AsyncWebFilter extends WebFilter implements TaskListener {

    // --------------------------------------------- Asynch supports -----//

    /**
     * Is asynchronous mode enabled?
     */
    protected boolean asyncExecution = false;


    /**
     * When the asynchronous mode is enabled, the execution of this object
     * will be delegated to the {@link AsyncHandler}
     */
    protected AsyncHandler asyncHandler;


    /**
     * Default size for ByteBuffer.
     */
    protected int bbSize = 4096;


    /**
     * Execute a unit of processing work to be performed. This ProtocolFilter
     * may either complete the required processing and return false,
     * or delegate remaining processing to the next ProtocolFilter in a
     * ProtocolChain containing this ProtocolFilter by returning true.
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        HttpWorkerThread workerThread = ((HttpWorkerThread)Thread.currentThread());

        ctx.getConnection().setIdleTime(Connection.UNLIMITED_IDLE_TIMEOUT,
                TimeUnit.MILLISECONDS);

        ProcessorTask processor = getProcessorTask(ctx);
        configureProcessorTask(processor, ctx, workerThread,
                interceptor, (InputStream) ctx.getStreamReader());

        try {
            getAsyncHandler().handle(processor);
        } catch (Throwable ex) {
            logger.log(Level.INFO, "Processor exception", ex);
            ctx.getConnection().close();
            return new StopAction();
        }

        // Last filter.
        return nextAction;
    }


    /**
     * Called when the Asynchronous Request Processing is resuming.
     */
    public void taskEvent(TaskEvent event) {
        if (event.getStatus() == TaskEvent.COMPLETED
                || event.getStatus() == TaskEvent.ERROR){
            ProcessorTask processor = (ProcessorTask) event.attachement();

            Connection connection = processor.getConnection();
            // Should never happens.
            if (connection == null){
                logger.log(Level.WARNING,"AsyncProtocolFilter invalid state.");
                return;
            }

            if (processor.isKeepAlive() && !processor.isError()) {
                connection.setIdleTime(Connection.UNLIMITED_IDLE_TIMEOUT,
                        TimeUnit.MILLISECONDS);
            } else {
                try {
                    connection.close();
                } catch (IOException e) {
                }
            }

            processor.recycle();
        }
    }


    /**
     * Configure {@link SSLProcessorTask}.
     */
    protected void configureProcessorTask(ProcessorTask processorTask,
            FilterChainContext context, HttpWorkerThread workerThread,
            Interceptor handler, InputStream inputStream) {
        processorTask.setConnection(context.getConnection());
        processorTask.setTaskListener(this);
        processorTask.setInputStream(inputStream);
        processorTask.setHandler(handler);
    }

    @Override
    protected void configureProcessorTask(ProcessorTask processorTask,
            FilterChainContext context, HttpWorkerThread workerThread,
            Interceptor handler) {
        super.configureProcessorTask(processorTask, context, workerThread, handler);
        processorTask.setEnableAsyncExecution(asyncExecution);
        processorTask.setAsyncHandler(asyncHandler);
    }



    /**
     * Reconfigure Grizzly Asynchronous Request Processing(ARP) internal
     * objects.
     */
    protected void reconfigureAsyncExecution(){
        for(ProcessorTask task : processorTasks) {
            task.setEnableAsyncExecution(asyncExecution);
            task.setAsyncHandler(asyncHandler);
        }
    }

    // ---------------------------- Async-------------------------------//

    /**
     * Enable the {@link AsyncHandler} used when asynchronous
     */
    public void setEnableAsyncExecution(boolean asyncExecution){
        this.asyncExecution = asyncExecution;
        reconfigureAsyncExecution();
    }


    /**
     * Return true when asynchronous execution is
     * enabled.
     */
    public boolean getEnableAsyncExecution(){
        return asyncExecution;
    }


    /**
     * Set the {@link AsyncHandler} used when asynchronous execution is
     * enabled.
     */
    public void setAsyncHandler(AsyncHandler asyncHandler){
        this.asyncHandler = asyncHandler;
    }


    /**
     * Return the {@link AsyncHandler} used when asynchronous execution is
     * enabled.
     */
    public AsyncHandler getAsyncHandler(){
        return asyncHandler;
    }

    // ------------------------------------------------------ Debug ---------//


    /**
     * Display the Grizzly configuration parameters.
     */
    @Override
    protected void displayConfiguration(){
       if (config.isDisplayConfiguration()){
            logger.log(Level.INFO,
                    "\n Grizzly configuration"
                    + "\n\t name"
                    + name
                    + "\n\t maxHttpHeaderSize: "
                    + config.getMaxHttpHeaderSize()
                    + "\n\t maxKeepAliveRequests: "
                    + config.getMaxKeepAliveRequests()
                    + "\n\t keepAliveTimeoutInSeconds: "
                    + config.getKeepAliveTimeoutInSeconds()
                    + "\n\t Static File Cache enabled: "
                    + (fileCache != null && fileCache.isEnabled())
                    + "\n\t Static resources directory: "
                    + new File(config.getRootFolder()).getAbsolutePath()
                    + "\n\t Adapter : "
                    + (adapter == null ? null : adapter.getClass().getName())
                    + "\n\t Processing mode: asynchronous");
        }
    }

}
