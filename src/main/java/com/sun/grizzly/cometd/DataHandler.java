/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.cometd;

import com.sun.enterprise.web.connector.grizzly.ConcurrentQueue;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.comet.CometContext;
import com.sun.enterprise.web.connector.grizzly.comet.CometEvent;
import com.sun.enterprise.web.connector.grizzly.comet.CometHandler;
import com.sun.enterprise.web.connector.grizzly.comet.CometInputStream;
import com.sun.grizzly.cometd.bayeux.DeliverResponse;
import com.sun.grizzly.cometd.bayeux.Verb;
import com.sun.grizzly.cometd.bayeux.VerbUtils;
import com.sun.grizzly.cometd.util.JSONParser;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CometHandler used to support the meta channel Verb Connect and Reconnect.
 * The DataHandler is holding the state of the long polled (Comet) connection.
 * 
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class DataHandler implements CometHandler<Object[]>{
    
    private final static Logger logger = SelectorThread.logger();
    
    private CometdRequest req;    
    
    private CometdResponse res; 
   
    private String clientId;
   
    private Collection<String> channels = new ConcurrentQueue<String>("DataHandler.channels");

    private Collection<String> unmodifiableChannels = null;
    
    private BayeuxCometHandler bayeuxCometHandler;

    private boolean wasLast = false;

    
    public DataHandler(BayeuxCometHandler bayeuxCometHandler){
        this.bayeuxCometHandler = bayeuxCometHandler;
    }
    
    
    private void writeEnd() throws IOException {
        if (!wasLast) {
            wasLast = true;
            res.write("]*/");
            res.flush();
        }
    }


    public void attach(Object[] reqRes){
        this.req = (CometdRequest) reqRes[0];
        this.res = (CometdResponse) reqRes[1];
        wasLast = false;
    }
            
        
    @SuppressWarnings("unchecked")
    public synchronized void onEvent(CometEvent event) throws IOException{ 
        Object obj = event.attachment();      
        try{                   
            if ("NOTIFY_END".equals(obj)) {
                CometContext cometContext = event.getCometContext();
                if (cometContext.isActive(this)) {
                    writeEnd();
                    cometContext.resumeCometHandler(this);
                }
                return;
            } else if (obj instanceof DeliverResponse){
                DeliverResponse deliverRes = (DeliverResponse)obj;
                String deliverResClientId = deliverRes.getClientId();
                if ((deliverResClientId != null &&
                    deliverResClientId.equals(getClientId()) ||
                    deliverResClientId == null && getClientId() == null)) { 
                    return;
                }
                
                wasLast = deliverRes.isLast();
                res.write(deliverRes.toJSON());
                res.flush();
            }
        }  catch (Throwable t){
           logger.log(Level.SEVERE,"Data.onEvent",t);
        } 
        
        if (event.getType()  == CometEvent.READ){
            CometInputStream is = (CometInputStream)obj;
            
            // XXX This is dangerous...
            byte[] dataStream = new byte[2 * 8192];
            is.setReadTimeout(2000);
            while (is.read(dataStream) > 0){
            }
            String sdata = new String(dataStream).trim();
            
            if (sdata.length() <=1) return;  
            
            try{
                List<Verb> verbs = 
                        VerbUtils.parseRequest(JSONParser.parse(sdata));                
                // Notify our listener;
                CometContext cometContext = event.getCometContext();
                for (Verb verb : verbs) {
                    cometContext.notify(
                        BayeuxCometHandler.newCometdContext(req,res,verb),
                        CometEvent.NOTIFY,
                        (Integer)cometContext.getAttribute(
                            BayeuxCometHandler.BAYEUX_COMET_HANDLER));
                }
                event.getCometContext().removeAttribute(this);
            } catch (Throwable t){
                logger.log(Level.SEVERE,"Data.onEvent",t);
            }
        }            
    }

    
    public void onInitialize(CometEvent event) throws IOException{  
    }


    public void onTerminate(CometEvent event) throws IOException{
        writeEnd();
    }

    public void onInterrupt(CometEvent event) throws IOException{       
    }

    public synchronized Collection<String> getChannels() {
        if (unmodifiableChannels == null) {
            unmodifiableChannels = Collections.unmodifiableCollection(channels);
        }
        return unmodifiableChannels;
    }

    public synchronized void addChannel(String channel) {
        if (channels.contains(channel)) {
            throw new IllegalArgumentException(channel);
        }

        unmodifiableChannels = null;
        channels.add(channel);
    }

    public synchronized boolean removeChannel(String channel) {
        unmodifiableChannels = null;
        return channels.remove(channel);
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
