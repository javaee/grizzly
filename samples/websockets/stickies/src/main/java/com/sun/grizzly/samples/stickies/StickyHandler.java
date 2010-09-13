/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.stickies;

import java.io.IOException;
import java.util.Arrays;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;

public class StickyHandler implements CometHandler<HttpServletResponse> {
    private static final String BEGIN_SCRIPT_TAG = "<script type='text/javascript'>alert('response!');";
    private static final String END_SCRIPT_TAG = "</script>\n";
    private HttpServletResponse response;
    private CometServlet cometServlet;

    public StickyHandler(final CometServlet cometServlet) {
        this.cometServlet = cometServlet;
    }

    public void write(String text) throws IOException {
        ServletOutputStream writer = response.getOutputStream();
        writer.println(BEGIN_SCRIPT_TAG /*+ text */+ END_SCRIPT_TAG);
//        for (int i = 0; i < 10000; i++) {
//            writer.println(CometServlet.JUNK);
//        }
        writer.flush();
    }

    public void attach(HttpServletResponse attachment) {
        response = attachment;
    }

    public void onEvent(CometEvent event) throws IOException {
        System.out.println("CometServlet$StickyHandler.onEvent: event.getType() = " + event.getType());
        final String[] split = String.valueOf(event.attachment()).split("-");
        System.out.println("CometServlet$StickyHandler.onEvent: split = " + Arrays.toString(split));
        final CometServlet.CometOperations operation;
        try {
            operation = CometServlet.CometOperations.valueOf(split[0].toUpperCase());
            System.out.println("StickyHandler.onEvent: operation = " + operation);
            operation.accept(cometServlet, this, split);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
//            event.getCometContext().resumeCometHandler(this);
    }

    public void onInitialize(CometEvent event) throws IOException {
        System.out.println("CometServlet$StickyHandler.onInitialize");
//            event.getCometContext().resumeCometHandler(this);
    }

    public void onTerminate(CometEvent event) throws IOException {
        System.out.println("CometServlet$StickyHandler.onTerminate");
    }

    public void onInterrupt(CometEvent event) throws IOException {
        System.out.println("CometServlet$StickyHandler.onInterrupt");
    }
}
