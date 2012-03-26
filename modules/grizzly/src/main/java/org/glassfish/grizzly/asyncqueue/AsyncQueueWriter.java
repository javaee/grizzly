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

package org.glassfish.grizzly.asyncqueue;

import java.net.SocketAddress;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.nio.NIOConnection;

/**
 * The {@link AsyncQueue}, which implements asynchronous write queue.
 *
 * @author Alexey Stashok
 * @author Ryan Lubke
 */
public interface AsyncQueueWriter<L> 
        extends Writer<L>, AsyncQueue {
    
    /**
     * Constant set via {@link #setMaxPendingBytesPerConnection(int)} means
     * the async write queue size is unlimited.
     */
    public static final int UNLIMITED_SIZE = -1;
    
    /**
     * Constant set via {@link #setMaxPendingBytesPerConnection(int)} means
     * the async write queue size will be configured automatically per
     * {@link NIOConnection} depending on connections write buffer size.
     */
    public static final int AUTO_SIZE = -2;
    
    /**
     * Method writes the {@link Buffer} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     * @param lifeCycleHandler {@link LifeCycleHandler}, which gives developer
     *        finer control over message write process.
     * @param cloner {@link MessageCloner}, which will be invoked by
     *        <tt>AsyncQueueWriter</tt>, if message could not be written to a
     *        channel directly and has to be put on a asynchronous queue    
     */
    public void write(
            Connection connection, SocketAddress dstAddress, WritableMessage message,
            CompletionHandler<WriteResult<WritableMessage, SocketAddress>> completionHandler,
            LifeCycleHandler lifeCycleHandler,
            MessageCloner<WritableMessage> cloner);

    /**
     * Configures the maximum number of bytes pending to be written
     * for a particular {@link Connection}.
     *
     * @param maxQueuedWrites maximum number of bytes that may be pending to be
     *  written to a particular {@link Connection}.
     */
    void setMaxPendingBytesPerConnection(final int maxQueuedWrites);


    /**
     * @return the maximum number of bytes that may be pending to be written
     *  to a particular {@link Connection}.  By default, this will be four
     *  times the size of the {@link java.net.Socket} send buffer size.
     */
    int getMaxPendingBytesPerConnection();    
}
