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
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.http.io.NIOWriter;
import org.glassfish.grizzly.http.io.OutputBuffer;

/**
 * {@link NIOWriter} implementation.
 *
 * @author Ryan Lubke
 * @author Alexey Stashok
 */
final class NIOWriterImpl extends NIOWriter implements Cacheable {

    private OutputBuffer outputBuffer;


    // ----------------------------------------------------- Methods from Writer


    /**
     * {@inheritDoc}
     */
    @Override public void write(int c) throws IOException {
        outputBuffer.writeChar(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void write(char[] cbuf) throws IOException {
        outputBuffer.write(cbuf);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void write(char[] cbuf, int off, int len)
          throws IOException {
        outputBuffer.write(cbuf, off, len);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void write(String str) throws IOException {
        outputBuffer.write(str);
    }

    /**
     * {@inheritDoc}
     */
    @Override public void write(String str, int off, int len)
          throws IOException {
        outputBuffer.write(str, off, len);
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
     * @param length specifies the number of characters that require writing
     *
     * @return <code>true</code> if a write to this <code>OutputSink</code>
     *  will succeed, otherwise returns <code>false</code>.
     * 
     * @deprecated the <code>length</code> parameter will be ignored. Pls use {@link #canWrite()}.
     */
    @Override public boolean canWrite(final int length) {
        return outputBuffer.canWrite();
    }
    
    /**
     * @return <code>true</code> if a write to this <code>OutputSink</code>
     *  will succeed, otherwise returns <code>false</code>.
     * 
     * @since 2.3
     */
    @Override public boolean canWrite() {
        return outputBuffer.canWrite();
    }    

    /**
     * Instructs the <code>OutputSink</code> to invoke the provided
     * {@link WriteHandler} when it is possible to write <code>length</code>
     * characters.
     *
     * @param handler the {@link WriteHandler} that should be notified
     *  when it's possible to write <code>length</code> characters.
     * @param length the number of characters that require writing.
     * 
     * @deprecated the <code>length</code> parameter will be ignored. Pls. use {@link #notifyCanWrite(org.glassfish.grizzly.WriteHandler)}.
     */
    @Override
    public void notifyCanWrite(final WriteHandler handler, final int length) {
        outputBuffer.notifyCanWrite(handler);
    }

    /**
     * Instructs the <code>OutputSink</code> to invoke the provided
     * {@link WriteHandler} when it is possible to write more characters.
     *
     * @param handler the {@link WriteHandler} that should be notified
     *  when it's possible to write more characters.
     * 
     * @since 2.3
     */
    @Override
    public void notifyCanWrite(final WriteHandler handler) {
        outputBuffer.notifyCanWrite(handler);
    }
    
    // -------------------------------------------------- Methods from Cacheable


    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle() {

        outputBuffer = null;

    }


    // ---------------------------------------------------------- Public Methods


    public void setOutputBuffer(final OutputBuffer outputBuffer) {

        this.outputBuffer = outputBuffer;

    }
}
