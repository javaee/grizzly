/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
package org.glassfish.grizzly.streams;

import java.io.Closeable;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;

/** Write the primitive Java types and arrays of primitives to some data sink.  
 * This may include internal buffering for efficiency reasons.
 * @author Ken Cavanaugh
 */
public interface StreamWriter extends Closeable {
    public enum Mode {
        NON_BLOCKING, BLOCKING;
    }

    public Mode getMode();

    public void setMode(Mode mode);

    /** Return the ByteOrder of the stream.
     * All streams default to big endian byte order.
     */
    ByteOrder order();

    /** Set the ByteOrder of the stream.
     */
    void order(ByteOrder byteOrder);

    /**
     * Make sure that all data that has been written is
     * flushed from the stream to its destination.
     */
    Future flush() throws IOException;

    /**
     * Make sure that all data that has been written is
     * flushed from the stream to its destination.
     */
    Future flush(CompletionHandler completionHandler) throws IOException;

    /**
     * Close the {@link StreamWriter} and make sure all data was flushed.
     */
    Future close(CompletionHandler completionHandler) throws IOException;

    void writeBoolean(boolean data) throws IOException;

    void writeByte(byte data) throws IOException;

    void writeChar(char data) throws IOException;

    void writeShort(short data) throws IOException;

    void writeInt(int data) throws IOException;

    void writeLong(long data) throws IOException;

    void writeFloat(float data) throws IOException;

    void writeDouble(double data) throws IOException;

    void writeBooleanArray(boolean[] data) throws IOException;

    void writeByteArray(byte[] data) throws IOException;

    void writeCharArray(final char[] data) throws IOException;

    void writeShortArray(short[] data) throws IOException;

    void writeIntArray(int[] data) throws IOException;

    void writeLongArray(long[] data) throws IOException;

    void writeFloatArray(float[] data) throws IOException;

    void writeDoubleArray(double[] data) throws IOException;

    /**
     * Puts {@link StreamReader} available data to this {@link StreamWriter}
     * This method will make possible direct writing from {@link StreamReader},
     * avoiding {@link Buffer} copying.
     *
     * @param stream {@link StreamReader}
     */
    void writeStream(StreamReader stream) throws IOException;

    Connection getConnection();

    Buffer getBuffer();

    int getBufferSize();

    void setBufferSize(int size);

    long getTimeout(TimeUnit timeunit);
    void setTimeout(long timeout, TimeUnit timeunit);

    /* We need a means to remember positions within the stream that
     *  can be restored
     * later, so that we can 
     *
     * Need this in reader and writer!
    
    / ** Save the current position in the stream for possible later use.
     * Saving a Position on a stream has implications on buffer usage: the
     * buffers in use that include the Position and after it will NOT be
     * sent to the overflow handler until the Position is closed.
     * /
    Position save() ;
    
    / ** Restore the current position in the stream to the Position.
     * /
    void restore( Position pos ) ; 
     */
}

