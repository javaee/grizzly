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

package com.sun.grizzly.cometd.bayeux;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class unmarshal a JSON message into a Java Object.
 * @author Jeanfrancois Arcand
 */
public class VerbUtils {
    
    private final static String HANDSHAKE = Handshake.META_HANDSHAKE;
    private final static String CONNECT = Connect.META_CONNECT;
    private final static String DISCONNECT = Disconnect.META_DISCONNECT;
    private final static String RECONNECT = Reconnect.META_RECONNECT;
    private final static String SUBSCRIBE = Subscribe.META_SUBSCRIBE;
    private final static String UNSUBSCRIBE = Unsubscribe.META_UNSUBSCRIBE;
    private final static String STATUS = "/status";
    private final static String PING = "/ping";
    private final static String DATA = "data";    
    
    
    public VerbUtils() {
    }

    public static List<Verb> parseRequest(Object verb) {
        return parse(verb, true);
    }

    public static List<Verb> parseResponse(Object verb) {
        return parse(verb, false);
    }
    
    private static List<Verb> parse(Object verb, boolean isRequest){  
        List<Verb> verbs = new ArrayList<Verb>();
        VerbBase wellFormedVerb = null;
        if (verb.getClass().isArray()){
            int length = Array.getLength(verb);
            for (int i=0; i < length; i++){
                wellFormedVerb = 
                        parseMap((Map)Array.get(verb,i), isRequest);
                if (wellFormedVerb == null){
                    throw new RuntimeException("Wrong type");  
                }
                if (i == 0) {
                    wellFormedVerb.setFirst(true);
                } else {
                    wellFormedVerb.setFollow(true);
                }
                verbs.add(wellFormedVerb);
            }
            wellFormedVerb.setLast(true);
        } else { // single JSON object case
            wellFormedVerb = parseMap((Map)verb, isRequest);
            if (wellFormedVerb == null){
                throw new RuntimeException("Wrong type");  
            }
            wellFormedVerb.setFirst(true);
            wellFormedVerb.setLast(true);
            verbs.add(wellFormedVerb);
        }
        
        return verbs;
    }
    
    
    protected static VerbBase parseMap(Map map, boolean isRequest) {
        String channel = (String)map.get("channel");
        VerbBase vb = null;
        if (channel.indexOf(HANDSHAKE) != -1){
            vb = (isRequest)? newHandshakeRequest(map) : newHandshakeResponse(map);
        } else if (channel.indexOf(CONNECT) != -1){
            vb = (isRequest)? newConnectRequest(map) : newConnectResponse(map);
        } else if (channel.indexOf(DISCONNECT) != -1){
            vb = (isRequest)? newDisconnectRequest(map) : newDisconnectResponse(map);            
        } else if (channel.indexOf(RECONNECT) != -1){
            vb = (isRequest)? newReconnectRequest(map) : newReconnectResponse(map);
        } else if (channel.indexOf(SUBSCRIBE) != -1){
            vb = (isRequest)? newSubscribeRequest(map) : newSubscribeResponse(map);
        } else if (channel.indexOf(UNSUBSCRIBE) != -1){
            vb = (isRequest)? newUnsubscribeRequest(map) : newUnsubscribeResponse(map);
        } else if (channel.indexOf(PING) != -1){
            vb = newPing(map);
        } else if (channel.indexOf(STATUS) != -1){
            vb = newStatus(map);
        } else { // publish request, publish response, deliver message
            if (isRequest) {
               vb = newPublishRequest(map);
            } else if (map.get(DATA) != null) {
               vb = newDeliverResponse(map);
            } else {
               vb = newPublishResponse(map);
            }
        }
        configureExt(vb,map);        
        return vb;
    }
    
    
    private final static HandshakeRequest newHandshakeRequest(Map map){
        HandshakeRequest handshakeReq = new HandshakeRequest();
        
        handshakeReq.setAuthScheme((String)map.get("authScheme"));
        handshakeReq.setAuthUser((String)map.get("authUser"));
        handshakeReq.setAuthToken((String)map.get("authToken"));
        handshakeReq.setChannel((String)map.get("channel"));
        handshakeReq.setVersion((String)map.get("version"));
        handshakeReq.setMinimumVersion((String)map.get("minimumVersion"));
        handshakeReq.setId((String)map.get("id"));
        handshakeReq.setSupportedConnectionTypes(getSupportedConnectionTypes(map));


        return handshakeReq; 
    }


