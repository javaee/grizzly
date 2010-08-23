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
import com.sun.grizzly.util.DataStructures;
import com.sun.grizzly.util.buf.Base64Utils;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.AbstractQueue;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implement the Bayeux Server side protocol. 
 *
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class BayeuxParser{

    private final static Logger logger = SelectorThread.logger();
    
    private final static Level level;    
    
    /**
     * Allow pushing data to all created channel. Default is true,
     */
    private static boolean enforceSubscriptionUnderPush = true;
  
    static{
        if (System.getProperty("com.sun.grizzly.cometd.logAll") != null){
            level = Level.INFO;
        }else{
             level = Level.FINE;
        }
        
        if (System.getProperty("com.sun.grizzly.cometd.enforceSubscription") != null){
            enforceSubscriptionUnderPush = false;
        }
    }
    
    public final static String DEFAULT_CONTENT_TYPE ="text/json";

    protected final static DataHandler dumyhandler = new DataHandler("dummy", null);

    private final SecureRandom random = new SecureRandom();

    private final ConcurrentHashMap<String,AbstractQueue<String>> inactiveChannels
            = new ConcurrentHashMap<String,AbstractQueue<String>>(16, 0.75f, 64);
    
    private final ConcurrentHashMap<String,CometContext> activeCometContexts =
            new ConcurrentHashMap<String,CometContext>(16, 0.75f, 64);
    
    private final ConcurrentHashMap<String,DataHandler> authenticatedUsers =
            new ConcurrentHashMap<String,DataHandler>(16, 0.75f, 64);

    private final ThreadLocal<Set<String>> deliverInChannels =
        new ThreadLocal<Set<String>>() {
            protected Set initialValue() {
                return new HashSet<String>();
            }
        };

    
    // Interceptor, which (if not null) will process published data on channel.
    // If Interceptor is null - default logic is applied - everyone gets notified
    private volatile PublishInterceptor publishInterceptor;
    
    public BayeuxParser() {
        this(null);
    }

    public BayeuxParser(PublishInterceptor publishInterceptor) {
        this.publishInterceptor = publishInterceptor;
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
            } while (!authenticatedUsers.containsKey(clientId) &&
                    authenticatedUsers.putIfAbsent(clientId,
                    new DataHandler(clientId, this)) != null);
            
            handshakeRes.setClientId(clientId);
        } else {
            handshakeRes.setSuccessful(false);
            handshakeRes.setError("501::invalid handshake");
        }

        res.setContentType(DEFAULT_CONTENT_TYPE);            
        res.write(handshakeRes.toJSON());
    }
    
    
    public void onConnect(CometdContext cometdContext) throws IOException {        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();        

        DataHandler dataHandler = isAuthenticatedAndValid(cometdContext);
        if (dataHandler != null){
            ConnectRequest connectReq = (ConnectRequest)cometdContext.getVerb();
            ConnectResponse connectRes = new ConnectResponse(connectReq);
            connectRes.setAdvice(new Advice());
            if (dataHandler != dumyhandler ) {
                synchronized(dataHandler){
                    if (dataHandler.getChannels().size() > 0){
                        res.write(connectRes.toLongPolledJSON());
                        for (String channel: dataHandler.getChannels()){
                            CometContext cc = getCometContext(channel);
                            if (!cc.isActive(dataHandler)){
                                // not applying standard logging rules for
                                // this message as it's not displayed unless
                                // a system property is explicitly set.
                                if (logger.isLoggable(level)){
                                    log("Suspending client: "+connectReq.getClientId()+" channel: "+channel);
                                }
                                dataHandler.attach(new Object[]{req,res});
                                cc.addCometHandler(dataHandler);
                                dataHandler.setSuspended(true);
                            }
                        }
                        connectRes.setAdvice(null);
                        return;
                    }
                }
            }
            res.write(connectRes.toJSON());
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public void onDisconnect(CometdContext cometdContext) throws IOException {
        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        DisconnectRequest disconnectReq = (DisconnectRequest)cometdContext.getVerb();
        DisconnectResponse disconnectRes = new DisconnectResponse(disconnectReq);

        DataHandler dataHandler = isAuthenticatedAndValid(cometdContext);        
        if (dataHandler != null){
            authenticatedUsers.remove(disconnectReq.getClientId());
            dataHandler.write(disconnectRes.toJSON(), res, true);
        }
        notifyEnd(disconnectRes, req.getRemotePort(),dataHandler);
    }
       

    public void onReconnect(CometdContext cometdContext) throws IOException {        
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        ReconnectRequest reconnectReq = (ReconnectRequest)cometdContext.getVerb();                    
        ReconnectResponse reconnectRes = new ReconnectResponse(reconnectReq);

        DataHandler dataHandler = isAuthenticatedAndValid(cometdContext);        
        if (dataHandler != null){
            dataHandler.write(reconnectRes.toJSON(), res, true);
        }         
        notifyEnd(reconnectRes, req.getRemotePort(),dataHandler);
    }    
    
    @SuppressWarnings("unchecked")
    public void onSubscribe(CometdContext cometdContext) throws IOException {
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        SubscribeRequest subscribeReq = (SubscribeRequest)cometdContext.getVerb();   
        SubscribeResponse subscribeRes = new SubscribeResponse(subscribeReq);

        DataHandler dataHandler = isAuthenticatedAndValid(cometdContext);        
        if (dataHandler != null){
            if (dataHandler == dumyhandler) {
                String clientId = subscribeReq.getClientId();
                dataHandler = new DataHandler(clientId, this);
                authenticatedUsers.put(clientId, dataHandler);
            }

            dataHandler.addChannel(subscribeReq.getSubscription());

            if (dataHandler.isSuspended()){
                subscribeRes.setLast(true);
            }
            dataHandler.write(subscribeRes.toJSON(), res, true);
        }            
        notifyEnd(subscribeRes, req.getRemotePort(),dataHandler);
    }
    
    
    @SuppressWarnings("unchecked")
    public void onUnsubscribe(CometdContext cometdContext) throws IOException {
        CometdRequest req = cometdContext.getRequest();
        CometdResponse res = cometdContext.getResponse();
        UnsubscribeRequest unsubscribeReq = (UnsubscribeRequest)cometdContext.getVerb();
        UnsubscribeResponse unsubscribeRes = new UnsubscribeResponse(unsubscribeReq);
        
        boolean hasSubscription = false;        
        String subscription = unsubscribeReq.getSubscription();

        DataHandler dataHandler = isAuthenticatedAndValid(cometdContext);        
        if (dataHandler != null){
            if (dataHandler != dumyhandler) {
                hasSubscription = dataHandler.containsChannel(subscription);
                if (hasSubscription) {
                    String clientId = unsubscribeReq.getClientId();
                    AbstractQueue<String> unsubscribedChannels = inactiveChannels.get(clientId);
                    if (unsubscribedChannels == null) {
                        unsubscribedChannels = (AbstractQueue<String>) DataStructures.getCLQinstance(String.class);
                        AbstractQueue<String> uscs = inactiveChannels.putIfAbsent(clientId, unsubscribedChannels);
                        if (uscs != null) {
                            unsubscribedChannels = uscs;
                        }
                    }
                    unsubscribedChannels.add(subscription);
                }
            }
            unsubscribeRes.setSuccessful(hasSubscription);
            dataHandler.write(unsubscribeRes.toJSON(), res, true);
        }

        notifyEnd(unsubscribeRes, req.getRemotePort(),dataHandler);

        if (hasSubscription) {
            dataHandler.removeChannel(subscription);
        }
    }
    
    
    @SuppressWarnings("unchecked")
    public void onPublish(CometdContext cometdContext) throws IOException {                     

        DataHandler dataHandler = isAuthenticatedAndValid(cometdContext);
        if (dataHandler == null){
            return;
        }

        CometdRequest req = cometdContext.getRequest();        
        PublishRequest publishReq = (PublishRequest)cometdContext.getVerb();
        PublishResponse publishRes = new PublishResponse(publishReq);
        publishRes.setSuccessful(true);

        boolean justSubscribedInTheSameRequest =
            (dataHandler != dumyhandler && dataHandler.getRemotePort() == -1);
        boolean deliverToSamePort =
                justSubscribedInTheSameRequest || 
                // subscribed and connected
                (dataHandler != dumyhandler &&
                (dataHandler.getRemotePort() == req.getRemotePort()));

        Data data = publishReq.getData();
        DeliverResponse deliverRes = null;
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

        CometdResponse res = cometdContext.getResponse();
        dataHandler.write(publishRes.toJSON(), res, false);
        if (deliverRes != null) {
            deliverInChannels.get().add(publishReq.getChannel());
            // Exceptional case, DataHandler is not in CometdContext yet
            if (justSubscribedInTheSameRequest) {
                dataHandler.write(deliverRes.toJSON(), res, false);
            }

            if (publishInterceptor == null) {
                notifyAll(deliverRes, publishRes.getChannel());
            } else {
                publishInterceptor.onPublish(
                        new PublishContextImpl(publishRes.getChannel(),
                        dataHandler, data), deliverRes);
            }
        }
        notifyEnd(deliverRes, req.getRemotePort(),dataHandler);
    }

    private void notifyAll(Object obj, String channel) throws IOException {
        if (enforceSubscriptionUnderPush){
            for (Entry<String,CometContext> entry:activeCometContexts.entrySet()){
                entry.getValue().notify(obj);
            } 
        } else {
            CometContext cc = getCometContext(channel);
            // not applying standard logging rules for
            // this message as it's not displayed unless
            // a system property is explicitly set.
            if (logger.isLoggable(level)){
                log("Notifying " + channel + " to "
                        + cc.getCometHandlers().size()
                        + " CometHandler with message\n" + obj);
            }
            cc.notify(obj);
        }
    }

    private void notifyEnd(VerbBase verb, int requestPort, DataHandler dataHandler) throws IOException {
        if (verb.isLast()) {
            Set<String> dic = deliverInChannels.get();
            if  (dic.size() > 0) {
                End end = new End(requestPort, dic);
                notifyAll(end, dic.iterator().next());
                dic.clear();
            }
            final boolean dolog = logger.isLoggable(level);
                        
            if (dataHandler != null && dataHandler != dumyhandler) {
                Collection<String> subscribedChannels = dataHandler.getChannels();
                if (subscribedChannels != null && subscribedChannels.size() > 0 ){
                    int i = 0;
                    for (String channel: subscribedChannels){
                        if (dolog){
                            log("Removing subscribed " + channel);
                        }
                         if (i++ != 0){
                            getCometContext(channel).getCometHandlers().remove(dataHandler);
                         }
                    }
                }

                String clientId = verb.getClientId();
                AbstractQueue<String> unsubscribedChannels = inactiveChannels.get(clientId);
                if (unsubscribedChannels != null && !unsubscribedChannels.isEmpty() ){
                    int i=0;
                    for (String channel: unsubscribedChannels){
                        if (dolog){
                            log("Removing unsubscribed " + channel);
                        }
                        if (i++ != 0){   //what if dataHandler is null ?
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
    }
    
    /**
     * Has the client been authenticated (by executing the /meta/handshake operation).
     * and the request valid.
     */
    private DataHandler isAuthenticatedAndValid(CometdContext cometdContext) throws IOException{
        CometdResponse res = cometdContext.getResponse();
        res.setContentType(DEFAULT_CONTENT_TYPE);
        VerbBase verb = (VerbBase)cometdContext.getVerb();
        String clientId = verb.getClientId();       

        if (clientId == null){
            return dumyhandler;
        }
        DataHandler dataHandler = authenticatedUsers.get(clientId);

        String errmsg = null;
        if (dataHandler == null){
            errmsg = constructError("402","Unknown Client", verb.getMetaChannel());
        }else
        if (!verb.isValid()){
            errmsg =  constructError("501","Invalid Operation", verb.getMetaChannel());
        }

        if (errmsg != null){
            if (dataHandler != null && dataHandler != dumyhandler){
                synchronized(dataHandler){
                    res.write(errmsg);
                    res.flush();
                }
            }else{
                res.write(errmsg);
                res.flush();
            }
            return null;
        }
        return dataHandler;
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
        
        StringBuilder sb = new StringBuilder(128);
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
       
    
    private void log(String log){
        // not applying standard logging rules for
        // this message as it's not displayed unless
        // a system property is explicitly set.
        logger.log(level,log);
    }

    // ---------------------------------------------------- Reserved but not used
    
    
    public void onPing(CometdContext cometdContext) throws IOException {
    }
    
    
    public void onStatus(CometdContext cometdContext) throws IOException {
    }

    /**
     * Interceptor, which (if not null) will process published data on channel.
     * If Interceptor is null - default logic is applied - everyone gets notified.
     *
     * @return {@link PublishInterceptor}
     */
    public PublishInterceptor getPublishInterceptor() {
        return publishInterceptor;
    }

    /**
     * Interceptor, which (if not null) will process published data on channel.
     * If Interceptor is null - default logic is applied - everyone gets notified.
     *
     * @param publishInterceptor {@link PublishInterceptor}
     */
    public void setPublishInterceptor(PublishInterceptor publishInterceptor) {
        this.publishInterceptor = publishInterceptor;
    }

    private class PublishContextImpl implements PublishContext {

        private final String channel;
        private final DataHandler senderClient;
        private final Data data;

        public PublishContextImpl(String channel, DataHandler senderClient,
                Data data) {
            this.channel = channel;
            this.senderClient = senderClient;
            this.data = data;
        }

        public Set<DataHandler> lookupClientHandlers(String channelId) {
            final CometContext context =
                    CometEngine.getEngine().getCometContext(channel);
            if (context != null) {
                return context.getCometHandlers();
            }

            return Collections.EMPTY_SET;
        }
        
        public String getChannel() {
            return channel;
        }

        public DataHandler lookupClientHandler(String userId) {
            return authenticatedUsers.get(userId);
        }

        public DataHandler getSenderClient() {
            return senderClient;
        }

        public Data getData() {
            return data;
        }
    }
}
