/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.grizzly.http.ajp;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class AjpPacketTest extends AjpTestBase {
    @Test
    public void forwardWgetRequest() throws IOException {
        AjpForwardRequestPacket forward = new AjpForwardRequestPacket("GET", "//ajpindex.html", 1025, 61878);
        forward.addHeader("User-Agent", "Wget/1.13 (darwin10.8.0)");
        forward.addHeader("Accept", "*/*");
        forward.addHeader("Host", "localhost:1025");
        forward.addHeader("Connection", "Keep-Alive");

        Assert.assertArrayEquals(read("/request.txt").array(), forward.toBuffer().array());
    }
    @Test
    public void forwardFireFoxRequest() throws IOException {
        AjpForwardRequestPacket forward = new AjpForwardRequestPacket("GET", "//index.html", 1025, 56599);
        forward.addHeader("Host", "localhost:1025");
        forward.addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.6; rv:5.0.1) Gecko/20100101 Firefox/5.0.1");
        forward.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        forward.addHeader("Accept-Language", "en-us,en;q=0.5");
        forward.addHeader("Accept-Encoding", "gzip, deflate");
        forward.addHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");
        forward.addHeader("Connection", "keep-alive");

        Assert.assertArrayEquals(read("/request2.txt").array(), forward.toBuffer().array());
    }
}
