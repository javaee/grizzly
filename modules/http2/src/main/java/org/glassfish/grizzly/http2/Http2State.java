/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.util.concurrent.atomic.AtomicReference;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.http2.Http2FrameCodec.FrameParsingState;

/**
 *
 * @author oleksiys
 */
class Http2State {
    private static final Attribute<Http2State> http2State =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            Http2State.class.getName() + ".state");
    
    static Http2State getOrCreate(final Connection connection,
            final DraftVersion version) {
        
        Http2State state = http2State.get(connection);
        if (state == null) {
            state = new Http2State();
            http2State.set(connection, state);
        }
        
        return state;
    }
    
    static Http2State get(final Connection connection) {
        return http2State.get(connection);
    }

    static boolean isHttp2(final Connection connection) {
        final Http2State state = http2State.get(connection);
        
        return state != null && state.isHttp2();
    }

    static Http2State obtain(final Connection connection) {
        Http2State state = http2State.get(connection);
        if (state == null) {
            state = create(connection);
        }
        
        return state;
    }

    static Http2State create(final Connection connection) {
        final Http2State state = new Http2State();
        http2State.set(connection, state);
        
        return state;
    }

    static void remove(final Connection connection) {
        http2State.remove(connection);
    }
    
    private final AtomicReference<Status> status = new AtomicReference<Status>();
    private final FrameParsingState frameParsingState = new FrameParsingState();
    
    private Http2Connection http2Connection;

    private boolean isClientHttpUpgradeRequestFinished;
    private boolean isClientPrefaceSent;
    
    public enum Status {
        NEVER_HTTP2, HTTP_UPGRADE, DIRECT_UPGRADE, OPEN
    }

    public Http2State() {
        status.set(Status.HTTP_UPGRADE);
    }
    
    public Status getStatus() {
        return status.get();
    }
    
    /**
     * @return <tt>true</tt> if this connection is not HTTP2 and never
     *          will be in future, or <tt>false</tt> otherwise
     */
    boolean isNeverHttp2() {
        return status.get() == Status.NEVER_HTTP2;
    }

    /**
     * Marks the connection as never be used for HTTP2.
     */
    void setNeverHttp2() {
        status.set(Status.NEVER_HTTP2);
    }
    
    boolean isHttp2() {
        return !isNeverHttp2();
    }

    /**
     * @return <tt>true</tt> if HTTP2 connection received preface from the peer,
     *          or <tt>false</tt> otherwise
     */
    boolean isReady() {
        return status.get() == Status.OPEN;
    }

    /**
     * Confirms that HTTP2 connection received preface from the peer.
     */
    void setOpen() {
        status.set(Status.OPEN);
    }
    
    boolean isUpgradePhase() {
        final Status statusLocal = status.get();
        
        return statusLocal == Status.HTTP_UPGRADE ||
                statusLocal == Status.DIRECT_UPGRADE;
    }
    
    boolean isHttpUpgradePhase() {
        return status.get() == Status.HTTP_UPGRADE;
    }
    
    void finishHttpUpgradePhase() {
        status.compareAndSet(Status.HTTP_UPGRADE, Status.DIRECT_UPGRADE);
    }

    
    boolean isDirectUpgradePhase() {
        return status.get() == Status.DIRECT_UPGRADE;
    }

    void setDirectUpgradePhase() {
        status.set(Status.DIRECT_UPGRADE);
    }
    
    FrameParsingState getFrameParsingState() {
        return frameParsingState;
    }

    Http2Connection getHttp2Connection() {
        return http2Connection;
    }

    void setHttp2Connection(final Http2Connection http2Connection) {
        this.http2Connection = http2Connection;
        this.http2Connection.http2State = this;
    }

    
    /**
     * Client-side only. Invoked, when a client finishes sending plain HTTP/1.x
     * request containing HTTP2 upgrade headers.
     */
    void onClientHttpUpgradeRequestFinished() {
        isClientHttpUpgradeRequestFinished = true;
    }
    
    synchronized boolean tryLockClientPreface() {
        final Status s = status.get();
        if (!isClientPrefaceSent && isClientHttpUpgradeRequestFinished &&
                (s == Status.DIRECT_UPGRADE || s == Status.OPEN)) {
            isClientPrefaceSent = true;
            return true;
        }
        
        return false;
    }
}
