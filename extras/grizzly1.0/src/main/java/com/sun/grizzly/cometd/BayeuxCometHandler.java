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

package com.sun.grizzly.cometd;

import com.sun.enterprise.web.connector.grizzly.comet.CometContext;
import com.sun.enterprise.web.connector.grizzly.comet.CometEvent;
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
import com.sun.grizzly.cometd.bayeux.VerbBase;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class implement the Bayeux Server side protocol. 
 *
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class BayeuxCometHandler extends BayeuxCometHandlerBase{
    
    public final static String DEFAULT_CONTENT_TYPE ="application/json-comment-filtered";
            
    public final static String BAYEUX_COMET_HANDLER = "bayeuxCometHandler";

    private static Collection<String> BAYEUX_COMET_HANDLER_COLLECTION
        = Collections.unmodifiableCollection(Collections.singleton(BAYEUX_COMET_HANDLER));

    private ConcurrentHashMap<String,Collection<String>> inactiveChannels 
            = new ConcurrentHashMap<String,Collection<String>>();
    
    private ConcurrentLinkedQueue<String> authenticatedUsers 
            = new ConcurrentLinkedQueue<String>();    
    
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

            handshakeRes.setClientId(clientId);
            authenticatedUsers.offer(clientId);
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
        
        String errorMessage = isAuthenticatedAndValid(connectReq);      
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        if (errorMessage == null){
            boolean hasChannel = false;
            String clientId = connectReq.getClientId();
            DataHandler dataHandler = activeCometHandlers.get(clientId);

            if (dataHandler != null && dataHandler.getChannels().size() > 0) {
                hasChannel = true;
                dataHandler.attach(new Object[]{req,res});
                event.getCometContext().addCometHandler(dataHandler);  
                connectRes.setAdvice(null);
            }

            String jsonMessage = (hasChannel ? 
                connectRes.toLongPolledJSON() : connectRes.toJSON());

            res.write(jsonMessage);
        } else {
            res.write(errorMessage);
        }
        res.flush();
    }
    
    
    @SuppressWarnings("unchecked")
    public void onDisconnect(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdResponse res = cometdContext.getResponse();
        DisconnectRequest disconnectReq = (DisconnectRequest)cometdContext.getVerb();
        DisconnectResponse disconnectRes = new DisconnectResponse(disconnectReq);
        
        String errorMessage = isAuthenticatedAndValid(disconnectReq);
        res.setContentType(DEFAULT_CONTENT_TYPE);    
        if (errorMessage == null){
            removeActiveHandler(disconnectReq.getClientId(),event.getCometContext());
            authenticatedUsers.remove(disconnectReq.getClientId());
            res.write(disconnectRes.toJSON());
        } else {
            res.write(errorMessage);
        }
        res.flush();
        notifyEnd(event, disconnectReq);
    }
    
    
    /**
     * Remove an {@link DataHandler} from the list of active listener.
     * @param clientId
     * @param ctx
     * @return Return <tt>true</tt> if the clientId was a registered {@link DataHandler}
     */
    public boolean removeActiveHandler(String clientId, CometContext ctx) 
            throws IOException{
        
        DataHandler dataHandler = 
                activeCometHandlers.remove(clientId);
        if (dataHandler != null && ctx.isActive(dataHandler)){
            ctx.notify("disconnecting",
                     CometEvent.TERMINATE,dataHandler.hashCode());
            return true;
        }       
        return false;
    }
       

    public void onReconnect(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        ReconnectRequest reconnectReq = (ReconnectRequest)cometdContext.getVerb();                    
        ReconnectResponse reconnectRes = new ReconnectResponse(reconnectReq);
        
        String errorMessage = isAuthenticatedAndValid(reconnectReq);
        res.setContentType(DEFAULT_CONTENT_TYPE);            
        if (errorMessage == null){
            res.write(reconnectRes.toJSON());
        } else {
            res.write(errorMessage);
        }
        res.flush();
        notifyEnd(event, reconnectReq);
    }
    
    
    @SuppressWarnings("unchecked")
    public void onSubscribe(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        SubscribeRequest subscribeReq = (SubscribeRequest)cometdContext.getVerb();   
        SubscribeResponse subscribeRes = new SubscribeResponse(subscribeReq);
        
        String errorMessage = isAuthenticatedAndValid(subscribeReq);    
        res.setContentType(DEFAULT_CONTENT_TYPE);             
        if (errorMessage == null){
            String clientId = subscribeReq.getClientId();
            DataHandler dataHandler = activeCometHandlers.get(clientId);
            if (dataHandler == null) {
                dataHandler = new DataHandler(this);
                dataHandler.setClientId(clientId);
                activeCometHandlers.putIfAbsent(clientId, dataHandler);
                // just in case two threads both put
                dataHandler = activeCometHandlers.get(clientId);
            }

            dataHandler.addChannel(subscribeReq.getSubscription());

            res.write(subscribeRes.toJSON());
        } else {
            res.write(errorMessage);
        }            
        res.flush();
        notifyEnd(event, subscribeReq);
    }
    
    
    @SuppressWarnings("unchecked")
    public void onUnsubscribe(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        UnsubscribeRequest unsubscribeReq = (UnsubscribeRequest)cometdContext.getVerb();
        UnsubscribeResponse unsubscribeRes = new UnsubscribeResponse(unsubscribeReq);
        
        boolean hasSubscription = false;
        DataHandler dataHandler = null;
        String clientId = unsubscribeReq.getClientId();
        String subscription = unsubscribeReq.getSubscription();

        String errorMessage = isAuthenticatedAndValid(unsubscribeReq);       
        res.setContentType(DEFAULT_CONTENT_TYPE);  
        if (errorMessage == null){
            dataHandler = activeCometHandlers.get(clientId);
            if (dataHandler != null) {
                hasSubscription = dataHandler.removeChannel(subscription);
                if (hasSubscription) {
                    dataHandler.removeChannel(subscription);
                    Collection<String> unsubscribedChannels = inactiveChannels.get(clientId);
                    if (unsubscribedChannels == null) {
                        unsubscribedChannels = new ConcurrentLinkedQueue<String>();
                        inactiveChannels.putIfAbsent(clientId, unsubscribedChannels);
                        // just in case two threads both put
                        unsubscribedChannels = inactiveChannels.get(clientId);
                    }
                    unsubscribedChannels.add(subscription);
                }
            }

            unsubscribeRes.setSuccessful(hasSubscription);
            res.write(unsubscribeRes.toJSON());
        } else {
            res.write(errorMessage);
        }
        res.flush();
        notifyEnd(event, unsubscribeReq);

    }
    
    
    @SuppressWarnings("unchecked")
    public void onPublish(CometEvent event) throws IOException {
        CometdContext cometdContext = (CometdContext)event.attachment();
        
        CometdResponse res = cometdContext.getResponse();
        PublishRequest publishReq = (PublishRequest)cometdContext.getVerb();
        PublishResponse publishRes = new PublishResponse(publishReq);
        DeliverResponse deliverRes = null;
                        
        String errorMessage = isAuthenticatedAndValid(publishReq);  
        res.setContentType(DEFAULT_CONTENT_TYPE);             
        if (errorMessage != null){
            res.write(errorMessage);
        } else {
            publishRes.setSuccessful(true);
            Data data = publishReq.getData();
            if (data != null) {
                deliverRes = new DeliverResponse(publishReq);
                deliverRes.setFollow(true);
                if (publishReq.isFirst()) {
                    deliverRes.setFirst(false);
                }
            }
         
            boolean hasWritten = false;
            if (deliverRes != null) {
                String clientId = publishReq.getClientId();
                if (clientId != null) {
                    Collection<String> subscribedChannels = null;
                    DataHandler dataHandler = activeCometHandlers.get(clientId);
                    if (dataHandler != null) {
                        subscribedChannels = dataHandler.getChannels();
                    }

                    if (subscribedChannels != null &&
                            subscribedChannels.contains(publishReq.getChannel())) {
                        hasWritten = true;
                        if (publishReq.isLast()) {
                            publishRes.setLast(false);
                        }
                        res.write(publishRes.toJSON());
                        res.write(deliverRes.toJSON());
                    }
                }
            }

            if (!hasWritten) {
                res.write(publishRes.toJSON());
            }
        }
        res.flush(); 

        if (deliverRes != null) {
            event.getCometContext().notify(deliverRes);
        }

        notifyEnd(event, publishReq);
    }


    private void notifyEnd(CometEvent event, VerbBase verb) throws IOException {
        if (verb.isLast()) {
            //XXX what if clientId is null
            String clientId = verb.getClientId();
            if (clientId != null) {
                Collection<String> subscribedChannels = null;
                DataHandler dataHandler = activeCometHandlers.get(clientId);
                if (dataHandler != null) {
                    subscribedChannels = dataHandler.getChannels();
                }

                Collection<String> unsubscribedChannels = inactiveChannels.get(clientId);
                if (subscribedChannels != null && subscribedChannels.size() > 0 ||
                        unsubscribedChannels != null && unsubscribedChannels.size() > 0) {
                    event.getCometContext().notify("NOTIFY_END");
                }

                if (unsubscribedChannels != null) {
                    unsubscribedChannels.clear();
                    inactiveChannels.remove(clientId);
                }
            }
        }
    }
    
    
    /**
     * Has the client been authenticated (by executing the /meta/handshake operation)
     * and the request valid.
     */
    private String isAuthenticatedAndValid(VerbBase verb){
        String clientId = verb.getClientId();
        
        if (clientId == null) return null;
        if (clientId!= null && !authenticatedUsers.contains(clientId)){
            return constructError("402","Unknown Client", verb.getMetaChannel());
        } else if (!verb.isValid()){
            return constructError("501","Invalid Operation", verb.getMetaChannel());
        } else {
            return null;
        }
    }
    

    /**
     * Construct an error message.
     * @param errorMessage
     * @param errorMsg
     * @param meta
     * @return
     */
    private final static String constructError(String errorMessage, 
            String errorMsg, 
            String meta){
        
        StringBuilder sb = new StringBuilder();
        sb.append("[{\"successful\":false,\"error\":\"");
        sb.append(errorMessage);
        sb.append("::");
        sb.append(errorMsg);
        sb.append("\",\"advice\":{\"reconnect\":\"handshake\"},\"channel\":\"");
        sb.append(meta);
        sb.append("\"}]");
        
        return sb.toString();
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
    
        
    @Override
    public void onTerminate(CometEvent event) throws IOException {
        onInterrupt(event);    
    }
    
    
    @Override
    public void onInterrupt(CometEvent event) throws IOException {
    }
    
    // ---------------------------------------------------- Reserved but not used
    
    
    public void onPing(CometEvent event) throws IOException {
    }
    
    
    public void onStatus(CometEvent event) throws IOException {
    }

    
    public Collection<String> getChannels() {
        return BAYEUX_COMET_HANDLER_COLLECTION;
    }

    
    public void addChannel(String channel) {
        // Not supported
    }

    public boolean removeChannel(String channel) {
        // Not supported
        return false;
    }
 
}
