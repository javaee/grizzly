/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Future;

/**
 * Implementations of this interface are able to read data from
 * {@link Connection} to a {@link Buffer}.
 *
 * There are two basic Reader implementations in Grizzly:
 * {@link org.glassfish.grizzly.asyncqueue.AsyncQueueReader},
 * {@link org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorReader}.
 *
 * @param <L> the reader address type
 * 
 * @author Alexey Stashok
 */
public interface Reader<L> {
    public static final int READ_EVENT = 1;
    public static final int COMPLETE_EVENT = 2;
    public static final int INCOMPLETE_EVENT = 3;
    /**
     * Method reads data.
     *
     * @param connection the {@link Connection} to read from
     * @return {@link Future}, using which it's possible to check the result
     */
    public GrizzlyFuture<ReadResult<Buffer, L>> read(Connection<L> connection);

    /**
     * Method reads data to the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param buffer the buffer, where data will be read
     * @return {@link Future}, using which it's possible to check the result
     */
    public GrizzlyFuture<ReadResult<Buffer, L>> read(Connection<L> connection,
            Buffer buffer);

    /**
     * Method reads data to the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param buffer the buffer, where data will be read
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when read will be completed
     */
    public void read(Connection<L> connection,
            Buffer buffer,
            CompletionHandler<ReadResult<Buffer, L>> completionHandler);


    /**
     * Method reads data to the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param buffer the {@link Buffer} to which data will be read
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when read will be completed
     * @param interceptor {@link Interceptor}, which will be able to intercept
     *        control each time new portion of a data was read to a
     *        <tt>buffer</tt>.
     *        The <tt>interceptor</tt> can decide, whether asynchronous read is
     *        completed or not, or provide other processing instructions.
     */
    public void read(Connection<L> connection,
            Buffer buffer,
            CompletionHandler<ReadResult<Buffer, L>> completionHandler,
            Interceptor<ReadResult> interceptor);
}
