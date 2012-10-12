/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.websockets.draft17;

import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.websockets.WebSocketEngine;
import org.glassfish.grizzly.websockets.draft08.HandShake08;

import java.net.URI;

import static org.glassfish.grizzly.websockets.WebSocketEngine.ORIGIN_HEADER;
import static org.glassfish.grizzly.websockets.WebSocketEngine.SEC_WS_ORIGIN_HEADER;

public class HandShake17 extends HandShake08 {


    // ------------------------------------------------------------ Constructors


    public HandShake17(URI uri) {
        super(uri);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public HandShake17(HttpRequestPacket request) {
        super(request);    //To change body of overridden methods use File | Settings | File Templates.
    }


    // -------------------------------------------------- Methods from HandShake

    @Override
    protected int getVersion() {
        return 13;
    }

    @Override
    public HttpContent composeHeaders() {
        HttpContent content = super.composeHeaders();
        HttpHeader header = content.getHttpHeader();
        final String headerValue = header.getHeaders().getHeader(SEC_WS_ORIGIN_HEADER);
        header.getHeaders().removeHeader(SEC_WS_ORIGIN_HEADER);
        header.addHeader(ORIGIN_HEADER, headerValue);
        return content;
    }
}
