/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.localization.LogMessages;

/**
 *
 * @author oleksiys
 */
public final class ProcessorExecutor {

    private static final Logger LOGGER = Grizzly.logger(ProcessorExecutor.class);

    public static void execute(final Connection connection,
            final IOEvent ioEvent, final Processor processor,
            final IOEventLifeCycleListener lifeCycleListener) {

        execute(Context.create(connection, processor, ioEvent, lifeCycleListener));
    }
   
    @SuppressWarnings("unchecked")
    public static void execute(Context context) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "executing connection ({0}). IOEvent={1} processor={2}",
                    new Object[]{context.getConnection(), context.getIoEvent(),
                    context.getProcessor()});
        }

        boolean isRerun;
        ProcessorResult result;
        
        try {
            do {
                result = context.getProcessor().process(context);
                isRerun = (result.getStatus() == ProcessorResult.Status.RERUN);
                if (isRerun) {
                    final Context newContext = (Context) result.getData();
                    rerun(context, newContext);
                    context = newContext;
                }
            } while (isRerun);
            
            complete0(context, result);
            
        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_PROCESSOR_ERROR(
                                context.getConnection(), context.getIoEvent(),
                                context.getProcessor()),
                        t);
            }
            
            try {
                error(context, t);
            } catch (Exception ignored) {
            }
        }
    }

    public static void resume(final Context context) throws IOException {
        execute(context);
    }

    private static void complete(final Context context, final Object data)
            throws IOException {

        final int sz = context.lifeCycleListeners.size();
        final IOEventLifeCycleListener[] listeners = context.lifeCycleListeners.array();
        try {
            for (int i = 0; i < sz; i++) {
                listeners[i].onComplete(context, data);
            }
        } finally {
            context.recycle();
        }
    }

    private static void leave(final Context context) throws IOException {
        final int sz = context.lifeCycleListeners.size();
        final IOEventLifeCycleListener[] listeners = context.lifeCycleListeners.array();
        try {
            for (int i = 0; i < sz; i++) {
                listeners[i].onLeave(context);
            }
        } finally {
            context.recycle();
        }
    }

    private static void reregister(final Context context, final Object data)
            throws IOException {
        
        // "Context context" was suspended, so we reregister with its copy
        // which is passed as "Object data"
        final Context realContext = (Context) data;
        
        final int sz = context.lifeCycleListeners.size();
        final IOEventLifeCycleListener[] listeners = context.lifeCycleListeners.array();
        try {
            for (int i = 0; i < sz; i++) {
                listeners[i].onReregister(realContext);
            }
        } finally {
            realContext.recycle();
        }
    }

    private static void rerun(final Context context, final Context newContext)
            throws IOException {
        
        final int sz = context.lifeCycleListeners.size();
        final IOEventLifeCycleListener[] listeners = context.lifeCycleListeners.array();
        for (int i = 0; i < sz; i++) {
            listeners[i].onRerun(context, newContext);
        }
    }

    private static void error(final Context context, final Object description)
            throws IOException {
        final int sz = context.lifeCycleListeners.size();
        final IOEventLifeCycleListener[] listeners = context.lifeCycleListeners.array();
        for (int i = 0; i < sz; i++) {
            listeners[i].onError(context, description);
        }
    }

    private static void notRun(final Context context) throws IOException {
        final int sz = context.lifeCycleListeners.size();
        final IOEventLifeCycleListener[] listeners = context.lifeCycleListeners.array();
        try {
            for (int i = 0; i < sz; i++) {
                listeners[i].onNotRun(context);
            }
        } finally {
            context.recycle();
        }
    }

    static void complete(final Context context,
            final ProcessorResult result) {
        
        try {
            complete0(context, result);
        } catch (Throwable t) {
            try {
                error(context, t);
            } catch (Exception ignored) {
            }
        }
    }
    
    private static void complete0(final Context context,
            final ProcessorResult result)
            throws IllegalStateException, IOException {

        final ProcessorResult.Status status = result.getStatus();

        switch (status) {
            case COMPLETE:
                complete(context, result.getData());
                break;

            case LEAVE:
                leave(context);
                break;

            case TERMINATE:
//                    terminate(context);
                break;

            case REREGISTER:
                reregister(context, result.getData());
                break;

            case ERROR:
                error(context, result.getData());
                break;

            case NOT_RUN:
                notRun(context);
                break;

            default:
                throw new IllegalStateException();
        }
    }
}
