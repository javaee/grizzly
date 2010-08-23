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

package com.sun.grizzly.messagesbus;

import java.io.IOException;

import java.io.UnsupportedEncodingException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;

import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.comet.NotificationHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet implementation of the Grizzly Comet Protocol (GCP). The GCP protocol
 * is a very basic protocol that can be used by browser to share data, using the
 * comet technique, between serveral clients without having to poll for it. 
 * 
 * The protocol is very simple. First, a client must subscribe to a topic:
 * <p><pre><code>
 * http://host:port/contextPath?
 *    subscribe=[topic name]&cometTechnique=[polling|log-polling|http-streaming]&message[text]
 * 
 * Mandatory: subscribe and cometTechnique.
 * </code></pre></p>
 * When issuing the URL above, the connection will be automatically suspended
 * based on the <strong>cometTechnique</strong> specified. To share data
 * between application, a browser just need to send the following request:
 * <p><pre><code>
 * http://host:port/contextPath?publish=[topic name]&message[text] 
 * </code></pre></p>
 * The Servlet can be used as it is or extended to add extra features like
 * filtering messages, security, login, etc.
 * 
 * To use this Servlet, just add in your web.xml:
 * 
 * <p><pre><code>
    &lt;?xml version="1.0" encoding="UTF-8"?&gt;
    &lt;web-app xmlns="http://java.sun.com/xml/ns/j2ee"
      xmlns:j2ee="http://java.sun.com/xml/ns/j2ee"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.4"
      xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd"&gt;
      &lt;description&gt;Grizzly Messages Bus Servlet&lt;/description&gt;
      &lt;display-name&gt;Grizzly Messages Bus Servlet&lt;/display-name&gt;
      &lt;servlet&gt;
        &lt;description&gt;MessagesBus&lt;/description&gt;
        &lt;servlet-name&gt;MessagesBus&lt;/servlet-name&gt;
        &lt;servlet-class&gt;com.sun.grizzly.messagesbus.MessagesBus&lt;/servlet-class&gt;
        &lt;load-on-startup&gt;0&lt;/load-on-startup&gt;
      &lt;/servlet&gt;
      &lt;servlet-mapping&gt;
        &lt;servlet-name&gt;MessagesBus&lt;/servlet-name&gt;
        &lt;url-pattern&gt;/mb&lt;/url-pattern&gt;
      &lt;/servlet-mapping&gt;
      &lt;session-config&gt;
        &lt;session-timeout&gt;25&lt;/session-timeout&gt;
      &lt;/session-config&gt;
    &lt;/web-app&gt;
 * </code></pre></p>
 * 
 * By default, a connection will be timed out after a {@link #expirationDelay}.
 * The default is 30 seconds, but this can be configured by adding, in web.xml:
 * <p><pre><code>
   &lt;init-param&gt;
        &lt;param-name&gt;expirationDelay&lt;/param-name&gt;
        &lt;param-value&gt;60000&lt;/param-value&gt;
   &lt;/init-param&gt;
 * </code></pre></p>
 * 
 * The Grizzly Comet {@link NotificationHandler} can also be replaced by adding:
 * <p><pre><code>
   &lt;init-param&gt;
        &lt;param-name&gt;notificationHandler&lt;/param-name&gt;
        &lt;param-value&gt;com.foo.bar.ScriptFilterNotificationHandler&lt;/param-value&gt;
   &lt;/init-param&gt;
 * </code></pre></p>
 * 
 * A request can also be automatically suspended when the request URI only 
 * contains the <strong>publish</strong> GCP action.
 * <p><pre><code>
   &lt;init-param&gt;
        &lt;param-name&gt;suspendOnTheFly&lt;/param-name&gt;
        &lt;param-value&gt;true&lt;/param-value&gt;
   &lt;/init-param&gt;
 * </code></pre></p> 
 * 
 * 
 * @author Jeanfrancois Arcand
 */
public class MessagesBus extends HttpServlet {

    /**
     * The Comet technique supported.
     */ 
    public enum CometType {
        POLLING, LONG_POLLING, HTTP_STREAMING
    }
    
    // 30 seconds by default.
    private long expirationDelay = 30 * 1000;
    
    
    /**
     * The specified {@link NotificationHandler}. When not specified,
     * 
     * 
     */
    private NotificationHandler notificationHandler = null; 
    
    
    /**
     * Default Logger.
     */
    private static final Logger logger = Logger.getAnonymousLogger();

    
    /**
     * <tt>true</tt> if a connection can be automatically suspended
     * when the request only contains the GCP message <strong>publish</strong>
     * Default is <tt>true</tt>
     */
    private boolean suspendOnTheFly = true;
    
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        String ed = config.getInitParameter("expirationDelay");
        if (ed != null) {
            expirationDelay = Long.parseLong(ed);
        }
        
        String nh = config.getInitParameter("notificationHandler");
        if (ed != null) {
            notificationHandler = (NotificationHandler)loadClass(nh);
        }   
        
        String stf = config.getInitParameter("suspendOnTheFly");
        if (stf != null) {
            suspendOnTheFly = Boolean.valueOf(stf);
        } 
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doPost(req, res);
    }


    /**
     * Basic implementation of the Grizzly Comet Protocol (GCP). 
     * @param req The request
     * @param res The response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        CometType cometType = suspend(req, res);
        res.setContentType("text/html");

        // The connection has been suspended.
        if (cometType != null && cometType != CometType.POLLING) {
            res.flushBuffer();
            return;
        }

        push(req,res);
    }

    
    /**
     * Inspect the request, looking for the GCP parameters <strong>publish</strong>
     * and <strong>message</strong>. If those parameters are included, the 
     * message will be pushed to all subscriber of the publish's value.
     * 
     * @param req The http request
     * @param res The http response.
     * @throws java.io.IOException
     */
    protected synchronized void push(HttpServletRequest req, HttpServletResponse res) 
            throws IOException {
        String message = req.getParameter("message");
        String topic = req.getParameter("publish");

        // Nothing to send
        if (message == null || topic == null) {
            return;
        }
        
        CometContext context = CometEngine.getEngine().getCometContext(topic);
        if (context == null && suspendOnTheFly) {
            context = createCometContext(topic);
            CometType cometType = CometType.LONG_POLLING;
            CometHandler cometHandler = new MessagesBusCometHandler(context, cometType);
            cometHandler.attach(res);
            context.addCometHandler(cometHandler);            
        } else if (!suspendOnTheFly) {            
            if (logger.isLoggable(Level.INFO)) {
                logger.info("Cannot create message topic:" + topic 
                        + " on the fly. You first have to " + "subscribe");
            }            
            return;
        }
        context.notify(message);
    }


    /**
     * Suspend the connection if the request contains the GCP 
     * <strong>suscribe</strong> and  <strong>cometTechnique</strong> action.
     * @param req 
     * @param res
     * @return {@link CometType} used to suspend the connection.
     * @throws java.io.UnsupportedEncodingException
     * @throws java.io.IOException
     */
    protected synchronized CometType suspend(HttpServletRequest req, HttpServletResponse res)
            throws UnsupportedEncodingException, IOException {
        String cometTechnique = req.getParameter("cometTechnique");
        String topic = req.getParameter("subscribe");
        String message = req.getParameter("message");
        
        // This is not a request for suspending the connection.
        if (cometTechnique == null || topic == null) {
            return CometType.POLLING;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("CometTechnique: " + cometTechnique + " topic:" + topic);
        }

        if (cometTechnique.equals("polling")) {
            return CometType.POLLING;
        }

        CometContext context = CometEngine.getEngine().getCometContext(topic);
        if (context == null) {
            context = createCometContext(topic);
        }

        CometType cometType = null;
        if (cometTechnique.equals("long-polling")) {
            cometType = CometType.LONG_POLLING;
        } else if (cometTechnique.equals("http-streaming")) {
            cometType = CometType.HTTP_STREAMING;
        }

        CometHandler cometHandler = new MessagesBusCometHandler(context, cometType);
        cometHandler.attach(res);
        
        if (message != null){
            context.notify(message);
            // Echo 
            res.getWriter().write(message);
        }
        
        context.addCometHandler(cometHandler);
        return cometType;
    }

    
    /**
     * Create a Grizzly Comet {@link CometContext} 
     * @param topic The name of the  {@link CometContext} 
     * @return a cached or newly created  {@link CometContext} 
     */
    protected final CometContext createCometContext(String topic){
        CometContext  context = CometEngine.getEngine().register(topic);
        if (notificationHandler != null){
            context.setNotificationHandler(notificationHandler);
        }
        context.setExpirationDelay(expirationDelay);
        context.setBlockingNotification(true);
        return context;
    }
    
    
    /**
     * Util to load classes using reflection.
     */
    private static Object loadClass(String clazzName) {
        Class className = null;
        try {
            className = Class.forName(clazzName, true,
                    Thread.currentThread().getContextClassLoader());
            return className.newInstance();
        } catch (Throwable t) {
            logger.log(Level.SEVERE,"Invalid NotificationHandler",t);
        }

        return null;
    }
    
    /**
     * Return the time a connection can stay idle.
     * @return
     */
    public long getExpirationDelay() {
        return expirationDelay;
    }

    
    /**
     * Set the maximum idle time a connection can stay suspended.
     * @param expirationDelay
     */
    public void setExpirationDelay(long expirationDelay) {
        this.expirationDelay = expirationDelay;
    }

}
