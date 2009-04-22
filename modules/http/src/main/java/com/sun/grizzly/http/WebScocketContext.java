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

import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author gustav trede
 * @since 2009-04
 */
public class WebScocketContext {

    private final static ConcurrentHashMap<String,WebScocketContext> contexts
            = new ConcurrentHashMap<String, WebScocketContext>();
        

   // private final ConcurrentHashMap<WebSocketClient,Boolean> clients
     //       = new ConcurrentHashMap<WebSocketClient, Boolean>();    

    private final String name;
    
    private final WebSocketEventListener eventlistener;   
    
    private volatile boolean alive = true;

    private volatile boolean doManyClientIOPerThread = true;

    
    private WebScocketContext(String name, WebSocketEventListener eventlistener) {
        this.name = name;
        this.eventlistener = eventlistener;
    }

    public String getName() {
        return name;
    }

    public WebSocketEventListener getEventlistener() {
        return eventlistener;
    }

    public void setDoManyClientIOPerThread(boolean doManyClientIOPerThread) {
        this.doManyClientIOPerThread = doManyClientIOPerThread;
    }

    public boolean getDoManyClientIOPerThread() {
        return doManyClientIOPerThread;
    }



   /* boolean removeClient(WebSocketClient client){
        return clients.remove(client);
    }*/



    /**
     * Returns null if [@link WebScocketContext} does not exist.
     * @param request
     * @param response
     * @return
     */
     static WebSocketConnection clientConnected(String name, SelectionKey key){
        WebScocketContext context = contexts.get(name);
        if (context != null){
            WebSocketConnection client = new WebSocketConnection(context, key);
           // context.clients.put(client, Boolean.TRUE);
            if (context.alive){
                context.eventlistener.clientConnected(client);
                return client;
            }
            // we dont want to synchronize, so we simply remove
            //context.removeClient(client);
        }
        return null;
    }

    /**
     * Returns null if no [@link WebScocketContext} exists for the name.
     * @param name
     * @return
     */
    public static WebScocketContext getWebScocketContext(String name){
        return contexts.get(name);
    }
    
    public static WebScocketContext createWebScocketContext(String name,
            WebSocketEventListener eventlistener) throws WebScocketContextAlredyExists{
        WebScocketContext context = new WebScocketContext(name,eventlistener);
        if (contexts.putIfAbsent(name, context) == null){
            return context;
        }
        throw new WebScocketContextAlredyExists(name);
    }

    /**
     * Removes the [@link WebScocketContext} with the corresponding name and
     * returns it if it exits, returns null if not.
     * @param name
     * @return
     */
    public static WebScocketContext removeWebScocketContext(String name){
        WebScocketContext context = contexts.remove(name);
        if (context != null){
            context.alive = false;
            //todo cleanup etc ?
        }
        return context;
    }


    public static class WebScocketContextAlredyExists extends Exception{
        public WebScocketContextAlredyExists(String name) {
            super(name);
        }
    }
}
