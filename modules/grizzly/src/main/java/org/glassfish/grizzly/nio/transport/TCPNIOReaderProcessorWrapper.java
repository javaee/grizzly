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
import java.nio.channels.SelectionKey;
import org.glassfish.grizzly.AbstractProcessor;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.ProcessorResult.Status;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.NIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOReaderProcessorWrapper.ReaderWrapperContext;
import org.glassfish.grizzly.streams.StreamReader;

/**
 *
 * @author Alexey Stashok
 */
public class TCPNIOReaderProcessorWrapper extends AbstractProcessor<ReaderWrapperContext> {
    private static final int DEFAULT_READ_SIZE = 16384;
    
    private int readBufferSize = DEFAULT_READ_SIZE;

    @Override
    public ReaderWrapperContext context() {
        return new ReaderWrapperContext();
    }


    @Override
    public void beforeProcess(ReaderWrapperContext context) throws IOException {
        if (!context.getProcessorExecutor().isCurrentThreadExecutor()) {
            NIOConnection connection = (NIOConnection) context.getConnection();
            NIOTransport transport = (NIOTransport) connection.getTransport();
            transport.getSelectorHandler().unregisterKey(
                    connection.getSelectorRunner(),
                    connection.getSelectionKey(),
                    SelectionKey.OP_READ);
        }
    }

    public ProcessorResult process(ReaderWrapperContext context) throws IOException {
        Connection connection = (Connection) context.getConnection();
        StreamReader streamReader = connection.getStreamReader();

        MemoryManager manager = connection.getTransport().getMemoryManager();
        Buffer buffer = manager.allocate(readBufferSize);

        ((TCPNIOTransport) connection.getTransport()).read(connection, buffer);
        
        buffer.trim();
        streamReader.receiveData(buffer);
        return new ProcessorResult(Status.OK);
    }

    @Override
    public void afterProcess(ReaderWrapperContext context) throws IOException {
        ProcessorExecutor executor = null;
        
        TCPNIOConnection connection = (TCPNIOConnection) context.getConnection();
        TCPNIOTransport transport = (TCPNIOTransport) connection.getTransport();
        if (!context.getProcessorExecutor().isCurrentThreadExecutor()) {
            transport.getSelectorHandler().registerKey(
                    connection.getSelectorRunner(),
                    connection.getSelectionKey(),
                    SelectionKey.OP_READ);
            executor = transport.sameThreadProcessorExecutor;
        }

        transport.executeProcessor(IOEvent.READ,
                connection, context.customProcessor, executor, null);
    }

    public boolean isInterested(IOEvent ioEvent) {
        return ioEvent == IOEvent.READ;
    }

    public void setInterested(IOEvent ioEvent, boolean isInterested) {
    }

    public static class ReaderWrapperContext extends Context {

        private Processor customProcessor;

        public Processor getCustomProcessor() {
            return customProcessor;
        }

        public void setCustomProcessor(Processor customProcessor) {
            this.customProcessor = customProcessor;
        }

        @Override
        public void release() {
            customProcessor = null;
            super.release();
        }
    }
}
