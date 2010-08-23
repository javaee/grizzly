/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.servlet.ServletAdapter;
import com.sun.grizzly.util.Utils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.logging.Logger;

/**
 * {@link GrizzlyWebServer} tests.
 *
 * @author Hubert Iwaniuk
 * @since Jan 22, 2009
 */
public class ComplexGrizzlyWebServerTest extends GrizzlyWebServerAbstractTest {

    public static final int PORT = 18890 + 10;
    private static final Logger logger = Logger.getLogger("grizzly.test");

    /**
     * Want to test multiple servletMapping
     *
     * examples :
     *
     * context = /test
     * servletPath = /servlet1
     * mapping = *.1
     * mapping = /1
     *
     * URL = http://localhost:port/test/servlet1/test.1
     * URL = http://localhost:port/test/servlet1/1
     *
     * @throws IOException Error.
     */
    public void testComplexAliasMapping() throws IOException {
        Utils.dumpOut("testComplexAliasMapping");
        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[] { "/1", "/2", "/3", "/*.a" };
           
            String context = "/test";
            String servletPath = "/servlet";
            String rootFolder = ".";
           
            for (String alias : aliases) {
                addAdapter(context + servletPath + alias, context, servletPath, rootFolder);
            }

            for (String alias : aliases) {
                HttpURLConnection conn = getConnection(context + servletPath + alias, PORT);
                assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
                assertEquals(context + servletPath + alias, readResponse(conn));
            }
           
            //special test
            String url = context + servletPath + "/test.a";
            HttpURLConnection conn = getConnection(url, PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));
            assertEquals(url, readResponse(conn));
           
        } finally {
            stopGrizzlyWebServer();
        }
    }

    private ServletAdapter addAdapter(final String alias, final String context, final String servletPath, final String rootFolder) {
        ServletAdapter adapter = new ServletAdapter(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                logger.info(alias + " received request " + req.getRequestURI());
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().write(alias.indexOf("*") == 0 ? alias : req.getRequestURI());
            }
        });
       
        adapter.setContextPath(context);
        adapter.setServletPath(servletPath);
        adapter.setRootFolder(rootFolder);
       
       
        gws.addGrizzlyAdapter(adapter, new String[] { alias });
        return adapter;
    }
}
