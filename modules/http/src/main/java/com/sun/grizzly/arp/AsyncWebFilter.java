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

package com.sun.grizzly.arp;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import com.sun.grizzly.Connection;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.http.FileCache;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.http.TaskEvent;
import com.sun.grizzly.http.TaskListener;
import com.sun.grizzly.http.WebFilter;
import com.sun.grizzly.tcp.Adapter;

/**
 *
 * @author Alexey Stashok
 */
public class AsyncWebFilter extends WebFilter<AsyncWebFilterConfig>
        implements TaskListener {

    public AsyncWebFilter(String name) {
        this(name, new AsyncWebFilterConfig());
    }

    public AsyncWebFilter(String name, AsyncWebFilterConfig config) {
        super(name, config);
    }

    /**
     * Execute a unit of processing work to be performed. This ProtocolFilter
     * may either complete the required processing and return false,
     * or delegate remaining processing to the next ProtocolFilter in a
     * ProtocolChain containing this ProtocolFilter by returning true.
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {

        if (config.isAsyncEnabled()) {
            ProcessorTask processor = getProcessorTask(ctx);
            configureProcessorTask(processor, ctx);

            try {
                config.getAsyncHandler().handle(
                        processor);
            } catch (Throwable ex) {
                logger.log(Level.INFO, "Processor exception", ex);
                ctx.getConnection().close();
                return ctx.getStopAction();
            }

            // Suspend further FilterChain execution on the current thread
            return ctx.getSuspendAction();
        } else {
            return super.handleRead(ctx, nextAction);
        }
    }

    /**
     * Called when the Asynchronous Request Processing is resuming.
     */
    @Override
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
                // Resume FilterChain execution
                FilterChainContext context = processor.getFilterChainContext();
                try {
                    context.setCurrentFilterIdx(context.getCurrentFilterIdx() + 1);
                    context.getProcessorRunnable().run();
                } catch (Exception e) {
                    try {
                        connection.close();
                    } catch (IOException ee) {
                    }
                }
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
    @Override
    protected void configureProcessorTask(ProcessorTask processorTask,
            FilterChainContext context) {
        
        super.configureProcessorTask(processorTask, context);
        
        if (config.isAsyncEnabled()) {
            processorTask.setEnableAsyncExecution(true);
            processorTask.setTaskListener(this);
            processorTask.setInputStream(context.getStreamReader());
            processorTask.setOutputStream(context.getStreamWriter());
        } else {
            processorTask.setEnableAsyncExecution(false);
        }
    }

    @Override
    protected ProcessorTask initializeProcessorTask(ProcessorTask task) {
        task = super.initializeProcessorTask(task);
        if (config.isAsyncEnabled()) {
            task.setEnableAsyncExecution(true);
            task.setAsyncHandler(config.getAsyncHandler());
        }
        
        return task;
    }

    /**
     * Display the Grizzly configuration parameters.
     */
    @Override
    protected void displayConfiguration() {
       if (config.isDisplayConfiguration()) {
            FileCache fileCache = config.getFileCache();
            Adapter adapter = config.getAdapter();
            boolean isAsyncEnabled = config.isAsyncEnabled();
            
            logger.log(Level.INFO,
                    "\n Grizzly configuration"
                    + "\n\t name: "
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
                    + "\n\t Processing mode: "
                    + (isAsyncEnabled ? "asynchronous" : "synchronous"));
        }
    }

}
