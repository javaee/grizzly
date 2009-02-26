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

package org.glassfish.grizzly.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Writable;

/**
 * Bridge between {@link org.glassfish.grizzly.Writable} and {@link OutputStream}
 * 
 * @author Alexey Stashok
 */
public class WritableOutputStream extends OutputStream {
    private Writable writable;
    private Buffer buffer;
    private long timeout = 30000;

    public Buffer getBuffer() {
        return buffer;
    }

    public void setBuffer(Buffer buffer) {
        this.buffer = buffer;
    }

    public Writable getWritable() {
        return writable;
    }

    public void setWritable(Writable writable) {
        this.writable = writable;
    }

    public long getTimeout(TimeUnit unit) {
        return unit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = TimeUnit.MILLISECONDS.convert(timeout, unit);
    }

    @Override
    public void write(int b) throws IOException {
        if (!buffer.hasRemaining()) {
            flush();
        }

        buffer.put((byte) b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        int written = 0;
        while(written < len) {
            if (!buffer.hasRemaining()) {
                flush();
            }

            int bytesToWrite = Math.min(len - written, buffer.remaining());
            buffer.put(b, off + written, bytesToWrite);
            written += bytesToWrite;
        }
    }

    @Override
    public void flush() throws IOException {
        Future future = writable.write(buffer);

        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted exception");
        } catch (TimeoutException e) {
            throw new IOException("Timeout excepting during flushing data");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException(cause.getClass().getName() +
                        ": " + cause.getMessage());
            }
        } finally {
            buffer.clear();
        }
    }

    @Override
    public void close() throws IOException {
        writable.close();
    }
}
