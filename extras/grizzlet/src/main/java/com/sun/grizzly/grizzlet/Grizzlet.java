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

package com.sun.grizzly.grizzlet;

import java.io.IOException;


/**
 * This interface defines the contract between the Grizzlet Container
 * (GrizzlyComet). Implementation of this interface will enable the Grizzly 
 * Comet Web Server (or Grizzlet Container) to send data to its subcribed 
 * clients without having any need for the client to request for it. 
 * 
 * It allows creation of event-driven web applications which are hosted 
 * in the browser.
 *
 * This interface is the only component needed by an application that 
 * want to implement support for Asynchronous Request Processing or
 * Comet Request Processing.
 *
 * For example, an AJAX based Chat application will only have to define the 
 * following
 *
 * <p><pre><code>
    public void onRequest(AsyncConnection event) throws IOException {
        GrizzletRequest req = event.getRequest();
        GrizzletResponse res = event.getResponse();
                
        res.setContentType("text/html");
        res.addHeader("Cache-Control", "private");
        res.addHeader("Pragma", "no-cache");                
        if (event.isGet()){                             
            res.write("<!-- Comet is a programming technique that enables web " +
                    "servers to send data to the client without having any need " +
                    "for the client to request it. -->\n");
            res.flush();
            event.suspend();
            return;
        } else if (event.isPost()){
            res.setCharacterEncoding("UTF-8");
            String action = req.getParameterValues("action")[0];
            String name = req.getParameterValues("name")[0];

            if ("login".equals(action)) {
                event.push(
                        BEGIN_SCRIPT_TAG + toJsonp("System Message", name 
                            + " has joined.") + END_SCRIPT_TAG);
                res.write("success");                 
                res.flush();
            } else if ("post".equals(action)) {                                
                String message = req.getParameterValues("message")[0];
                event.push(BEGIN_SCRIPT_TAG 
                        + toJsonp(name, message) + END_SCRIPT_TAG);
                res.write("success");                 
                res.flush();                
            } else {
                res.setStatus(422);
                res.setMessage("Unprocessable Entity");
                                                
                res.write("success");                 
                res.flush();
            }
        }          
    }
    
    
    public void onPush(AsyncConnection event) throws IOException{        
        GrizzletRequest req = event.getRequest();
        GrizzletResponse res = event.getResponse();
             
        // The connection will be closed, flush the last String.   
        if (event.isResuming()){
            String script = BEGIN_SCRIPT_TAG 
                + "window.parent.app.listen();\n" 
                + END_SCRIPT_TAG;

            res.write(script);
            res.flush();
            res.finish();   
            return;            
        } else if (event.hasPushEvent()){
            // The Chatroom has been updated, push the data to our
            // subscribed clients.
            res.write(event.getPushEvent().toString());
            res.flush();  
            return;
        }   
    }
 *
 * </code></pre></p>
 *
 * @author Jeanfrancois Arcand
 */
public interface Grizzlet {
    
    /**
     * When a client send a request to its associated Grizzlet, it can decide
     * if the underlying connection can be suspended (creating a Continuation)
     * or handle the connection synchronously. 
     *
     * It is recommended to only suspend request for which HTTP method is a GET
     * and use the POST method to send data to the server, without marking the
     * connection as asynchronous.
     *
     * @param asyncConnection An object representing an asynchronous connection.
     */
    public void onRequest(AsyncConnection asyncConnection) throws IOException;
    
    
    /**
     * This method is invoked when the Grizzlet Container execute a push 
     * operations. When this method is invoked by the Grizzlet Container, any
     * suspended connection will be allowed to push the data back to its
     * associated clients. 
     *
     * @param asyncConnection An object representing an asynchronous connection.
     */
    public void onPush(AsyncConnection asyncConnection) throws IOException;
   
}
