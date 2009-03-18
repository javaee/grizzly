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

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.cometd.bayeux.DeliverResponse;
import com.sun.grizzly.cometd.bayeux.End;
import com.sun.grizzly.util.LinkedTransferQueue;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
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

    static final String DELIVER_RESPONSE_END = "DELIVER_RESPONSE_END";
    
    private final static Logger logger = SelectorThread.logger();
    
    private CometdRequest req;    
    
    private CometdResponse res;    
   
    private Collection<String> channels = new LinkedTransferQueue<String>();

    private Collection<String> unmodifiableChannels = null;
    
    private BayeuxParser bayeuxParser;
    
    private boolean isSuspended = false;

    private int remotePort = -1;

    private volatile boolean ended = false;


    public DataHandler(BayeuxParser bayeuxParser){
        this.bayeuxParser = bayeuxParser;
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
                        res.write("]*/");
                        res.flush();
                        ended = true;
                    }
                    event.getCometContext().resumeCometHandler(this);
                }
            }
        }  catch (Throwable t){
           logger.log(Level.SEVERE,"DataHandler.onEvent",t);
        } 
    }

    
    public void onInitialize(CometEvent event) throws IOException{  
    }


    public void onTerminate(CometEvent event) throws IOException{
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

    public synchronized boolean containsChannel(String channel) {
        return channels.contains(channel);
    }

    public synchronized boolean removeChannel(String channel) {
        unmodifiableChannels = null;
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
