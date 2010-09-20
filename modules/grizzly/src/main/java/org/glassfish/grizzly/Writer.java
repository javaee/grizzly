/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.Future;

/**
 * Implementations of this interface are able to write data from a {@link Buffer}
 * to {@link Connection}.
 *
 * There are two basic Writer implementations in Grizzly:
 * {@link org.glassfish.grizzly.asyncqueue.AsyncQueueWriter},
 * {@link org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorWriter}.
 *
 * @author Alexey Stashok
 */
public interface Writer<L> {
    /**
     * Method writes the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to write to
     * @param buffer the buffer, from which the data will be written
     * @return {@link Future}, using which it's possible to check the
     *         result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<WriteResult<Buffer, L>> write(Connection connection,
            Buffer buffer) throws IOException;

    /**
     * Method writes the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to write to
     * @param buffer the buffer, from which the data will be written
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when write will be completed
     * @return {@link Future}, using which it's possible to check the
     *         result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<WriteResult<Buffer, L>> write(Connection connection,
            Buffer buffer,
            CompletionHandler<WriteResult<Buffer, L>> completionHandler)
            throws IOException;

    /**
     * Method writes the <tt>buffer</tt> to the specific address.
     *
     * @param connection the {@link Connection} to write to
     * @param dstAddress the destination address the <tt>buffer</tt> will be
     *        sent to
     * @param buffer the buffer, from which the data will be written
     * @return {@link Future}, using which it's possible to check the
     *         result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<WriteResult<Buffer, L>> write(Connection connection, L dstAddress,
            Buffer buffer) throws IOException;

    /**
     * Method writes the <tt>buffer</tt> to the specific address.
     *
     * @param connection the {@link Connection} to write to
     * @param dstAddress the destination address the <tt>buffer</tt> will be
     *        sent to
     * @param buffer the buffer, from which the data will be written
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when write will be completed
     * @return {@link Future}, using which it's possible to check the
     *         result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<WriteResult<Buffer, L>> write(Connection connection,
            L dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, L>> completionHandler)
            throws IOException;

    /**
     * Method writes the <tt>buffer</tt> to the specific address.
     *
     * @param connection the {@link Connection} to write to
     * @param dstAddress the destination address the <tt>buffer</tt> will be
     *        sent to
     * @param buffer the buffer, from which the data will be written
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when write will be completed
     * @param interceptor {@link Interceptor}, which will be able to intercept
     *        control each time new portion of a data was written from a
     *        <tt>buffer</tt>.
     *        The <tt>interceptor</tt> can decide, whether asynchronous write is
     *        completed or not, or provide other processing instructions.
     * @return {@link Future}, using which it's possible to check the
     *         result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<WriteResult<Buffer, L>> write(Connection connection,
            L dstAddress, Buffer buffer,
            CompletionHandler<WriteResult<Buffer, L>> completionHandler,
            Interceptor<WriteResult<Buffer, L>> interceptor)
            throws IOException;   
}
