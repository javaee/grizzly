/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.thrift;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.utils.BufferOutputStream;

import java.io.IOException;

/**
 * Abstract class for implementing thrift's TTransport.
 * By using BufferOutputStream, the output buffer will be increased automatically by given MemoryManager if it doesn't have enough spaces.
 *
 * @author Bongjae Chang
 */
public abstract class AbstractTGrizzlyTransport extends TTransport {

    @Override
    public void open() throws TTransportException {
    }

    @Override
    public int read(byte[] buf, int off, int len) throws TTransportException {
        final Buffer input = getInputBuffer();
        final int readableBytes = input.remaining();
        final int bytesToRead = len > readableBytes ? readableBytes : len;
        input.get(buf, off, bytesToRead);
        return bytesToRead;
    }

    @Override
    public void write(byte[] buf, int off, int len) throws TTransportException {
        final BufferOutputStream outputStream = getOutputStream();
        if (outputStream != null) {
            try {
                outputStream.write(buf, off, len);
            } catch (IOException ie) {
                throw new TTransportException(ie);
            }
        }
    }

    @Override
    public abstract void flush() throws TTransportException;

    protected abstract Buffer getInputBuffer() throws TTransportException;

    protected abstract BufferOutputStream getOutputStream() throws TTransportException;
}
