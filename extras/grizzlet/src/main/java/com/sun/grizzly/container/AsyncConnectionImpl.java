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

package com.sun.grizzly.container;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.grizzlet.AsyncConnection;
import com.sun.grizzly.grizzlet.AlreadyPausedException;
import com.sun.grizzly.grizzlet.Grizzlet;
import com.sun.grizzly.grizzlet.NotYetPausedException;
import com.sun.grizzly.util.LinkedTransferQueue;
import java.io.IOException;
import java.util.Iterator;

/**
 * This class represent a possible asynchronous connection. An asynchronous
 * connection can always be suspended or resumed, its associated request
 * and response objects be used to construct a response, etc.
 * 
 * This class hook the Grizzlet with the underlying CometHandler.
 *
 * @author Jeanfrancois Arcand
 */
public class AsyncConnectionImpl implements AsyncConnection {

    private GrizzletRequest cometRequest;
    
    private GrizzletResponse cometResponse;
    
    public String message;
       
    private boolean isPaused = false;
    
    private CometContext cometContext;
        
    private static LinkedTransferQueue<CometHandler> handlers;
    
    private Grizzlet grizzlet;
    
    private boolean isResuming = false;
    
    private boolean hasPushEvent = false;
    
    
    public AsyncConnectionImpl(){
        handlers = new LinkedTransferQueue<CometHandler>();
    }
   
    public void setCometContext(CometContext cometContext){
       this.cometContext = cometContext;
    }

    
    public GrizzletRequest getRequest() {
        return cometRequest;
    }

    
    protected void setRequest(GrizzletRequest cometRequest) {
        this.cometRequest = cometRequest;
    }

    
    public GrizzletResponse getResponse() {
        return cometResponse;
    }

    
    protected void setResponse(GrizzletResponse cometResponse) {
        this.cometResponse = cometResponse;
    }

    public boolean isSuspended() {
        return isPaused;
    }

    public void suspend() throws AlreadyPausedException {
        if (isPaused) throw new AlreadyPausedException();
        isPaused = true;
        
        CometEvent event = new CometEvent();       
        GrizzletCometHandler cometHandler = new GrizzletCometHandler();
        cometHandler.setGrizzlet(grizzlet);
        cometHandler.attach(cometRequest);
        cometContext.addCometHandler(cometHandler);   
        handlers.add(cometHandler);
    }

    
    public void resume() throws NotYetPausedException {   
        Iterator<CometHandler> i = handlers.iterator();
        while(i.hasNext()){
            cometContext.removeCometHandler(i.next());
            i.remove();
        } 
        isPaused = false;
    }

    
    public boolean isResuming() {
        return isResuming;
    }
    
    public void setIsResuming(boolean isResuming){
        this.isResuming = isResuming;
    }
    

    public void push(String message) throws IOException{
        this.message = message;
        hasPushEvent = true;
        cometContext.notify(this);
        hasPushEvent = false;
    }
    
    
    public void sethasPushEvent(boolean hasPushEvent){
        this.hasPushEvent = hasPushEvent;
    }

    
    public boolean hasPushEvent() {
        return hasPushEvent;
    }

    
    public String getPushEvent() {
        return message;
    }

    
    public boolean isGet() {
        return cometRequest.getRequest().method().equals("GET");
    }

    
    public boolean isPost() {
        return cometRequest.getRequest().method().equals("POST");
    }

    public void setGrizzlet(Grizzlet grizzlet) {
        this.grizzlet = grizzlet;
    }
    
    public void recycle(){
        grizzlet = null;
        isPaused = false;
        isResuming = false;
        cometRequest = null;
        cometResponse = null;
        message = null;
        hasPushEvent = false;
    }

    /**
     * Return the number of suspended connections the current {@link Grizzlet}
     * is having.
     * 
     * @return the number of suspended connection.
     */
    public int getSuspendedCount() {
        return cometContext.getCometHandlers().size();
    }

}
