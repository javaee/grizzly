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

import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.cometd.bayeux.DeliverResponse;
import com.sun.grizzly.cometd.bayeux.End;
import com.sun.grizzly.cometd.bayeux.VerbBase;
import com.sun.grizzly.util.DataStructures;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
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
   
    private final Collection<String> channels = DataStructures.getCLQinstance(String.class);

    private BayeuxParser bayeuxParser;
    
    private volatile boolean isSuspended;

    private int remotePort = -1;

    private volatile boolean ended;

    private final String clientId;

    public DataHandler(String clientId, BayeuxParser bayeuxParser){
        this.clientId = clientId;
        this.bayeuxParser = bayeuxParser;
    }

    protected void write(String s, CometdResponse res, boolean flush) throws IOException{
        if (this != BayeuxParser.dumyhandler){
            synchronized(this){
                res.write(s);
                if (flush){
                    res.flush();
                }
            }
        }else{
            res.write(s);
            if (flush){
                res.flush();
            }
        }
    }

    public void attach(Object[] reqRes){
        this.req = (CometdRequest) reqRes[0];
        this.res = (CometdResponse) reqRes[1];
        this.remotePort = req.getRemotePort();
        ended = false;
    }
            
        
    @SuppressWarnings("unchecked")
    public void onEvent(CometEvent event) throws IOException{ 
        Object obj = event.attachment();      
        try{                   
            if (obj instanceof DeliverResponse){
                DeliverResponse deliverRes = (DeliverResponse)obj;
                if (getChannels().contains(deliverRes.getChannel())) {
                    res.write(deliverRes.toJSON());    
                    res.flush();
                    ended = deliverRes.isLast();
                    if (deliverRes.isFinished()){
                        event.getCometContext().resumeCometHandler(this);
                    }
                }
            } else if (obj instanceof End) {
                End end = (End)obj;
                Set<String> channels = end.getChannels();
                boolean intersect = false;
                for (String ch : channels) {
                    boolean temp = containsChannel(ch);
                    if (temp) {
                        intersect = temp;
                        break;
                    }
                }
                if (intersect) {
                    if (!ended && (end.getRequestPort() != remotePort)) {
                        res.write(VerbBase.ARRAY_END);
                        res.flush();
                        ended = true;
                    }
                    event.getCometContext().resumeCometHandler(this);
                }
            }
        }  catch (Throwable t){
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE,
                           LogMessages.SEVERE_GRIZZLY_COMETD_ONEVENT_ERROR(),
                           t);
            }
        } 
    }

    
    public void onInitialize(CometEvent event) throws IOException{  
    }


    public void onTerminate(CometEvent event) throws IOException{
    }

    public void onInterrupt(CometEvent event) throws IOException{   
    }

    public String getClientId() {
        return clientId;
    }

    public Collection<String> getChannels() {
        return channels;
    }

    public void addChannel(String channel) {
        if (channels.contains(channel)) {
            throw new IllegalArgumentException(channel);
        }
        channels.add(channel);
    }

    public boolean containsChannel(String channel) {
        return channels.contains(channel);
    }

    public boolean removeChannel(String channel) {
        return channels.remove(channel);
    }

    public boolean isSuspended() {
        return isSuspended;
    }

    public void setSuspended(boolean isSuspended) {
        this.isSuspended = isSuspended;
    }
   
    public int getRemotePort() {
        return remotePort;
    }
}
