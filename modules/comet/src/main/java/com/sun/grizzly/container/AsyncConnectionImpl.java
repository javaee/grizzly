/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.container;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.grizzlet.AsyncConnection;
import com.sun.grizzly.grizzlet.AlreadyPausedException;
import com.sun.grizzly.grizzlet.Grizzlet;
import com.sun.grizzly.grizzlet.NotYetPausedException;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

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
        
    private static ConcurrentLinkedQueue<CometHandler> handlers;
    
    private Grizzlet grizzlet;
    
    private boolean isResuming = false;
    
    private boolean hasPushEvent = false;
    
    
    public AsyncConnectionImpl(){
        handlers = new ConcurrentLinkedQueue<CometHandler>();
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

}
