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
import java.util.concurrent.Future;

/**
 * Implementations of this interface are able to read data from
 * {@link Connection} to a {@link Buffer}.
 *
 * There are two basic Reader implementations in Grizzly:
 * {@link com.sun.grizzly.asyncqueue.AsyncQueueReader},
 * {@link com.sun.grizzly.nio.tmpselectors.TemporarySelectorReader}.
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
     * @throws java.io.IOException
     */
    public GrizzlyFuture<ReadResult<Buffer, L>> read(Connection connection)
            throws IOException;

    /**
     * Method reads data to the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param buffer the buffer, where data will be read
     * @return {@link Future}, using which it's possible to check the result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<ReadResult<Buffer, L>> read(Connection connection,
            Buffer buffer) throws IOException;

    /**
     * Method reads data to the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param buffer the buffer, where data will be read
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when read will be completed
     * @return {@link Future}, using which it's possible to check the result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<ReadResult<Buffer, L>> read(Connection connection,
            Buffer buffer,
            CompletionHandler<ReadResult<Buffer, L>> completionHandler)
            throws IOException;


    /**
     * Method reads data to the <tt>buffer</tt>.
     *
     * @param connection the {@link Connection} to read from
     * @param message the Message to which data will be read
     * @param completionHandler {@link CompletionHandler},
     *        which will get notified, when read will be completed
     * @param interceptor {@link Interceptor}, which will be able to intercept
     *        control each time new portion of a data was read to a
     *        <tt>buffer</tt>.
     *        The <tt>interceptor</tt> can decide, whether asynchronous read is
     *        completed or not, or provide other processing instructions.
     * @return {@link Future}, using which it's possible to check the result
     * @throws java.io.IOException
     */
    public GrizzlyFuture<ReadResult<Buffer, L>> read(Connection connection,
            Buffer buffer,
            CompletionHandler<ReadResult<Buffer, L>> completionHandler,
            Interceptor<ReadResult> interceptor)
            throws IOException;
}
