/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.asyncqueue.WritableMessage;

/**
 * Implementations of this interface are able to write data from a {@link Buffer}
 * to {@link Connection}.
 *
 * There are two basic Writer implementations in Grizzly:
 * {@link org.glassfish.grizzly.asyncqueue.AsyncQueueWriter},
 * {@link org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorWriter}.
 *
 * @param <L> the writer address type
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("deprecation")
public interface Writer<L> {
    /**
     * Method writes the {@link WritableMessage}.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @return {@link Future}, using which it's possible to check the
     *         result
     */
    GrizzlyFuture<WriteResult<WritableMessage, L>> write(Connection<L> connection,
                                                         WritableMessage message) throws IOException;

    /**
     * Method writes the {@link WritableMessage}.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     */
    void write(Connection<L> connection,
               WritableMessage message,
               CompletionHandler<WriteResult<WritableMessage, L>> completionHandler);

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @return {@link Future}, using which it's possible to check the
     *         result
     */
    GrizzlyFuture<WriteResult<WritableMessage, L>> write(Connection<L> connection,
                                                         L dstAddress, WritableMessage message);

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     */
    void write(Connection<L> connection,
               L dstAddress, WritableMessage message,
               CompletionHandler<WriteResult<WritableMessage, L>> completionHandler);
    
    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     * @param pushBackHandler {@link org.glassfish.grizzly.asyncqueue.PushBackHandler}, which will be notified
     *        if message was accepted by transport write queue or refused
     * @deprecated push back logic is deprecated
     */
    void write(
            Connection<L> connection,
            L dstAddress, WritableMessage message,
            CompletionHandler<WriteResult<WritableMessage, L>> completionHandler,
            org.glassfish.grizzly.asyncqueue.PushBackHandler pushBackHandler);

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     * @param messageCloner the {@link MessageCloner}, which will be able to
     *          clone the message in case it can't be completely written in the
     *          current thread.
     */
    void write(
            Connection<L> connection,
            L dstAddress, WritableMessage message,
            CompletionHandler<WriteResult<WritableMessage, L>> completionHandler,
            MessageCloner<WritableMessage> messageCloner);

    /**
         * Return <code>true</code> if the connection has not exceeded it's maximum
         *  size in bytes of pending writes, otherwise <code>false</code>.
         *
         * @param connection the {@link Connection} to test whether or not the
         *  specified number of bytes can be written to.
         * @return <code>true</code> if the connection has not exceeded it's maximum
         *  size in bytes of pending writes, otherwise <code>false</code>
         *
         * @since 2.3
         */
        boolean canWrite(final Connection<L> connection);

        /**
         * Registers {@link WriteHandler}, which will be notified ones at least one
         * byte can be written.
         *
         * This method call is equivalent to call notifyWritePossible(connection, writeHandler, <tt>1</tt>);
         *
         * Note: using this method from different threads simultaneously may lead
         * to quick situation changes, so at time {@link WriteHandler} is called -
         * the queue may become busy again.
         *
         * @param connection {@link Connection}
         * @param writeHandler {@link WriteHandler} to be notified.
         *
         * @since 2.3
         */
        void notifyWritePossible(final Connection<L> connection,
                final WriteHandler writeHandler);


    /**
     * Write reentrants counter
     */
    final class Reentrant {
        private static final ThreadLocal<Reentrant> REENTRANTS_COUNTER =
                new ThreadLocal<Reentrant>() {

                    @Override
                    protected Reentrant initialValue() {
                        return new Reentrant();
                    }
                };

        private static final int maxWriteReentrants = Integer.getInteger(
                "org.glassfish.grizzly.Writer.max-write-reentrants", 10);

        /**
         * Returns the maximum number of write() method reentrants a thread is
         * allowed to made. This is related to possible
         * write()->onComplete()->write()->... chain, which may grow infinitely
         * and cause StackOverflow. Using maxWriteReentrants value it's possible
         * to limit such a chain.
         *
         * @return the maximum number of write() method reentrants a thread is
         *         allowed to make.
         */
        public static int getMaxReentrants() {
            return maxWriteReentrants;
        }

        /**
         * Returns the current write reentrants counter. Might be useful, if
         * developer wants to use custom notification mechanism, based on on {@link #canWrite(org.glassfish.grizzly.Connection)}
         * and various write methods.
         */
        public static Reentrant getWriteReentrant() {
            // ThreadLocal otherwise
            return REENTRANTS_COUNTER.get();
        }

        private int counter;

        /**
         * Returns the value of the reentrants counter for the current thread.
         */
        public int get() {
            return counter;
        }

        /**
         * Increments the reentrants counter by one.
         *
         * @return <tt>true</tt> if the counter (after incrementing) didn't reach
         *         {@link #getMaxReentrants()} limit, or <tt>false</tt> otherwise.
         */
        public boolean inc() {
            return ++counter <= maxWriteReentrants;
        }

        /**
         * Decrements the reentrants counter by one.
         *
         * @return <tt>true</tt> if the counter (after decrementing) didn't reach
         *         {@link #getMaxReentrants()} limit, or <tt>false</tt> otherwise.
         */
        public boolean dec() {
            return --counter <= maxWriteReentrants;
        }

        /**
         * Returns <tt>true</tt>, if max number of write->completion-handler
         * reentrants has been reached for the passed {@link Reentrant} object,
         * and next write will happen in the separate thread.
         *
         * @return <tt>true</tt>, if max number of write->completion-handler
         *         reentrants has been reached for the passed {@link Reentrant} object,
         *         and next write will happen in the separate thread.
         */
        public boolean isMaxReentrantsReached() {
            return get() >= getMaxReentrants();
        }
    }
}
