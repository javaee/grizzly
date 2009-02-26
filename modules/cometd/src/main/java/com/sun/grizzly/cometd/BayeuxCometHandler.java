/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.cometd;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.cometd.bayeux.ConnectRequest;
import com.sun.grizzly.cometd.bayeux.ConnectResponse;
import com.sun.grizzly.cometd.bayeux.Advice;
import com.sun.grizzly.cometd.bayeux.Data;
import com.sun.grizzly.cometd.bayeux.DeliverResponse;
import com.sun.grizzly.cometd.bayeux.DisconnectRequest;
import com.sun.grizzly.cometd.bayeux.DisconnectResponse;
import com.sun.grizzly.cometd.bayeux.HandshakeRequest;
import com.sun.grizzly.cometd.bayeux.HandshakeResponse;
import com.sun.grizzly.cometd.bayeux.PublishRequest;
import com.sun.grizzly.cometd.bayeux.PublishResponse;
import com.sun.grizzly.cometd.bayeux.ReconnectRequest;
import com.sun.grizzly.cometd.bayeux.ReconnectResponse;
import com.sun.grizzly.cometd.bayeux.SubscribeRequest;
import com.sun.grizzly.cometd.bayeux.SubscribeResponse;
import com.sun.grizzly.cometd.bayeux.UnsubscribeRequest;
import com.sun.grizzly.cometd.bayeux.UnsubscribeResponse;
import com.sun.grizzly.cometd.bayeux.Verb;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class implement the Bauyeux Server side protocol. 
 *
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class BayeuxCometHandler extends BayeuxCometHandlerBase{
    
    
    public final static String DEFAULT_CONTENT_TYPE ="application/json-comment-filtered";
            
    public final static String BAYEUX_COMET_HANDLER = "bayeuxCometHandler";

    
    private ConcurrentHashMap<String,String> activeChannels 
            = new ConcurrentHashMap<String,String>();
    
    
    private Random random = new Random();
    
    
    private ConcurrentHashMap<String,DataHandler> activeCometHandlers =
            new ConcurrentHashMap<String,DataHandler>();
    
    
    public void onHandshake(CometEvent event) throws IOException {            
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        HandshakeRequest handshakeReq = (HandshakeRequest)cometdContext.getVerb();
        HandshakeResponse handshakeRes = new HandshakeResponse(handshakeReq);
        handshakeRes.setAdvice(new Advice());
        if (handshakeReq.isValid()) {
            String clientId  = null;
            synchronized(random){
                clientId = String.valueOf(Long.toHexString(random.nextLong()));
            }

            // XXX Why do we need to cache the ID. Memory leak right now
            handshakeRes.setClientId(clientId);
        } else {
            handshakeRes.setSuccessful(false);
            handshakeRes.setError("501::invalid handshake");
        }

        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(handshakeRes.toJSON());
        res.flush();
    }
    
    
    public void onConnect(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        ConnectRequest connectReq = (ConnectRequest)cometdContext.getVerb();
        
        ConnectResponse connectRes = new ConnectResponse(connectReq);
        connectRes.setAdvice(new Advice());
        if (!connectReq.isValid()) {
            connectRes.setError("501::invalid connect");
        }
        String subscribedChannel = activeChannels.get(connectReq.getClientId());
        if (subscribedChannel != null){                    
            CometContext cometContext = event.getCometContext();
            DataHandler dataHandler = new DataHandler();
            dataHandler.attach(new Object[]{req,res});
            dataHandler.setChannel(subscribedChannel);
            dataHandler.setClientId(connectReq.getClientId());
            activeCometHandlers.put(connectReq.getClientId(),dataHandler);
            event.getCometContext().addCometHandler(dataHandler);  
            connectRes.setAdvice(null);
        }
        String jsonMessage = (subscribedChannel != null ? 
            connectRes.toLongPolledJSON() : connectRes.toJSON());
        
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(jsonMessage);
        res.flush();
    }
    
    
    @SuppressWarnings("unchecked")
    public void onDisconnect(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        DisconnectRequest disconnectReq = (DisconnectRequest)cometdContext.getVerb();
        DisconnectResponse disconnectRes = new DisconnectResponse(disconnectReq);
        if (!disconnectReq.isValid()) {
            disconnectRes.setError("501::invalid disonnect");
        }
        DataHandler dataHandler = 
                activeCometHandlers.remove(disconnectReq.getClientId());
        
        if (dataHandler != null){
            event.getCometContext().notify("disconnecting",
                     CometEvent.TERMINATE,dataHandler.hashCode());
        }
        
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(disconnectRes.toJSON());
        res.flush();
    }
    

    public void onReconnect(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        ReconnectRequest reconnectReq = (ReconnectRequest)cometdContext.getVerb();                    
        ReconnectResponse reconnectRes = new ReconnectResponse(reconnectReq);
        if (!reconnectReq.isValid()) {
            reconnectRes.setError("501::invalid reconnect");
        }
                              
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(reconnectRes.toJSON());
        res.flush();
    }
    
    
    @SuppressWarnings("unchecked")
    public void onSubscribe(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        SubscribeRequest subscribeReq = (SubscribeRequest)cometdContext.getVerb();   
        SubscribeResponse subscribeRes = new SubscribeResponse(subscribeReq);
        if (!subscribeReq.isValid()) {
            subscribeRes.setError("501::invalid subscribe");
        }
        activeChannels.put(subscribeReq.getClientId(),subscribeReq.getSubscription());

        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(subscribeRes.toJSON());
        res.flush();
    }
    
    
    @SuppressWarnings("unchecked")
    public void onUnsubscribe(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        UnsubscribeRequest unsubscribeReq = (UnsubscribeRequest)cometdContext.getVerb();
        UnsubscribeResponse unsubscribeRes = new UnsubscribeResponse(unsubscribeReq);
        if (!unsubscribeReq.isValid()) {
            unsubscribeRes.setError("501::invalid unsubscribe");
        }
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(unsubscribeRes.toJSON());
        res.flush();
    }
    
    
    @SuppressWarnings("unchecked")
    public void onPublish(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        PublishRequest publishReq = (PublishRequest)cometdContext.getVerb();
        PublishResponse publishRes = new PublishResponse(publishReq);
        DeliverResponse deliverRes = null;
        if (publishReq.isValid()) {
            publishRes.setSuccessful(true);
            Data data = publishReq.getData();
            if (data != null) {
                deliverRes = new DeliverResponse(publishReq);
                if (publishReq.isFirst()) {
                    deliverRes.setFirst(false);
                    deliverRes.setFollow(true);
                }
                if (publishReq.isLast()) {
                    publishRes.setLast(false);
                    deliverRes.setFollow(true);
                    deliverRes.setLast(true);
                }
            }
        } else {
            publishRes.setSuccessful(false);
            publishRes.setError("501:: invalid publish");
        }

         
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(publishRes.toJSON());
        if (deliverRes != null) {
            res.write(deliverRes.toJSON());
        }
        res.flush(); 

        if (deliverRes != null) {
            event.getCometContext().notify(deliverRes);
        }
    }
    
    
    public final static CometdContext newCometdContext(final CometdRequest req, 
            final CometdResponse res,final Verb verb){
        return new CometdContext(){
            
            public Verb getVerb(){
                return verb;
            }
            
            public CometdRequest getRequest(){
                return req;
            }
                      
            public CometdResponse getResponse(){
                return res;
            }            
        };
    }  
    
        
    public void onTerminate(CometEvent event) throws IOException {
        onInterrupt(event);    
    }
    
    
    public void onInterrupt(CometEvent event) throws IOException {
    }
    
    // ---------------------------------------------------- Reserved but not used
    
    
    public void onPing(CometEvent event) throws IOException {
    }
    
    
    public void onStatus(CometEvent event) throws IOException {
    }

    
    public String getChannel() {
        return BAYEUX_COMET_HANDLER;
    }

    
    public void setChannel(String channel) {
        // Not supported
    }
 
}
