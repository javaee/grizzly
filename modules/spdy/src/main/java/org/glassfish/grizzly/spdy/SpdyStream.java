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
package org.glassfish.grizzly.spdy;

import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.filterchain.FilterChainContext;

/**
 *
 * @author oleksiys
 */
public class SpdyStream {

    private final SpdyRequest spdyRequest;
    private final FilterChainContext upstreamContext;
    private final FilterChainContext downstreamContext;
    private final int streamId;
    private final int associatedToStreamId;
    private final int priority;
    private final int slot;
    private final SpdySession spdySession;
    
    private boolean isInputClosed;
    private final AtomicBoolean isOutputClosed = new AtomicBoolean();
    private final AtomicInteger completeCloseIndicator = new AtomicInteger();
    
    SpdyStream(final SpdySession spdySession,
            final SpdyRequest spdyRequest,
            final FilterChainContext upstreamContext,
            final FilterChainContext downstreamContext,
            final int streamId, final int associatedToStreamId,
            final int priority, final int slot) {
        this.spdySession = spdySession;
        this.spdyRequest = spdyRequest;
        this.upstreamContext = upstreamContext;
        this.downstreamContext = downstreamContext;
        this.streamId = streamId;
        this.associatedToStreamId = associatedToStreamId;
        this.priority = priority;
        this.slot = slot;
    }

    SpdySession getSpdySession() {
        return spdySession;
    }
    
    public SpdyRequest getSpdyRequest() {
        return spdyRequest;
    }
    
    public int getStreamId() {
        return streamId;
    }

    public int getAssociatedToStreamId() {
        return associatedToStreamId;
    }

    public int getPriority() {
        return priority;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isClosed() {
        return completeCloseIndicator.get() >= 2;
    }
    
    void writeDownStream(final Buffer frame) {
        writeDownStream(frame, null);
    }
    
    void writeDownStream(final Buffer frame,
            final CompletionHandler<WriteResult> completionHandler) {
        
        // Check if we can write (RST flag etc...)
        downstreamContext.write(frame, completionHandler);
    }
    
    FilterChainContext getUpstreamContext() {
        return upstreamContext;
    }

    FilterChainContext getDownstreamContext() {
        return downstreamContext;
    }
    
    void closeInput() {
        if (!isInputClosed) {
            isInputClosed = true;
            if (completeCloseIndicator.incrementAndGet() == 2) {
                closeStream();
            }
        }
    }
    
    void closeOutput() {
        if (isOutputClosed.compareAndSet(false, true)) {
            if (completeCloseIndicator.incrementAndGet() == 2) {
                closeStream();
            }
        }
    }

    
    private void closeStream() {
        spdySession.deregisterStream(this);
    }
    
}