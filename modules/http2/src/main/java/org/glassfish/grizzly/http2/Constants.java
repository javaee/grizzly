/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.http2.Http2Stream.Termination;
import org.glassfish.grizzly.http2.Http2Stream.TerminationType;
import org.glassfish.grizzly.utils.Charsets;

/**
 *
 * @author oleksiys
 */
public class Constants {
    
    static final String AUTHORITY_HEADER = ":authority";
    static final byte[] AUTHORITY_HEADER_BYTES = AUTHORITY_HEADER.getBytes(Charsets.ASCII_CHARSET);

    static final String METHOD_HEADER = ":method";
    static final byte[] METHOD_HEADER_BYTES = METHOD_HEADER.getBytes(Charsets.ASCII_CHARSET);

    static final String PATH_HEADER = ":path";
    static final byte[] PATH_HEADER_BYTES = PATH_HEADER.getBytes(Charsets.ASCII_CHARSET);

    static final String SCHEMA_HEADER = ":scheme";
    static final byte[] SCHEMA_HEADER_BYTES = SCHEMA_HEADER.getBytes(Charsets.ASCII_CHARSET);
    
    static final String STATUS_HEADER = ":status";
    static final byte[] STATUS_HEADER_BYTES = STATUS_HEADER.getBytes(Charsets.ASCII_CHARSET);
    
    static final Termination IN_FIN_TERMINATION =
            new Termination(TerminationType.FIN, "End of input");
    
    static final Termination OUT_FIN_TERMINATION =
            new Termination(TerminationType.FIN, "The output stream has been closed");
    
    static final String CLOSED_BY_PEER_STRING = "Closed by peer";
    
    static final Termination LOCAL_CLOSE_TERMINATION =
            new Termination(TerminationType.LOCAL_CLOSE, "Closed locally");
    
    static final Termination PEER_CLOSE_TERMINATION =
            new Termination(TerminationType.PEER_CLOSE, CLOSED_BY_PEER_STRING);
    
    static final Termination RESET_TERMINATION =
            new Termination(TerminationType.RST, "Reset by peer");

    static final Termination UNEXPECTED_FRAME_TERMINATION =
            new Termination(TerminationType.LOCAL_CLOSE, "Unexpected SPDY frame");

    static final Termination FRAME_TOO_LARGE_TERMINATION =
            new Termination(TerminationType.LOCAL_CLOSE, "SpdyFrame sent by peer is too big");
}
