/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

/**
 * 
 */
public class Termination {

    static final Termination IN_FIN_TERMINATION =
            new Termination(TerminationType.FIN, "End of input", false);

    static final Termination OUT_FIN_TERMINATION =
            new Termination(TerminationType.FIN, "The output stream has been closed", false);

    static final String CLOSED_BY_PEER_STRING = "Closed by peer";

    static final Termination LOCAL_CLOSE_TERMINATION =
            new Termination(TerminationType.LOCAL_CLOSE, "Closed locally", true);

    static final Termination PEER_CLOSE_TERMINATION =
            new Termination(TerminationType.PEER_CLOSE, CLOSED_BY_PEER_STRING, true);

    static final Termination RESET_TERMINATION =
            new Termination(TerminationType.RST, "Reset by peer", false);

    static final Termination UNEXPECTED_FRAME_TERMINATION =
            new Termination(TerminationType.LOCAL_CLOSE, "Unexpected HTTP/2 frame", false);

    static final Termination FRAME_TOO_LARGE_TERMINATION =
            new Termination(TerminationType.LOCAL_CLOSE, "HTTP/2 frame sent by peer is too large", false);

    static final String HTTP2_PUSH_ENABLED = "http2-push-enabled";

    enum TerminationType {
        FIN, RST, LOCAL_CLOSE, PEER_CLOSE
    }

    private final TerminationType type;
    private final String description;
    private final boolean sessionClosed;

    public Termination(final TerminationType type,
                       final String description,
                       final boolean sessionClosed) {
        this.type = type;
        this.description = description;
        this.sessionClosed = sessionClosed;
    }

    public TerminationType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSessionClosed() {
        return sessionClosed;
    }

    public void doTask() {
    }
}
