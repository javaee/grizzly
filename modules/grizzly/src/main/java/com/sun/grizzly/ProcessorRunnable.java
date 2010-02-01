/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly;

import com.sun.grizzly.ProcessorResult.Status;
import com.sun.grizzly.Transport.State;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Runnable} task, which encapsulates {@link Processor} execution
 * information and actually runs {@link Processor}
 * to process occured {@link IOEvent}.
 * Usually {@link ProcessorRunnable} task is constructed by {@link Transport}
 * and passed to {@link Strategy}, which makes decision how this task should
 * be executed.
 * 
 * @author Alexey Stashok
 */
public class ProcessorRunnable implements Runnable {

    private static final Logger logger = Grizzly.logger(ProcessorRunnable.class);

    /**
     * {@link Processor} execution {@link Context}.
     */
    private Context context;

    /**
     * {@link IOEvent}, which occured on {@link Connection}.
     */
    private IOEvent ioEvent;

    /**
     * Connection
     */
    private Connection connection;

    /**
     * {@link Processor}, which is going to process {@link IOEvent}.
     */
    private Processor processor;

    /**
     * PostProcessor to be called, on processing completion
     */
    private PostProcessor postProcessor;


    public ProcessorRunnable(Context context) {
        this.context = context;
    }

    public ProcessorRunnable(IOEvent ioEvent, Connection connection,
            Processor processor, PostProcessor postProcessor) {
        this.ioEvent = ioEvent;
        this.connection = connection;
        this.processor = processor;
        this.postProcessor = postProcessor;
    }

    /**
     * Get the processing {@link IOEvent}.
     *
     * @return the processing {@link IOEvent}.
     */
    public IOEvent getIoEvent() {
        return ioEvent;
    }

    /**
     * Set the processing {@link IOEvent}.
     *
     * @param ioEvent the processing {@link IOEvent}.
     */
    public void setIoEvent(IOEvent ioEvent) {
        this.ioEvent = ioEvent;
    }

    /**
     * Get the processing {@link Connection}.
     *
     * @return the processing {@link Connection}.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Set the processing {@link Connection}.
     *
     * @param connection the processing {@link Connection}.
     */
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     * Get the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     *
     * @return the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     *
     * @param processor the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     */
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    /**
     * Get the {@link PostProcessor}, which will be called after
     * {@link Processor} will finish its execution to finish IOEvent processing.
     *
     * @return the {@link PostProcessor}, which will be called after
     * {@link Processor} will finish its execution to finish IOEvent processing.
     */
    public PostProcessor getPostProcessor() {
        return postProcessor;
    }

    /**
     * Set the {@link PostProcessor}, which will be called after
     * {@link Processor} will finish its execution to finish IOEvent processing.
     *
     * @param ioEventPostProcessor the {@link PostProcessor}, which will be
     * called after {@link Processor} will finish its execution to
     * finish IOEvent processing.
     */
    public void setPostProcessor(PostProcessor ioEventPostProcessor) {
        this.postProcessor = ioEventPostProcessor;
    }

    /**
     * Get the processing {@link Context}.
     * 
     * @return the processing {@link Context}.
     */
    public Context getContext() {
        return context;
    }

    /**
     * Set the processing {@link Context}.
     *
     * @param context the processing {@link Context}.
     */
    public void setContext(Context context) {
        this.context = context;
    }

    /**
     * Runs the IOEvent processing.
     */
    @Override
    public void run() {
        if (context == null) {
            createContext();
            initContext();
        } else {
            initFromContext();
        }

        context.setProcessorRunnable(this);
        
        ProcessorResult result = null;

        try {
            if (context.state() == Context.State.RUNNING) {
                processor.beforeProcess(context);
            }

            do {
                context.resume();
                result = processor.process(context);
            } while (result != null &&
                    result.getStatus() == Status.RERUN);
            
            if (result == null || result.getStatus() != Status.TERMINATE) {
                postProcess(context, result);
            }
        } catch (IOException e) {
            result = new ProcessorResult(Status.ERROR, e);
            postProcess(context, result);
            logException(context, e);
        } catch (Throwable t) {
            logException(context, t);
            postProcess(context, result);
            throw new RuntimeException(t);
        }
    }

    /**
     * Create {@link Context} instance.
     */
    protected void createContext() {
        context = processor.context();
        if (context == null) {
            context = new Context();
        }
    }

    /**
     * Initialize {@link Context} with task's settings.
     */
    protected void initContext() {
        context.setIoEvent(ioEvent);
        context.setConnection(connection);
        context.setProcessor(processor);
        context.setPostProcessor(postProcessor);
    }

    private void initFromContext() {
        ioEvent = context.getIoEvent();
        connection = context.getConnection();
        processor = context.getProcessor();
        postProcessor = context.getPostProcessor();
    }

    /**
     * Finishing processing by calling post-process methods on {@link Processor}
     * and {@link PostProcessor}.
     * 
     * @param context processing {@link Context}.
     * @param result {@link Processor} result.
     */
    private void postProcess(Context context, ProcessorResult result) {
        try {
            processor.afterProcess(context);
        } catch (IOException e) {
            logException(context, e);
        }
        PostProcessor ioEventPostProcessor = context.getPostProcessor();
        if (ioEventPostProcessor != null) {
            try {
                ioEventPostProcessor.process(result, context);
            } catch (IOException e) {
                logException(context, e);
            }
        }
        context.offerToPool();
    }

    /**
     * Logs the exception.
     * 
     * @param context processing {@link Context}.
     * @param e {@link Exception}, which occured.
     */
    private void logException(Context context, Throwable e) {
        State transportState = context.getConnection().getTransport().
                getState().getState(false);

        if (transportState != State.STOPPING && transportState != State.STOP) {
            logger.log(Level.WARNING,
                    "Processor execution exception. Processor: " +
                    processor + " Context: " + context, e);
        } else {
            logger.log(Level.FINE,
                    "Processor execution exception, " +
                    "however transport was in the stopping phase: " + transportState +
                    " Processor: " +
                    processor + " Context: " + context, e);
        }
    }
}