/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import org.glassfish.grizzly.spdy.frames.GoAwayFrame;
import org.glassfish.grizzly.spdy.frames.RstStreamFrame;

/**
 * SPDY Session exception.
 * 
 * Unlike {@link SpdyStreamException}, this exception means severe problem
 * related to the entire SPDY session.
 * 
 * @author Alexey Stashok
 */
public final class SpdySessionException extends IOException {
    private final int streamId;
    private final int rstReason;
    private final int goAwayStatus;

    /**
     * Construct <tt>SpdySessionException</tt>.
     * 
     * @param streamId
     * @param goAwayStatus
     */
    public SpdySessionException(final int streamId, final int goAwayStatus) {
        this.streamId = streamId;
        this.goAwayStatus = goAwayStatus;
        rstReason = -1;
    }

    /**
     * Construct <tt>SpdySessionException</tt>.
     * If <tt>rstReason</tt> parameter is less than zero - the SPDY
     * implementation has to send {@link RstStreamFrame} with the specified
     * <tt>streamId</tt> and <tt>rstReason</tt> before sending {@link GoAwayFrame}.
     * 
     * @param streamId
     * @param goAwayStatus
     * @param rstReason 
     */
    public SpdySessionException(final int streamId, final int goAwayStatus,
            final int rstReason) {
        this.streamId = streamId;
        this.goAwayStatus = goAwayStatus;
        this.rstReason = rstReason;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getGoAwayStatus() {
        return goAwayStatus;
    }

    /**
     * RstFrame reason, if not <tt>-1</tt> RstFrame frame will be sent before GoAway.
     * @return RstFrame reason, if not <tt>-1</tt> RstFrame frame will be sent before GoAway.
     */
    public int getRstReason() {
        return rstReason;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(getClass().getName())
                .append(" streamId=").append(streamId)
                .append(" rstReason=").append(rstReason)
                .append(" goAwayStatus=").append(goAwayStatus);

        String message = getLocalizedMessage();
        
        return message != null ?
                (sb.append(": ").append(message).toString()) : sb.toString();
    }
}
