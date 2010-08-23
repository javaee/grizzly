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

/**
 * Bayeux Advise implementation. 
 * See http://svn.xantus.org/shortbus/trunk/bayeux/protocol.txt for the technical
 * details.
 * 
 * The advice system provides a way for servers to inform clients of their
 * preferred mode of client operation. In conjunction with server-enforced limits,
 * Bayeux implementations can prevent resource exhaustion and inelegant failure in
 * several important edge cases. Since transport evenlopes are "pluggable" in
 * order to support differing "on the wire" behaviors, advice values may also vary
 * by transport type, even for the same conditions. What follows is a breif
 * description of the enumerations that are used in advices and their canonical
 * meanings.
 *
 * @author Jeanfrancois Arcand
 */
public class Advice extends VerbBase{
    
    // "none", "retry", "handshake", "recover" (deprecated)
    private static final String[] VALID_RECONNECT =
            new String[] { "none", "retry", "handshake", "recover" };

    private String reconnect = "retry";
    
    
    private Integer interval = 0;


    private Boolean multipleClients = Boolean.FALSE;


    private String[] hosts = null;
    

    private String transport = "\"long-polling\":\t{\n";
    
    
    public Advice() {
        type = Verb.Type.ADVICE;
    }

    public String getReconnect() {
        return reconnect;
    }

    public void setReconnect(String reconnect) {
        this.reconnect = reconnect;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public Boolean getMultipleClients() {
        return multipleClients;
    }

    public void setMultipleClients(Boolean multipleClients) {
        this.multipleClients = multipleClients;
    }

    public String[] getHosts() {
        return hosts;
    }

    public void setHosts(String[] hosts) {
        this.hosts = hosts;
    }
        
    @Override
    public String toString(){
        return toJSON();
    }
    
    public String toJSON() {        
        StringBuilder jsonSb = new StringBuilder(
                "\"advice\":{" 
                + "\"reconnect\":\"" + reconnect + "\","
                + "\"interval\":" + interval + ","
                + "\"multiple-clients\":" + multipleClients
                );

        if (hosts != null && hosts.length > 0) {
            jsonSb.append("\"hosts\":[");
            boolean first_ = true;
            for (int i=0 ;i<hosts.length; i++) {
                if (first_) {
                    first_ = false;
                } else {
                    jsonSb.append(",");
                }

                jsonSb.append("\"").append(hosts[i]).append("\"");
            }
            jsonSb.append("]");
        }

        jsonSb.append("}");         
        return jsonSb.toString();
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }
        
    public boolean isValid() {
        for (int i=0;i<VALID_RECONNECT.length;i++) {
            if (VALID_RECONNECT[i].equals(reconnect)) {
                return true;
            }
        }
        return false;
    }
}
