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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author oleksiys
 */
public final class ProcessorExecutor {

    private static Logger logger = Grizzly.logger(ProcessorExecutor.class);

    public static boolean execute(Connection connection,
            IOEvent ioEvent, Processor processor,
            PostProcessor postProcessor)
            throws IOException {

        final Context context = Context.create(processor, connection, ioEvent,
                null, null);
        context.setPostProcessor(postProcessor);

        return resume(context);
    }

    public static boolean resume(final Context context) throws IOException {

        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "executing connection ("
                    + context.getConnection()
                    + "). IOEvent=" + context.getIoEvent()
                    + " processor=" + context.getProcessor());
        }

        final ProcessorResult result = context.getProcessor().process(context);
        final ProcessorResult.Status status = result.getStatus();

        if (status != ProcessorResult.Status.TERMINATE) {
            complete(context, status);
            return status == ProcessorResult.Status.COMPLETED;
        }

        return false;
    }

    private static void complete(Context context, ProcessorResult.Status status)
            throws IOException {
        try {
            final PostProcessor postProcessor = context.getPostProcessor();
            if (postProcessor != null) {
                postProcessor.process(context, status);
            }

        } finally {
            context.recycle();
        }
    }
}
