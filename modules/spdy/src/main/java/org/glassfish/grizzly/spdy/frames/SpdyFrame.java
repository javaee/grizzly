/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy.frames;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

public abstract class SpdyFrame implements Cacheable {

    protected SpdyHeader header;
    protected int flags;

    // ------------------------------------------------------------ Constructors


    protected SpdyFrame() { }


    // ---------------------------------------------------------- Public Methods


    public static SpdyFrame wrap(final Buffer buffer) {
        SpdyHeader header = SpdyHeader.wrap(buffer);
        if (header.control) {
            switch (header.type) {
                case SynStreamFrame.TYPE:
                    return SynStreamFrame.create(header);
                case SynReplyFrame.TYPE:
                    return SynReplyFrame.create(header);
                case RstStreamFrame.TYPE:
                    return RstStreamFrame.create(header);
                case SettingsFrame.TYPE:
                    return SettingsFrame.create(header);
                case PingFrame.TYPE:
                    return PingFrame.create(header);
                case GoAwayFrame.TYPE:
                    return GoAwayFrame.create(header);
                case HeadersFrame.TYPE:
                    return HeadersFrame.create(header);
                case WindowUpdateFrame.TYPE:
                    return WindowUpdateFrame.create(header);
                case CredentialFrame.TYPE:
                    return CredentialFrame.create(header);
                default:
                    throw new IllegalStateException("Unhandled control frame");

            }
        } else {
            return DataFrame.create(header);
        }
    }

    public boolean isFlagSet(final byte flag) {
        return (flags & flag) == flag;
    }

    public void setFlag(final byte flag) {
        if (header == null) {
            flags |= flag;
        }
    }

    public void clearFlag(final byte flag) {
        if (header == null) {
            flags &= ~flag;
        }
    }

    public SpdyHeader getHeader() {
        return header;
    }

    public abstract Buffer toBuffer(final MemoryManager memoryManager);


    // -------------------------------------------------- Methods from Cacheable


    @Override
    public void recycle() {
        if (header != null) {
            header.recycle();
        }
        flags = 0;
    }


    // ------------------------------------------------------- Protected Methods


    protected static Buffer allocateHeapBuffer(final MemoryManager mm, final int size) {
        if (!mm.willAllocateDirect(size)) {
            return mm.allocateAtLeast(size);
        } else {
            return Buffers.wrap(mm, new byte[size]);
        }
    }


    protected void initialize(final SpdyHeader header) {
        this.header = header;
        this.flags = header.flags;
    }

}