    private final static HandshakeResponse newHandshakeResponse(Map map) {
        HandshakeResponse handshakeRes = new HandshakeResponse();
        
        handshakeRes.setAuthScheme((String)map.get("authScheme"));
        handshakeRes.setAuthUser((String)map.get("authUser"));
        handshakeRes.setAuthToken((String)map.get("authToken"));
        handshakeRes.setChannel((String)map.get("channel"));
        handshakeRes.setVersion((String)map.get("version"));
        handshakeRes.setMinimumVersion((String)map.get("minimumVersion"));
        handshakeRes.setId((String)map.get("id"));
        handshakeRes.setSupportedConnectionTypes(getSupportedConnectionTypes(map));

        handshakeRes.setClientId((String)map.get("clientId"));
        handshakeRes.setSuccessful((Boolean)map.get("successful"));
        handshakeRes.setAuthSuccessful((Boolean)map.get("authSuccessful"));
        handshakeRes.setError((String)map.get("error"));
        configureAdvice(handshakeRes, map);

        return handshakeRes; 
    }
    
    
    private final static ConnectRequest newConnectRequest(Map map){
        ConnectRequest connectReq = new ConnectRequest();
        
        connectReq.setAuthToken((String)map.get("authToken"));
        connectReq.setChannel((String)map.get("channel"));
        connectReq.setClientId((String)map.get("clientId"));
        connectReq.setConnectionType((String)map.get("connectionType"));
        connectReq.setId((String)map.get("id"));

        return connectReq;
    }


    private final static ConnectResponse newConnectResponse(Map map) {
        ConnectResponse connectRes = new ConnectResponse();
        
        connectRes.setAuthToken((String)map.get("authToken"));
        connectRes.setChannel((String)map.get("channel"));
        connectRes.setClientId((String)map.get("clientId"));
        connectRes.setId((String)map.get("id"));
        
        connectRes.setSuccessful((Boolean)map.get("successful"));
        connectRes.setError((String)map.get("error"));
        connectRes.setTimestamp((String)map.get("timestamp"));
        configureAdvice(connectRes, map);
        return connectRes;
    }
    
    
    private final static DisconnectRequest newDisconnectRequest(Map map){
        DisconnectRequest disconnectReq = new DisconnectRequest();
        
        disconnectReq.setAuthToken((String)map.get("authToken"));
        disconnectReq.setChannel((String)map.get("channel"));
        disconnectReq.setClientId((String)map.get("clientId"));
        disconnectReq.setId((String)map.get("id"));

        
        return disconnectReq;
    }    


    private final static DisconnectResponse newDisconnectResponse(Map map) {
        DisconnectResponse disconnectRes = new DisconnectResponse();
        
        disconnectRes.setAuthToken((String)map.get("authToken"));
        disconnectRes.setChannel((String)map.get("channel"));
        disconnectRes.setClientId((String)map.get("clientId"));
        disconnectRes.setId((String)map.get("id")); 

        disconnectRes.setSuccessful((Boolean)map.get("successful"));
        disconnectRes.setError((String)map.get("error"));
        configureAdvice(disconnectRes, map);

        return disconnectRes;
    }
    
    
    private final static ReconnectRequest newReconnectRequest(Map map){
        ReconnectRequest reconnectReq = new ReconnectRequest();
        
        reconnectReq.setAuthToken((String)map.get("authToken"));
        reconnectReq.setChannel((String)map.get("channel"));
        reconnectReq.setClientId((String)map.get("clientId"));
        reconnectReq.setId((String)map.get("id"));

        
        return reconnectReq;
    } 


    private final static ReconnectResponse newReconnectResponse(Map map) {
        ReconnectResponse reconnectRes = new ReconnectResponse();
        
        reconnectRes.setAuthToken((String)map.get("authToken"));
        reconnectRes.setChannel((String)map.get("channel"));
        reconnectRes.setClientId((String)map.get("clientId"));
        reconnectRes.setId((String)map.get("id"));

        reconnectRes.setSuccessful((Boolean)map.get("successful"));
        reconnectRes.setError((String)map.get("error"));
        
        return reconnectRes;
    }
    
    
    private final static SubscribeRequest newSubscribeRequest(Map map){
        SubscribeRequest subscribeReq = new SubscribeRequest();
        
        subscribeReq.setChannel((String)map.get("channel"));
        subscribeReq.setAuthToken((String)map.get("authToken"));
        subscribeReq.setSubscription((String)map.get("subscription"));
        subscribeReq.setClientId((String)map.get("clientId"));
        subscribeReq.setId((String)map.get("id"));

        return subscribeReq;
    }


