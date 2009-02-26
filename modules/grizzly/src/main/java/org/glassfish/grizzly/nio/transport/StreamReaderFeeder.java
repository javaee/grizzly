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
package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.util.logging.Level;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Interceptor;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.ReadResult;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueProcessor;
import org.glassfish.grizzly.asyncqueue.AsyncReadQueueRecord;
import org.glassfish.grizzly.impl.ProcessorLockImpl;
import org.glassfish.grizzly.streams.StreamReader;
import org.glassfish.grizzly.util.ProcessorWrapper;

public class StreamReaderFeeder implements Interceptor<ReadResult> {

    private TCPNIOTransport transport;

    protected StreamReaderFeeder(TCPNIOTransport transport) {
        this.transport = transport;
    }

    public int intercept(int event,
            Object context, ReadResult readResult) {
        if (event == Reader.AFTER_READ_EVENT) {
            return interceptRead(context, readResult);
        } else if (event == Reader.INCOMPLETE_EVENT) {
            AsyncReadQueueRecord record = (AsyncReadQueueRecord) context;
            Runnable postProcessor = record.getPostProcesser();
            record.setPostProcesser(null);
            if (postProcessor != null) {
                postProcessor.run();
            }
        }

        return Interceptor.DEFAULT;
    }

    private int interceptRead(Object context, ReadResult readResult) {
        Buffer buffer = (Buffer) readResult.getMessage();
        if (buffer == null) {
            return Interceptor.DEFAULT;
        }

        int instructions = Interceptor.DEFAULT;

        final TCPNIOConnection connection =
                (TCPNIOConnection) readResult.getConnection();
        buffer.flip();

        final StreamReader reader = connection.getStreamReader();
        if (!reader.receiveData(buffer)) {
            return AsyncQueueProcessor.NOT_REGISTER_KEY;
        } else {
            readResult.setMessage(null);
            Processor processor = transport.getConnectionProcessor(connection,
                    IOEvent.READ);
            if (processor != null) {
                final ProcessorLockImpl lock = (ProcessorLockImpl) connection.obtainProcessorLock(IOEvent.READ);
                lock.getInternalUpdateLock().writeLock().lock();
                if (!lock.isLocked()) {
                    final ProcessorWrapper wrapper = new ProcessorWrapper(processor) {

                        @Override
                        public ProcessorResult process(Context context)
                                throws IOException {
                            ProcessorResult result = super.process(context);
                            if (result == null || result.getStatus() == Status.OK) {
                                lock.getInternalUpdateLock().writeLock().lock();
                                if (lock.isEventUpdated()) {
                                    lock.setEventUpdated(false);
                                    lock.getInternalUpdateLock().writeLock().unlock();
                                    return new ProcessorResult(Status.RERUN,
                                            "StreamReader has data available");
                                } else {
                                    lock.unlock();
                                    lock.getInternalUpdateLock().writeLock().unlock();
                                }
                            }

                            return result;
                        }
                    };

                    lock.lock(wrapper);
                    lock.getInternalUpdateLock().writeLock().unlock();
                    ((AsyncReadQueueRecord) context).setPostProcesser(
                            new Runnable() {

                        public void run() {
                            try {
                                transport.executeProcessor(IOEvent.READ,
                                        connection, wrapper,
                                        transport.sameThreadProcessorExecutor,
                                        null);
                            } catch (IOException e) {
                                Grizzly.logger.log(Level.FINE,
                                        "IOException happened in StreamReaderFeeder", e);
                                throw new IllegalStateException(e);
                            } catch (Exception e) {
                                Grizzly.logger.log(Level.SEVERE,
                                        "Unexpected exception happened in StreamReaderFeeder", e);
                                throw new IllegalStateException(e);
                            }
                        }
                    });
                } else {
                    lock.setEventUpdated(true);
                    lock.getInternalUpdateLock().writeLock().unlock();
                }
            }
        }
        return instructions;
    }
}
