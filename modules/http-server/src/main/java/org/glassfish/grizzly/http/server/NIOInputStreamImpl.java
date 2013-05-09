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
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.http.io.InputBuffer;
import org.glassfish.grizzly.http.io.NIOInputStream;

/**
 * {@link NIOInputStream} implementation based on {@link InputBuffer}.
 * 
 * @author Ryan Lubke
 * @author Alexey Stashok
 */
final class NIOInputStreamImpl extends NIOInputStream implements Cacheable {

    private InputBuffer inputBuffer;


    // ------------------------------------------------ Methods from InputStream


    /**
     * {@inheritDoc}
     */
    @Override public int read() throws IOException {
        return inputBuffer.readByte();
    }


    /**
     * {@inheritDoc}
     */
    @Override public int read(byte[] b) throws IOException {
        return inputBuffer.read(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override public int read(byte[] b, int off, int len) throws IOException {
        return inputBuffer.read(b, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override public long skip(long n) throws IOException {
        return inputBuffer.skip(n);
    }

    /**
     * {@inheritDoc}
     */
    @Override public int available() throws IOException {
        return inputBuffer.available();
    }

    /**
     * {@inheritDoc}
     */
    @Override public void close() throws IOException {
        inputBuffer.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override public void mark(int readlimit) {
        inputBuffer.mark(readlimit);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void reset() throws IOException {
        inputBuffer.reset();
    }

    /**
     * This {@link NIOInputStream} implementation supports marking.
     *
     * @return <code>true</code>
     */
    @Override public boolean markSupported() {
        return inputBuffer.markSupported();
    }


    // --------------------------------------------- Methods from InputSource


    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyAvailable(ReadHandler handler) {
        inputBuffer.notifyAvailable(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyAvailable(ReadHandler handler, int size) {
        inputBuffer.notifyAvailable(handler, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFinished() {
        return inputBuffer.isFinished();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int readyData() {
        return inputBuffer.available();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReady() {
        return (inputBuffer.available() > 0);
    }


    // --------------------------------------- Methods from BinaryNIOInputSource

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getBuffer() {
        return inputBuffer.getBuffer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer readBuffer() {
        return inputBuffer.readBuffer();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer readBuffer(final int size) {
        return inputBuffer.readBuffer(size);
    }    
    // -------------------------------------------------- Methods from Cacheable


    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {

        inputBuffer = null;

    }


    // ---------------------------------------------------------- Public Methods


    public void setInputBuffer(final InputBuffer inputBuffer) {

        this.inputBuffer = inputBuffer;

    }
}
