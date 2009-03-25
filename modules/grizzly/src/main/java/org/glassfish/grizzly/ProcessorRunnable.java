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
package org.glassfish.grizzly;

import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.Transport.State;
import java.io.IOException;
import java.util.logging.Level;

/**
 *
 * @author Alexey Stashok
 */
public class ProcessorRunnable implements Runnable {

    private Context context;

    private IOEvent ioEvent;
    private Connection connection;
    private Processor processor;
    private PostProcessor postProcessor;

    public ProcessorRunnable(Context context) {
        this.context = context;
    }

    public ProcessorRunnable(IOEvent ioEvent, Connection connection, Processor processor, PostProcessor postProcessor) {
        this.ioEvent = ioEvent;
        this.connection = connection;
        this.processor = processor;
        this.postProcessor = postProcessor;
    }

    public IOEvent getIoEvent() {
        return ioEvent;
    }

    public void setIoEvent(IOEvent ioEvent) {
        this.ioEvent = ioEvent;
    }

    public Connection getConnection() {
        return connection;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    public PostProcessor getPostProcessor() {
        return postProcessor;
    }

    public void setPostProcessor(PostProcessor postProcessor) {
        this.postProcessor = postProcessor;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void run() {
        if (context == null) {
            createContext();
            initContext();
        }

        ProcessorResult result = null;

        try {
            processor.beforeProcess(context);

            do {
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

    protected void createContext() {
        context = processor.context();
        if (context == null) {
            context = new Context();
        }
    }

    protected void initContext() {
        context.setIoEvent(ioEvent);
        context.setConnection(connection);
        context.setProcessor(processor);
        context.setPostProcessor(postProcessor);
    }

    private void logException(Context context, Throwable e) {
        State transportState = context.getConnection().getTransport().
                getState().getState(false);

        if (transportState != State.STOPPING && transportState != State.STOP) {
            Grizzly.logger.log(Level.WARNING,
                    "Processor execution exception. Processor: " +
                    processor + " Context: " + context, e);
        } else {
            Grizzly.logger.log(Level.FINE,
                    "Processor execution exception, " +
                    "however transport was in the stopping phase: " + transportState +
                    " Processor: " +
                    processor + " Context: " + context, e);
        }
    }

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
}