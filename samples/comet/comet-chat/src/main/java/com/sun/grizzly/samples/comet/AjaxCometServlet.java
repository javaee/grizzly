/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.comet;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

public class AjaxCometServlet extends HttpServlet {
    private static final String BEGIN_SCRIPT_TAG = "<script type='text/javascript'>\n";
    private static final String END_SCRIPT_TAG = "</script>\n";
    private static final Logger logger = Logger.getLogger("grizzly");
    private static final long serialVersionUID = -2919167206889576860L;
    private String contextPath;
    private final static String JUNK = "<!-- Comet is a programming technique that enables web " +
            "servers to send data to the client without having any need " +
            "for the client to request it. -->\n";

    public class ChatListenerHandler implements CometHandler<PrintWriter> {

        private PrintWriter writer;

        public void attach(PrintWriter writer) {
            this.writer = writer;
        }

        public void onEvent(CometEvent event) throws IOException {
            if (event.getType() == CometEvent.NOTIFY) {
                String output = (String) event.attachment();
                logger.info("CometEvent.NOTIFY => {}" + output);

                writer.println(output);
                writer.flush();
            }
        }

        public void onInitialize(CometEvent event) throws IOException {
        }

        public void onInterrupt(CometEvent event) throws IOException {
            String script = BEGIN_SCRIPT_TAG + "window.parent.app.listen();\n" + END_SCRIPT_TAG;
            logger.info("CometEvent.INTERRUPT => {}" + script);
            writer.println(script);
            writer.flush();

            removeThisFromContext();
        }

        public void onTerminate(CometEvent event) throws IOException {
            removeThisFromContext();
        }

        private void removeThisFromContext() {
            writer.close();

            CometContext context = CometEngine.getEngine().getCometContext(contextPath);
            context.removeCometHandler(this);
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        contextPath = config.getServletContext().getContextPath() + "/chat";

        CometContext context = CometEngine.getEngine().register(contextPath);
        context.setExpirationDelay(5 * 60 * 1000);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/html");
        res.setHeader("Cache-Control", "private");
        res.setHeader("Pragma", "no-cache");

        PrintWriter writer = res.getWriter();
        // For IE, Safari and Chrome, we must output some junk to enable
        // streaming
        for (int i = 0; i < 10; i++) {
            res.getWriter().write(JUNK);
        }
        writer.flush();

        ChatListenerHandler handler = new ChatListenerHandler();
        handler.attach(writer);

        CometContext context = CometEngine.getEngine().register(contextPath);
        context.addCometHandler(handler);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/plain");
        res.setHeader("Cache-Control", "private");
        res.setHeader("Pragma", "no-cache");

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");
        String name = req.getParameter("name");

        CometContext context = CometEngine.getEngine().register(contextPath);

        logger.info("req.getParameter(\"action\") => {}" + action);

        if ("login".equals(action)) {
            context.notify(BEGIN_SCRIPT_TAG + toJsonp("System Message", name + " has joined.") + END_SCRIPT_TAG);

            res.getWriter().println("success");
        } else if ("post".equals(action)) {
            String message = req.getParameter("message");
            context.notify(BEGIN_SCRIPT_TAG + toJsonp(name, message) + END_SCRIPT_TAG);

            res.getWriter().println("success");
        } else {
            res.sendError(422, "Unprocessable Entity");
        }
    }

    private String escape(String orig) {
        StringBuffer buffer = new StringBuffer(orig.length());

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\'':
                    buffer.append("\\'");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                default:
                    buffer.append(c);
            }
        }

        return buffer.toString();
    }

    private String toJsonp(String name, String message) {
        return "window.parent.app.update({ name: \"" + escape(name) + "\", message: \"" + escape(message) + "\" });\n";
    }
}
