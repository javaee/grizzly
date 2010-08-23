/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
import com.sun.grizzly.comet.handlers.ReflectorCometHandler;
import com.sun.grizzly.util.LoggerUtils;
import java.io.IOException;
import java.util.logging.Logger;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Twitter like Comet application. This {@link Servlet} implement the logic 
 * needed to support micro blogging a la Twitter.com. Users can blog about what 
 * they are doing and can also follow their friends. When an update is made
 * by one user, all its follower gets updated automatically. The updated words 
 * can be moved on the screen and all follower will see the move. 
 * 
 * This {@link Servlet} demonstrate how multiple {@link CometContext} can be 
 * used to easily isolate suspended connection (aka {@link CometHandler} to only
 * push messages to a subset of those suspended connection. It also demonstrate
 * how to push messages to a single {@link CometHandler}
 * 
 * There is one {@link CometContext} per user. {@link CometHandler} associated 
 * with the user suspended connection are added to their {@link CometContext}
 * and added to the {@link CometContext} of the users they are following. 
 *
 * @author Jeanfrancois Arcand
 */
public class TwitterServlet extends HttpServlet {

    // How long a suspended connection can be idle. -1 means no timeour
    private static final long DEFAULT_EXPIRATION_DELAY = -1;
    // {@link ServletContext}
    private ServletContext servletContext;
    // Simple transaction counter
    private int counter;
    // Begin Script
    private static final String BEGIN_SCRIPT_TAG = "<script type='text/javascript'>\n";
    //End script
    private static final String END_SCRIPT_TAG = "</script>\n";
    // Grizzly Logger
    private static final Logger logger = LoggerUtils.getLogger();
    // Unique id
    private static final long serialVersionUID = -2919167206889576860L;
    // Before suspending message
    private String startingMessage = "<html><head><title>Griztter</title></head><body bgcolor=\"#FFFFFF\">";
    // When terminate or interrupted event happens.
    private String endingMessage = "Griztter closed<br/>\n</body></html>";
    private final static String JUNK = "<!-- Comet is a programming technique that enables web " +
            "servers to send data to the client without having any need " +
            "for the client to request it. -->\n";

    public TwitterServlet() {
    }

    /**
     * Grab an instance of {@link ServletContext}
     * @param config
     * @throws javax.servlet.ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servletContext = config.getServletContext();
    }

    /**
     * Same as {@link TwitterServlet#doPost}
     * 
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    /**
     * Based on the {@link HttpServletRequest#getParameter} action value, decide
     * if the connection needs to be suspended (when the user logs in) or if the 
     * {@link CometContext} needs to be updated (by the user or by its follower.
     * 
     * There is one {@link CometContext} per suspended connection, representing 
     * the user account. When one user B request to follow user A, the {@link CometHandler}
     * associated with user B's {@link CometContext} is also added to user A
     * {@link CometContext}. Hence when user A push message ({@link CometContext.notify()}
     * all {@link CometHandler} gets the {@link CometEvent}, which means user B
     * will be updated when user A update its micro blog.
     * 
     * The suspended connection on the client side is multiplexed, e.g. 
     * messages sent by the server are not only for a single component, but
     * shared amongs several components. The client side include a message board
     * that is updated by notifying the owner of the {@link CometContext}. This
     * is achieved by calling {@link CometContext.notify(CometEvent,CometHandler)}
     * 
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String action = request.getParameter("action");

        String sessionId = request.getSession().getId();
        HttpSession session = request.getSession();
        CometContext twitterContext = (CometContext) session.getAttribute(sessionId);
        if (action != null) {
            /*
             * Notify the submitter, via its CometHandler, that it has just logged in.
             */
            if ("login".equals(action)) {
                response.setContentType("text/plain");
                response.setHeader("Cache-Control", "private");
                response.setHeader("Pragma", "no-cache");

                response.setCharacterEncoding("UTF-8");

                String name = request.getParameter("name");

                if (name == null) {
                    logger.severe("Name cannot be null");
                    return;
                }

                session.setAttribute("name", name);

                CometHandler ch = (CometHandler) session.getAttribute("handler");
                twitterContext.notify(BEGIN_SCRIPT_TAG + toJsonp("Welcome back", name) + END_SCRIPT_TAG, CometEvent.NOTIFY, ch);

                // Store the CometContext associated with this user so
                // we can retrieve it for supporing follower.
                servletContext.setAttribute(name, twitterContext);
            } else if ("post".equals(action)) {
                String message = request.getParameter("message");
                String callback = request.getParameter("callback");

                if (message == null) {
                    logger.severe("Message cannot be null");
                    return;
                }

                if (callback == null) {
                    callback = "alert";
                }

                if (twitterContext != null) {
                    // Notify other registered CometHandler.
                    twitterContext.notify("<script id='comet_" + counter++ + "'>"
                            + "window.parent." + callback + "(" + message + ");</script>");
                }
                response.getWriter().println("ok");
                return;
            } else if ("start".equals(action)) {
                String message = "{ message : 'Welcome'}";
                response.setContentType("text/html");
                // For IE, Safari and Chrome, we must output some junk to enable
                // streaming
                for (int i = 0; i < 10; i++) {
                    response.getWriter().write(JUNK);
                }

                String callback = request.getParameter("callback");
                if (callback == null) {
                    callback = "alert";
                }

                response.getWriter().println("<script id='comet_" + counter++ + "'>"
                        + "window.parent." + callback + "(" + message + ");</script>");

                // Create a CometContext based on this session id.
                twitterContext =
                        createCometContext(sessionId);

                // Create and register a CometHandler.
                ReflectorCometHandler handler = new ReflectorCometHandler(true, startingMessage, endingMessage);

                handler.attach(response.getWriter());
                twitterContext.addCometHandler(handler);

                // Keep a reference to us so we can be updated directly.
                twitterContext.addAttribute("twitterHandler", handler);

                session.setAttribute("handler", handler);
                session.setAttribute(sessionId, twitterContext);
                return;
            } else if ("following".equals(action)) {
                response.setContentType("text/html");

                String message = request.getParameter("message");
                String name = (String) session.getAttribute("name");

                if (message == null) {
                    logger.severe("Message cannot be null");
                    return;
                }

                if (name == null) {
                    logger.severe("Name cannot be null");
                    return;
                }

                // Retrive the user CometContext.
                CometContext followerContext = (CometContext) servletContext.getAttribute(message);


                CometHandler ch = (CometHandler) session.getAttribute("handler");
                if (followerContext == null) {
                    twitterContext.notify(BEGIN_SCRIPT_TAG +
                            toJsonp("Invalid Twitter user ", message) + END_SCRIPT_TAG, CometEvent.NOTIFY, ch);
                    return;
                }

                followerContext.addCometHandler(ch, true);

                twitterContext.notify(BEGIN_SCRIPT_TAG +
                        toJsonp("You are now following ", message) + END_SCRIPT_TAG, CometEvent.NOTIFY, ch);

                CometHandler twitterHandler =
                        (CometHandler) followerContext.getAttribute("twitterHandler");
                followerContext.notify(BEGIN_SCRIPT_TAG +
                        toJsonp(name, " is now following " + message) + END_SCRIPT_TAG, CometEvent.NOTIFY, twitterHandler);
                return;
            }
        }
    }

    /**
     * Create a {@link CometContext}
     * @param id - The topic assocated with the {@link CometContext}
     * @return {@link CometContext}
     */
    private CometContext createCometContext(String id) {
        CometEngine cometEngine = CometEngine.getEngine();
        CometContext ctx = cometEngine.register(id);
        ctx.setExpirationDelay(-1);
        return ctx;
    }

    /**
     * Escape any maliscious characters.
     * @param orig the String
     * @return a well formed String.
     */
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

    /**
     * Simple JSOn transformation.
     * @param name
     * @param message
     * @return the JSON representation.
     */
    private String toJsonp(String name, String message) {
        return "window.parent.app.update({ name: \"" + escape(name)
                + "\", message: \"" + escape(message) + "\" });\n";
    }
}
