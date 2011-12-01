/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

/**
 *
 * @author oleksiys
 */
public final class ProcessorExecutor {

    private static final Logger LOGGER = Grizzly.logger(ProcessorExecutor.class);

    public static boolean execute(final Connection connection,
            final IOEvent ioEvent, final Processor processor,
            final IOEventProcessingHandler processingHandler)
            throws IOException {

        return execute(Context.create(connection, processor, ioEvent,
                processingHandler));
    }
   
    @SuppressWarnings("unchecked")
    public static boolean execute(Context context) throws IOException {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "executing connection ({0}). IOEvent={1} processor={2}",
                    new Object[]{context.getConnection(), context.getIoEvent(),
                    context.getProcessor()});
        }

        boolean isRerun;
        ProcessorResult result;
        ProcessorResult.Status status;
        
        do {
            result = context.getProcessor().process(context);
            status = result.getStatus();
            isRerun = (status == ProcessorResult.Status.RERUN);
            if (isRerun) {
                final Context newContext = (Context) result.getData();
                rerun(context, newContext);
                context = newContext;
            }
        } while (isRerun);
        
        switch (status) {
            case COMPLETE:
                complete(context, result.getData());
                return true;

            case LEAVE:
                leave(context);
                return false;

            case TERMINATE:
                terminate(context);
                return false;

            case REREGISTER:
                reregister(context, result.getData());
                return true;

            case ERROR:
                error(context, result.getData());
                return false;

            case NOT_RUN:
                notRun(context);
                return false;
                
            default: throw new IllegalStateException();
        }
    }

    public static boolean resume(final Context context) throws IOException {
        return execute(context);
    }

    private static void complete(final Context context, final Object data)
            throws IOException {

        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();
        
        try {
            if (processingHandler != null) {
                processingHandler.onComplete(context, data);
            }
        } finally {
            context.recycle();
        }
    }

    private static void leave(final Context context) throws IOException {
        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();

        try {
            if (processingHandler != null) {
                processingHandler.onLeave(context);
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
        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();

        try {
            if (processingHandler != null) {
                processingHandler.onReregister(realContext);
            }
        } finally {
            realContext.recycle();
        }
    }

    private static void terminate(final Context context) throws IOException {
        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();

        if (processingHandler != null) {
            processingHandler.onTerminate(context);
        }
    }

    private static void rerun(final Context context, final Context newContext)
            throws IOException {
        
        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();

        if (processingHandler != null) {
            processingHandler.onRerun(context, newContext);
        }
    }

    private static void error(final Context context, final Object description)
            throws IOException {
        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();
        
        try {
            if (processingHandler != null) {
                processingHandler.onError(context, description);
            }
        } finally {
            context.recycle();
        }

    }

    private static void notRun(final Context context) throws IOException {
        final IOEventProcessingHandler processingHandler =
                context.getProcessingHandler();
        try {
            if (processingHandler != null) {
                processingHandler.onNotRun(context);
            }
        } finally {
            context.recycle();
        }

    }

}