    private final static SubscribeResponse newSubscribeResponse(Map map) {
        SubscribeResponse subscribeRes = new SubscribeResponse();
        
        subscribeRes.setChannel((String)map.get("channel"));
        subscribeRes.setAuthToken((String)map.get("authToken"));
        subscribeRes.setSubscription((String)map.get("subscription"));
        subscribeRes.setClientId((String)map.get("clientId"));
        subscribeRes.setId((String)map.get("id"));

        subscribeRes.setSuccessful((Boolean)map.get("successful"));
        subscribeRes.setError((String)map.get("error"));
        configureAdvice(subscribeRes, map);

        return subscribeRes;
    }
    
        
    private final static UnsubscribeRequest newUnsubscribeRequest(Map map){
        UnsubscribeRequest unsubscribeReq = new UnsubscribeRequest();
        
        unsubscribeReq.setChannel((String)map.get("channel"));
        unsubscribeReq.setAuthToken((String)map.get("authToken"));
        unsubscribeReq.setSubscription((String)map.get("subscription"));
        unsubscribeReq.setClientId((String)map.get("clientId"));
        unsubscribeReq.setId((String)map.get("id"));
        return unsubscribeReq;
    }
    
    
    private final static UnsubscribeResponse newUnsubscribeResponse(Map map){
        UnsubscribeResponse unsubscribeRes = new UnsubscribeResponse();
        
        unsubscribeRes.setChannel((String)map.get("channel"));
        unsubscribeRes.setAuthToken((String)map.get("authToken"));
        unsubscribeRes.setSubscription((String)map.get("subscription"));
        unsubscribeRes.setClientId((String)map.get("clientId"));
        unsubscribeRes.setId((String)map.get("id"));

        unsubscribeRes.setSuccessful((Boolean)map.get("successful"));
        unsubscribeRes.setError((String)map.get("error"));
        unsubscribeRes.setTimestamp((String)map.get("timestamp"));
        configureAdvice(unsubscribeRes, map);
        return unsubscribeRes;
    }
    
    
    private final static Ping newPing(Map map){
        Ping ping = new Ping();
        
        ping.setChannel((String)map.get("channel"));
        return ping;
    }
    
    
    private final static Status newStatus(Map map){
        Status status = new Status();
        
        status.setChannel((String)map.get("channel"));
        return status;        
    }


    private final static PublishRequest newPublishRequest(Map map) {
        PublishRequest publishReq = new PublishRequest();
        Map mapData = (Map)map.get("data");
        Data data = new Data();
        data.setMapData(mapData);

        publishReq.setChannel((String)map.get("channel"));
        publishReq.setData(data);
        publishReq.setClientId((String)map.get("clientId"));
        publishReq.setId((String)map.get("id"));
        return publishReq;
    }

    private final static PublishResponse newPublishResponse(Map map) {
        PublishResponse publishRes = new PublishResponse();
        publishRes.setChannel((String)map.get("channel"));
        publishRes.setClientId((String)map.get("clientId"));
        publishRes.setId((String)map.get("id"));
        publishRes.setError((String)map.get("error"));
        publishRes.setSuccessful((Boolean)map.get("successful"));
        return publishRes;
    }

    private final static DeliverResponse newDeliverResponse(Map map) {
        DeliverResponse deliverRes = new DeliverResponse();
        Map mapData = (Map)map.get("data");
        Data data = new Data();
        data.setMapData(mapData);

        deliverRes.setChannel((String)map.get("channel"));
        deliverRes.setData(data);
        deliverRes.setClientId((String)map.get("clientId"));
        deliverRes.setId((String)map.get("id"));
        configureAdvice(deliverRes, map);
        return deliverRes;
    }
    
    
    @SuppressWarnings("unchecked")
    private static void configureExt(VerbBase vb, Map map){
        Map<String, Object> extMap = (Map<String, Object>)map.get("ext");
        if (extMap == null) return;
        
        Ext ext = new Ext();
        ext.setExtensionMap(extMap);
        vb.setExt(ext);
    }

    private static void configureAdvice(VerbBase vb, Map map) {
        Map<String, Object> adviceMap = (Map<String, Object>)map.get("advice");
        if (adviceMap == null) return;

        Advice advice = new Advice();
        String reconnect = (String)adviceMap.get("reconnect");
        if (reconnect != null) {
            advice.setReconnect(reconnect);
        }
        Integer interval = new Integer(((Number)adviceMap.get("interval")).intValue());
        if (interval != null) {
            advice.setInterval(interval);
        }
        Boolean multipleClients = (Boolean)adviceMap.get("multiple-clients");
        if (multipleClients != null) {
            advice.setMultipleClients(multipleClients);
        }
        String[] hosts = (String[])adviceMap.get("hosts");
        if (hosts != null) {
            advice.setHosts(hosts);
        }

        vb.setAdvice(advice);
    }

    private static String[] getSupportedConnectionTypes(Map map) {
        String[] types = null;
        Object[] typeObjs = (Object[])map.get("supportedConnectionTypes");
        if (typeObjs != null) {
            types = new String[typeObjs.length];
            for (int i = 0; i < typeObjs.length; i++) {
                types[i] = (String)typeObjs[i];
            }
        }
        return types;
    }
}
