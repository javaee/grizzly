/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import java.net.Socket;

import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.utils.transport.DefaultStreamReader;
import org.glassfish.grizzly.utils.transport.DefaultStreamWriter;
import org.glassfish.grizzly.utils.streams.StreamReader;
import org.glassfish.grizzly.utils.streams.StreamWriter;

/**
 * {@link org.glassfish.grizzly.Processor}, which is not interested in processing I/O events.
 * {@link org.glassfish.grizzly.Connection} lifecycle should be managed explicitly,
 * using read/write/accept/connect methods.
 *
 * This {@link org.glassfish.grizzly.Processor} could be set on {@link org.glassfish.grizzly.Connection} to avoid it from
 * being processed by {@link FilterChain} or other {@link org.glassfish.grizzly.Processor}. In this
 * case {@link org.glassfish.grizzly.Connection} could be used like regular Java {@link Socket}.
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class StandaloneProcessor implements Processor {
    public static final StandaloneProcessor INSTANCE = new StandaloneProcessor();

    /**
     * This method should never be called, because
     * {@link StandaloneProcessor#isInterested(org.glassfish.grizzly.IOEvent)} returns false for any
     * {@link org.glassfish.grizzly.IOEvent}.
     */
    @Override
    public ProcessorResult process(final Context context) {
        final Event event = context.getEvent();
        if (event == ServiceEvent.READ) {
            final Connection connection = context.getConnection();
            final AsyncQueueReader reader =
                    ((AsyncQueueEnabledTransport) connection.getTransport()).
                    getAsyncQueueIO().getReader();

            return reader.processAsync(context).toProcessorResult();
        } else if (event == ServiceEvent.WRITE) {
            final Connection connection = context.getConnection();
            final AsyncQueueWriter writer =
                    ((AsyncQueueEnabledTransport) connection.getTransport()).
                    getAsyncQueueIO().getWriter();
            
            return writer.processAsync(context).toProcessorResult();
        }
        
        return ProcessorResult.createComplete();
    }

    @Override
    public Context obtainContext(final Connection connection) {
        final Context context = Context.create(connection);
        context.setProcessor(this);
        return context;
    }


    /**
     * Get the {@link Connection} {@link StreamReader}, to read data from the
     * {@link Connection}.
     *
     * @return the {@link Connection} {@link StreamReader}, to read data from the
     * {@link Connection}.
     */
    public StreamReader getStreamReader(Connection connection) {
        return new DefaultStreamReader(connection);
    }

    /**
     * Get the {@link Connection} {@link StreamWriter}, to write data to the
     * {@link Connection}.
     *
     * @return the {@link Connection} {@link StreamWriter}, to write data to the
     * {@link Connection}.
     */
    public StreamWriter getStreamWriter(Connection connection) {
        return new DefaultStreamWriter(connection);
    }

    @Override
    public void read(Connection connection,
            CompletionHandler completionHandler) {
        
        final Transport transport = connection.getTransport();
        transport.getReader(connection).read(connection,
                null, completionHandler);
    }

    @Override
    public void write(Connection connection, Object dstAddress,
            Object message, CompletionHandler completionHandler,
            LifeCycleHandler lifeCycleHandler) {
        
        final Transport transport = connection.getTransport();
        
        transport.getWriter(connection).write(connection, dstAddress,
                (Buffer) message, completionHandler, lifeCycleHandler);
    }
}
