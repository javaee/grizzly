/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.grizzly.tcp.http11.filters;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.buf.ByteChunk;

import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.http11.OutputFilter;

/**
 * Gzip output filter.
 * 
 * @author Remy Maucherat
 */
public class GzipOutputFilter implements OutputFilter {

    private static final Logger LOGGER = LoggerUtils.getLogger();

    // -------------------------------------------------------------- Constants


    protected static final String ENCODING_NAME = "gzip";
    protected static final ByteChunk ENCODING = new ByteChunk();


    // ----------------------------------------------------- Static Initializer


    static {
        ENCODING.setBytes(ENCODING_NAME.getBytes(), 0, ENCODING_NAME.length());
    }


    // ----------------------------------------------------- Instance Variables

    private final ByteChunk alias;
    
    /**
     * Next buffer in the pipeline.
     */
    protected OutputBuffer buffer;


    /**
     * Compression output stream.
     */
    protected GZIPOutputStream compressionStream = null;


    /**
     * Fake internal output stream.
     */
    protected OutputStream fakeOutputStream = new FakeOutputStream();

    // --------------------------------------------------- Constructors

    public GzipOutputFilter() {
        alias = ENCODING;
    }

    public GzipOutputFilter(String alias) {
        ByteChunk bc = new ByteChunk();
        bc.setBytes(alias.getBytes(), 0, alias.length());
        this.alias = bc;
    }

    // --------------------------------------------------- OutputBuffer Methods


    /**
     * Write some bytes.
     * 
     * @return number of bytes written by the filter
     */
    public int doWrite(ByteChunk chunk, Response res)
        throws IOException {
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream);
        }
        compressionStream.write(chunk.getBytes(), chunk.getStart(), chunk.getLength());
        return chunk.getLength();
    }


    // --------------------------------------------------- OutputFilter Methods


    /**
     * Some filters need additional parameters from the response. All the 
     * necessary reading can occur in that method, as this method is called
     * after the response header processing is complete.
     */
    public void setResponse(Response response) {
    }


    /**
     * Set the next buffer in the filter pipeline.
     */
    public void setBuffer(OutputBuffer buffer) {
        this.buffer = buffer;
    }


    /**
     * End the current request. It is acceptable to write extra bytes using
     * buffer.doWrite during the execution of this method.
     */
    public long end()
        throws IOException {
        if (compressionStream == null) {
            compressionStream = new GZIPOutputStream(fakeOutputStream);
        }
        compressionStream.finish();
        return ((OutputFilter) buffer).end();
    }


    /**
     * Make the filter ready to process the next request.
     */
    public void recycle() {
        try {
            if (compressionStream != null) {
                compressionStream.close();
            }
        } catch (IOException ex) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, ex.toString(), ex);
            }
        } finally {
            compressionStream = null;
        }
    }


    /**
     * Return the name of the associated encoding; Here, the value is 
     * "identity".
     */
    public ByteChunk getEncodingName() {
        return alias;
    }


    // ------------------------------------------- FakeOutputStream Inner Class


    protected class FakeOutputStream
        extends OutputStream {
        protected ByteChunk outputChunk = new ByteChunk();
        protected byte[] singleByteBuffer = new byte[1];
        public void write(int b)
            throws IOException {
            // Shouldn't get used for good performance, but is needed for 
            // compatibility with Sun JDK 1.4.0
            singleByteBuffer[0] = (byte) (b & 0xff);
            outputChunk.setBytes(singleByteBuffer, 0, 1);
            buffer.doWrite(outputChunk, null);
        }
        public void write(byte[] b, int off, int len)
            throws IOException {
            outputChunk.setBytes(b, off, len);
            buffer.doWrite(outputChunk, null);
        }
        public void flush() throws IOException {}
        public void close() throws IOException {}
    }


}
