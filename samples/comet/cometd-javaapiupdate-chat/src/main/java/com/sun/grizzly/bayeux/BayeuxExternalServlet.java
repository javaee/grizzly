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

package com.sun.grizzly.bayeux;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.cometd.bayeux.Data;
import com.sun.grizzly.cometd.bayeux.DeliverResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Simple Servlet that update the Chat demo via a simple Http request
 * 
 * <code>
 * http://host:port/BayeuxExternalServlet/?message=hello,user=moi
 * </code>
 * 
 * @author Jeanfrancois Arcand
 */
public class BayeuxExternalServlet extends HttpServlet {
    
    
    /**
     * All request to that channel will be considered as cometd enabled.
     */
    private String channel ="/chat/demo";


    /**
     * Push message on the chat room every 10 seconds.
     */
    public ScheduledThreadPoolExecutor timer =
            new ScheduledThreadPoolExecutor(1);
    
    
    /**
     * Initialize the Servlet by creating the CometContext.
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        if (config.getInitParameter("channel") != null){
            channel = config.getInitParameter("channel");
        }
        timer.scheduleAtFixedRate(new Runnable(){
                public void run(){
                    CometEngine engine = CometEngine.getEngine();
                    CometContext context = engine.getCometContext(channel);

                    if (context != null) {
                        Map<String, Object> map = new HashMap<String, Object>();
                        map.put("chat", "Wake up call from the chatroom :-)");
                        map.put("user", "ChatPigner");
                        Data data = new Data();
                        data.setMapData(map);
                        data.setChannel("/chat/demo");

                        DeliverResponse deliverResponse = new DeliverResponse();
                        deliverResponse.setChannel("/chat/demo");
                        deliverResponse.setClientId("");
                        deliverResponse.setData(data);
                        deliverResponse.setLast(true);
                        deliverResponse.setFollow(true);
                        try{
                            context.notify(deliverResponse);
                        } catch (IOException ex) {};

                    }
                }
            }, 10, 10, TimeUnit.SECONDS);
       
    }

    @Override
    public void destroy(){
        timer.shutdown();
    }
    
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    /**
     * See 
     * @param request
     * @param response
     * @throws javax.servlet.ServletException
     * @throws java.io.IOException
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String message = request.getParameter("message");
        String user = request.getParameter("user");
        ServletOutputStream out = response.getOutputStream();

        CometEngine engine = CometEngine.getEngine();
        CometContext context = engine.getCometContext(channel);

        if (context != null && message != null) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("chat", message);
            map.put("user", user);
            Data data = new Data();
            data.setMapData(map);
            data.setChannel("/chat/demo");

            DeliverResponse deliverResponse = new DeliverResponse();
            deliverResponse.setChannel("/chat/demo");
            deliverResponse.setClientId("");
            deliverResponse.setData(data);
            deliverResponse.setLast(true);
            deliverResponse.setFollow(true);
            deliverResponse.setFinished(true);

            context.notify(deliverResponse);
            
            out.println("Data is sent.");
            System.out.println("Data is sent.");
        } else {
            out.println("No data is sent.");
            System.out.println("No data is sent.");
        }
    }
}

