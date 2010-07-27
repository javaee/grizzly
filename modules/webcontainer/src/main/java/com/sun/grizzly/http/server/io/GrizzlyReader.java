/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.server.io;

import com.sun.grizzly.Buffer;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

/**
 * {@link Reader} implementation to be used to read character-based request
 * content.
 */
public class GrizzlyReader extends Reader implements NioInputSource {

    private final InputBuffer inputBuffer;


    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new <code>GrizzlyReader</code> using the specified
     * {@link #inputBuffer}
     * @param inputBuffer the <code>InputBuffer</code> from which character
     *  content will be supplied
     */
    public GrizzlyReader(InputBuffer inputBuffer) {

        this.inputBuffer = inputBuffer;

    }


    // ----------------------------------------------------- Methods from Reader


    /**
     * {@inheritDoc}
     */
    @Override public int read(CharBuffer target) throws IOException {
        return inputBuffer.read(target);
    }

    /**
     * {@inheritDoc}
     */
    @Override public int read() throws IOException {
        return inputBuffer.readChar();
    }

    /**
     * {@inheritDoc}
     */
    @Override public int read(char[] cbuf) throws IOException {
        return inputBuffer.read(cbuf, 0, cbuf.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override public long skip(long n) throws IOException {
        return inputBuffer.skip(n, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override public boolean ready() throws IOException {
        return inputBuffer.ready();
    }

    /**
     * This {@link Reader} implementation does not support marking.
     *
     * @return <code>false</code>
     */
    @Override public boolean markSupported() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override public void mark(int readAheadLimit) throws IOException {
        throw new IOException();
    }

    /**
     * {@inheritDoc}
     */
    @Override public void reset() throws IOException {
        throw new IOException();
    }

    /**
     * {@inheritDoc}
     */
    @Override public int read(char[] cbuf, int off, int len)
    throws IOException {
        return inputBuffer.read(cbuf, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void close() throws IOException {
        inputBuffer.close();
    }


    // --------------------------------------------- Methods from NioInputSource


    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyAvailable(DataHandler handler) {
        inputBuffer.notifyAvailable(handler);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyAvailable(DataHandler handler, int size) {
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
        return inputBuffer.availableChar();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isReady() {
        return (inputBuffer.availableChar() > 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Buffer getBuffer() {
        return inputBuffer.getBuffer();
    }
}
