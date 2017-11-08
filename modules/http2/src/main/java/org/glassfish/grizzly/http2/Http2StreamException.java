/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import org.glassfish.grizzly.http2.frames.ErrorCode;

/**
 * HTTP/2 Stream exception.
 * 
 * @author Alexey Stashok
 */
public final class Http2StreamException extends IOException {
    private final int streamId;
    private final ErrorCode errorCode;
    
    public Http2StreamException(final int streamId, final ErrorCode errorCode) {
        this.streamId = streamId;
        this.errorCode = errorCode;
    }

    public Http2StreamException(final int streamId, final ErrorCode errorCode,
            final String description) {
        super(description);
        
        this.streamId = streamId;
        this.errorCode = errorCode;
    }

    public Http2StreamException(final int streamId, final ErrorCode errorCode,
            final Throwable cause) {
        super(cause);
        
        this.streamId = streamId;
        this.errorCode = errorCode;
    }
    
    public Http2StreamException(final int streamId, final ErrorCode errorCode,
            final String description, final Throwable cause) {
        super(description, cause);
        
        this.streamId = streamId;
        this.errorCode = errorCode;
    }

    public int getStreamId() {
        return streamId;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(128);
        sb.append(getClass().getName())
                .append(" streamId=").append(streamId)
                .append(" errorCode=").append(errorCode);

        String message = getLocalizedMessage();
        
        return message != null ?
                (sb.append(": ").append(message).toString()) : sb.toString();
    }    
}
