/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.io.OutputBuffer;

/**
 * {@link NIOOutputStream} implementation.
 *
 * @author Ryan Lubke
 * @author Alexey Stashok
 */
class NIOOutputStreamImpl extends NIOOutputStream implements Cacheable {


    private OutputBuffer outputBuffer;


    // ----------------------------------------------- Methods from OutputStream

    /**
     * {@inheritDoc}
     */
    @Override public void write(final int b) throws IOException {
        outputBuffer.writeByte(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void write(final byte[] b) throws IOException {
        outputBuffer.write(b);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void write(final byte[] b, final int off, final int len)
    throws IOException {
        outputBuffer.write(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void flush() throws IOException {
        outputBuffer.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override public void close() throws IOException {
        outputBuffer.close();
    }


    // ---------------------------------------------- Methods from OutputSink


    /**
     * {@inheritDoc}
     * 
     * @deprecated the <code>length</code> parameter will be ignored. Pls use {@link #canWrite()}.
     */
    @Override public boolean canWrite(final int length) {
        return outputBuffer.canWrite();
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean canWrite() {
        return outputBuffer.canWrite();
    }

    /**
     * {@inheritDoc}
     * @deprecated the <code>length</code> parameter will be ignored. Pls. use {@link #notifyCanWrite(org.glassfish.grizzly.WriteHandler)}.
     */
    @Override
    public void notifyCanWrite(final WriteHandler handler, final int length) {
        outputBuffer.notifyCanWrite(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyCanWrite(final WriteHandler handler) {
        outputBuffer.notifyCanWrite(handler);
    }

    // ---------------------------------------- Methods from BinaryNIOOutputSink


    /**
     * {@inheritDoc}
     */
    @Override
    public void write(final Buffer buffer) throws IOException {
        outputBuffer.writeBuffer(buffer);
    }


    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {

        outputBuffer = null;

    }


    // ---------------------------------------------------------- Public Methods


    public void setOutputBuffer(final OutputBuffer outputBuffer) {

        this.outputBuffer = outputBuffer;

    }

}
