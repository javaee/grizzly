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

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEngine;
import com.sun.grizzly.cometd.bayeux.ConnectRequest;
import com.sun.grizzly.cometd.bayeux.ConnectResponse;
import com.sun.grizzly.cometd.bayeux.Advice;
import com.sun.grizzly.cometd.bayeux.Data;
import com.sun.grizzly.cometd.bayeux.DeliverResponse;
import com.sun.grizzly.cometd.bayeux.DisconnectRequest;
import com.sun.grizzly.cometd.bayeux.DisconnectResponse;
import com.sun.grizzly.cometd.bayeux.End;
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
import com.sun.grizzly.cometd.bayeux.VerbBase;
import com.sun.grizzly.cometd.bayeux.Verb.Type.*;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.LinkedTransferQueue;

import com.sun.grizzly.util.buf.Base64Utils;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.AbstractQueue;
import java.util.Map.Entry;
import java.util.logging.Level;

/**
 * This class implement the Bayeux Server side protocol. 
 *
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class BayeuxParser{
    
    private static Level level = Level.FINE;
    
    /**
     * Allow pushing data to all created channel. Default is true,
     */
    private static boolean enforceSubscriptionUnderPush = true;
  
    static{
        if (System.getProperty("com.sun.grizzly.cometd.logAll") != null){
            level = Level.INFO;
        }
        
        if (System.getProperty("com.sun.grizzly.cometd.enforceSubscription") != null){
            enforceSubscriptionUnderPush = false;
        }
    }
    
    public final static String DEFAULT_CONTENT_TYPE ="application/json-comment-filtered";

    private final SecureRandom random = new SecureRandom();

    private final ConcurrentHashMap<String,AbstractQueue<String>> inactiveChannels
            = new ConcurrentHashMap<String,AbstractQueue<String>>(16, 0.75f, 64);
    
    private final ConcurrentHashMap<String,Boolean> authenticatedUsers
            = new ConcurrentHashMap<String,Boolean>(16, 0.75f, 64);
    
    private final ConcurrentHashMap<String,CometContext> activeCometContexts =
            new ConcurrentHashMap<String,CometContext>(16, 0.75f, 64);
    
    private final ConcurrentHashMap<String,DataHandler> activeCometHandlers =
            new ConcurrentHashMap<String,DataHandler>(16, 0.75f, 64);

    private final ThreadLocal<Set<String>> deliverInChannels =
        new ThreadLocal<Set<String>>() {
            protected Set initialValue() {
                return new HashSet<String>();
            }
        };

    public BayeuxParser() {
    }
    
    public void parse(CometdContext cometdContext) throws IOException{
        log(cometdContext.getVerb().toString());
        switch(cometdContext.getVerb().getType()) {
            case HANDSHAKE:
                onHandshake(cometdContext);
                break;
            case CONNECT:
                onConnect(cometdContext);
                break;
            case DISCONNECT:
                onDisconnect(cometdContext);
                break;
            case RECONNECT:
                onReconnect(cometdContext);
                break;
            case SUBSCRIBE:
                onSubscribe(cometdContext);
                break;
            case UNSUBSCRIBE:
                onUnsubscribe(cometdContext);
                break;
            case PUBLISH:
                onPublish(cometdContext);
                break;
            case PING:
                onPing(cometdContext);
                break;
            case STATUS:
                onStatus(cometdContext);
                break;
            default:
                break;
        }
    }                         

    
    public void onHandshake(CometdContext cometdContext) throws IOException {            
        CometdResponse res = cometdContext.getResponse();
        HandshakeRequest handshakeReq = (HandshakeRequest)cometdContext.getVerb();
        HandshakeResponse handshakeRes = new HandshakeResponse(handshakeReq);
        handshakeRes.setAdvice(new Advice());
        if (handshakeReq.isValid()) {
            String clientId = null;
            do{
                byte [] ba = new byte[16]; //128bit
                random.nextBytes(ba);
                clientId = Base64Utils.encodeToString(ba,false);
            }while(authenticatedUsers.putIfAbsent(clientId,Boolean.TRUE) != null);
            handshakeRes.setClientId(clientId);
        } else {
            handshakeRes.setSuccessful(false);
            handshakeRes.setError("501::invalid handshake");
        }

        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(handshakeRes.toJSON());
        res.flush();
    }
    
    
    public void onConnect(CometdContext cometdContext) throws IOException {        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        ConnectRequest connectReq = (ConnectRequest)cometdContext.getVerb();
        
        ConnectResponse connectRes = new ConnectResponse(connectReq);
        connectRes.setAdvice(new Advice());
        
        String errorMessage = isAuthenticatedAndValid(connectReq);      
        res.setContentType(DEFAULT_CONTENT_TYPE);    
        
        if (errorMessage == null){
            String clientId = connectReq.getClientId();
            DataHandler dataHandler = activeCometHandlers.get(clientId);
            if (dataHandler != null && dataHandler.getChannels().size() > 0) {
                res.write(connectRes.toLongPolledJSON());
                for (String channel: dataHandler.getChannels()){
                    CometContext cc = getCometContext(channel);                
                    if (cc.getCometHandler(dataHandler.hashCode()) == null){
                        log("Suspending client: " + clientId + " channel: " + channel);
                        dataHandler.attach(new Object[]{req,res});
                        cc.addCometHandler(dataHandler); 
                        dataHandler.setSuspended(true);
                    }
                }
                connectRes.setAdvice(null);
            } else {
                res.write(connectRes.toJSON());
            }
            res.flush();
        } else {
            res.write(errorMessage);
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public void onDisconnect(CometdContext cometdContext) throws IOException {
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        DisconnectRequest disconnectReq = (DisconnectRequest)cometdContext.getVerb();
        DisconnectResponse disconnectRes = new DisconnectResponse(disconnectReq);
        
        String errorMessage = isAuthenticatedAndValid(disconnectReq);
        res.setContentType(DEFAULT_CONTENT_TYPE);    
        if (errorMessage == null){
            activeCometHandlers.remove(disconnectReq.getClientId());
            authenticatedUsers.remove(disconnectReq.getClientId());
            res.write(disconnectRes.toJSON());
        } else {
            res.write(errorMessage);
        }
        res.flush();
        notifyEnd(disconnectRes, req.getRemotePort());
    }
       

    public void onReconnect(CometdContext cometdContext) throws IOException {        
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
        notifyEnd(reconnectRes, req.getRemotePort());
    }
    
    
    @SuppressWarnings("unchecked")
    public void onSubscribe(CometdContext cometdContext) throws IOException {
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
                activeCometHandlers.put(clientId, dataHandler);
            }

            dataHandler.addChannel(subscribeReq.getSubscription());

            if (dataHandler.isSuspended()){
                subscribeRes.setLast(true);
            }
            res.write(subscribeRes.toJSON());
        } else {
            res.write(errorMessage);
        }            
        notifyEnd(subscribeRes, req.getRemotePort());
    }
    
    
    @SuppressWarnings("unchecked")
    public void onUnsubscribe(CometdContext cometdContext) throws IOException {
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
                hasSubscription = dataHandler.containsChannel(subscription);
                if (hasSubscription) {
                    AbstractQueue<String> unsubscribedChannels = inactiveChannels.get(clientId);
                    if (unsubscribedChannels == null) {
                        unsubscribedChannels = new LinkedTransferQueue<String>();
                        AbstractQueue<String> uscs = inactiveChannels.putIfAbsent(clientId, unsubscribedChannels);
                        if (uscs != null) {
                            unsubscribedChannels = uscs;
                        }
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

        notifyEnd(unsubscribeRes, req.getRemotePort());

        if (hasSubscription) {
            dataHandler.removeChannel(subscription);
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public void onPublish(CometdContext cometdContext) throws IOException {             
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        PublishRequest publishReq = (PublishRequest)cometdContext.getVerb();
        PublishResponse publishRes = new PublishResponse(publishReq);
        DeliverResponse deliverRes = null;
                        
        String errorMessage = isAuthenticatedAndValid(publishReq);  
        res.setContentType(DEFAULT_CONTENT_TYPE);   
        
        DataHandler dataHandler = null;
        if (errorMessage != null){
            res.write(errorMessage);
            return;
        }
  
        publishRes.setSuccessful(true);

        String clientId = publishReq.getClientId();
        if (clientId != null) {
            dataHandler = activeCometHandlers.get(clientId);
        }

        boolean justSubscribedInTheSameRequest =
            (dataHandler != null && dataHandler.getRemotePort() == -1);
        boolean deliverToSamePort =
                justSubscribedInTheSameRequest || 
                // subscribed and connected
                (dataHandler != null && 
                (dataHandler.getRemotePort() == req.getRemotePort()));

        Data data = publishReq.getData();
        if (data != null) {
            deliverRes = new DeliverResponse(publishReq);
            deliverRes.setFollow(true);
            if (publishReq.isFirst()) {
                deliverRes.setFirst(false);
            }
        }

        if (publishReq.isLast() && deliverRes != null && deliverToSamePort){
            publishRes.setLast(false);
        }

        res.write(publishRes.toJSON());
        if (deliverRes != null) {
            deliverInChannels.get().add(publishReq.getChannel());

            // Exceptional case, DataHandler is not in CometdContext yet
            if (justSubscribedInTheSameRequest) {
                res.write(deliverRes.toJSON());
            }

            notifyAll(deliverRes, publishRes.getChannel());
        }

        notifyEnd(deliverRes, req.getRemotePort());
        }

    private void notifyAll(Object obj, String channel) throws IOException {
        if (enforceSubscriptionUnderPush){
            for (Entry<String,CometContext> entry:activeCometContexts.entrySet()){
                entry.getValue().notify(obj);
            } 
        } else {
            CometContext cc = getCometContext(channel);
            log("Notifying " + channel + " to " 
                    + cc.getCometHandlers().size() 
                    + " CometHandler with message\n" + obj);
            cc.notify(obj);
        }
    }

    private void notifyEnd(VerbBase verb, int requestPort) throws IOException {
        if (verb.isLast()) {
            Set<String> dic = deliverInChannels.get();
            if  (dic.size() > 0) {
                End end = new End(requestPort, dic);
                notifyAll(end, dic.iterator().next());
                dic.clear();
            }

            String clientId = verb.getClientId();
            DataHandler dataHandler = activeCometHandlers.get(clientId);
            Collection<String> subscribedChannels = null;
            if (dataHandler != null) {
                subscribedChannels = dataHandler.getChannels();
            }

            if (subscribedChannels != null && subscribedChannels.size() > 0 ){
                int i = 0;
                for (String channel: subscribedChannels){
                     log("Removing subscribed " + channel);
                     if (i++ != 0){
                        getCometContext(channel).getCometHandlers().remove(dataHandler);
                     }
                }
            }
            AbstractQueue<String> unsubscribedChannels = inactiveChannels.get(clientId);
            if (unsubscribedChannels != null && !unsubscribedChannels.isEmpty() ){
                int i=0;
                for (String channel: unsubscribedChannels){
                    log("Removing unsubscribed " + channel);
                    if (i++ != 0){
                         getCometContext(channel).getCometHandlers().remove(dataHandler);
                    }
                }
            }

            if (unsubscribedChannels != null) {
                unsubscribedChannels.clear();
                inactiveChannels.remove(clientId);
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
        if (clientId!= null && !authenticatedUsers.containsKey(clientId)){
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


    private CometContext getCometContext(String channel){
        CometContext cc = activeCometContexts.get(channel);
        if (cc == null){
            cc = createCometContext(channel);  
            activeCometContexts.put(channel,cc);
        }
        return cc;       
    }
    

    private CometContext createCometContext(String channel){
        CometContext cc = CometEngine.getEngine().register(channel);
        cc.setExpirationDelay(-1);
        cc.setBlockingNotification(true);
        return cc;
    }
    
    
    protected void log(String log){
        if (SelectorThread.logger().isLoggable(level)){
            SelectorThread.logger().log(level,log);
        }
    }
    
    // ---------------------------------------------------- Reserved but not used
    
    
    public void onPing(CometdContext cometdContext) throws IOException {
    }
    
    
    public void onStatus(CometdContext cometdContext) throws IOException {
    }
 
}
