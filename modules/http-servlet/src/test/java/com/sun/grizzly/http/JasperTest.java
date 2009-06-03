/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */
package com.sun.grizzly.http;

import com.sun.grizzly.http.servlet.ServletAdapter;
import org.apache.jasper.servlet.JspServlet;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.logging.Logger;

/**
 * Test case for JSP issue.
 * Based on <a href="https://grizzly.dev.java.net/issues/show_bug.cgi?id=615">#615</a>.
 *
 * @author Hubert Iwaniuk
 */
public class JasperTest extends GrizzlyWebServerAbstractTest {

    public static final int PORT = 18890 + 11;
    private static Logger logger = Logger.getLogger("grizzly.test");

    public void testJSP_Jasper() throws IOException {
        logger.info("testJSP_Jasper");

        try {
            startGrizzlyWebServer(PORT);
            String[] aliases = new String[]{"*.jsp"};

            String context = "/ctx";
            String servletPath = "/jsps";
            String rootFolder = ".";

            ServletAdapter adapter = new ServletAdapter();
            Servlet servlet = new JspServlet();
            adapter.setServletInstance(servlet);

            adapter.setContextPath(context);
            adapter.setServletPath(servletPath);
            adapter.setRootFolder(rootFolder);

            gws.addGrizzlyAdapter(adapter, aliases);

            gws.start();

            String url = context + servletPath + "/index.jsp";
            HttpURLConnection conn = getConnection(url, PORT);
            assertEquals(HttpServletResponse.SC_OK, getResponseCodeFromAlias(conn));

            String response = readResponse(conn);
            assertEquals(url, response.trim());

        } finally {
            stopGrizzlyWebServer();
        }
    }
}
