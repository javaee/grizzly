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

package org.glassfish.grizzly;

import org.glassfish.grizzly.asyncqueue.WritableMessage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * A simple class that abstracts {@link FileChannel#transferTo(long, long, java.nio.channels.WritableByteChannel)}
 * for use with Grizzly 2.0 {@link org.glassfish.grizzly.asyncqueue.AsyncQueueWriter}.
 *
 * @since 2.2
 */
public class FileTransfer implements WritableMessage {
    
    private FileChannel fileChannel;
    private long len;
    private long pos;
    
    
    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new <code>FileTransfer</code> instance backed by the specified
     * {@link File}.  This simply calls <code>this(f, 0, f.length)</code>.
     * 
     * @param f the {@link File} to transfer.
     *
     * @throws NullPointerException if f is null.
     *
     * @see #FileTransfer(java.io.File, long, long)
     */
    public FileTransfer(final File f) {
        this(f, 0, f.length());
    }


    /**
     * Constructs a new <code>FileTransfer</code> instance backed by the specified
     * {@link File}.  The content to transfer will begin at the specified offset,
     * <code>pos</code> with the total transfer length being specified by 
     * <code>len</code>.
     * 
     * @param f the {@link File} to transfer.
     * @param pos the offset within the File to start the transfer.
     * @param len the total number of bytes to transfer.
     *
     * @throws IllegalArgumentException if <code>f</code> is null, does not exist, is
     *  not readable, or is a directory.
     * @throws IllegalArgumentException if <code>pos</code> or <code>len</code>
     *  are negative.
     * @throws IllegalArgumentException if len exceeds the number of bytes that
     *  may be transferred based on the provided offset and file length.
     */
    public FileTransfer(final File f, final long pos, final long len) {
        if (f == null) {
            throw new IllegalArgumentException("f cannot be null.");
        }
        if (!f.exists()) {
            throw new IllegalArgumentException("File " + f.getAbsolutePath() + " does not exist.");
        }
        if (!f.canRead()) {
            throw new IllegalArgumentException("File " + f.getAbsolutePath() + " is not readable.");
        }
        if (f.isDirectory()) {
            throw new IllegalArgumentException("File " + f.getAbsolutePath() + " is a directory.");
        }
        if (pos < 0) {
            throw new IllegalArgumentException("The pos argument cannot be negative.");
        }
        if (len < 0) {
            throw new IllegalArgumentException("The len argument cannot be negative.");
        }
        if (pos > f.length()) {
            throw new IllegalArgumentException("Illegal offset");
        }
        if (f.length() - pos < len) {
            throw new IllegalArgumentException("Specified length exceeds available bytes to transfer.");
        }
        
        this.pos = pos;
        this.len = len;
        try {
            fileChannel = new FileInputStream(f).getChannel();
        } catch (FileNotFoundException fnfe) {
            throw new IllegalStateException(fnfe);
        }
    }
    
    
    // ---------------------------------------------------------- Public Methods


    /**
     * Transfers the File backing this <code>FileTransfer</code> to the specified
     * {@link WritableByteChannel}.
     * 
     * @param c the {@link WritableByteChannel}
     * @return
     * @throws IOException
     */
    public long writeTo(final WritableByteChannel c) throws IOException {
        final long written = fileChannel.transferTo(pos, len, c);
        pos += written;
        len -= written;
        return written;
    }
    
    
    // ------------------------------------------ Methods from WritableMessage


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasRemaining() {
        return (len != 0);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int remaining() {
        return ((len > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) len);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean release() {
        try {
            fileChannel.close();
        } catch (IOException ignored) {
        } finally {
            fileChannel = null;
        }
        return true;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isExternal() {
        return true;
    }
}
