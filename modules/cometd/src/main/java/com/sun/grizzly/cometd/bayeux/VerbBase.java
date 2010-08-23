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
 * Abstract Verb implementation shared by all meta channel verb. 
 * All cometd /meta/ channel messages contain a protocol version number and all
 * messages generated from (or routed through) an event router contain a message
 * ID which is unique to the router. No randomness is required in these
 * identifiers although routers are expected to drop messages which they have
 * previously "seen".
 *
 * @author Jeanfrancois Arcand
 */
public abstract class VerbBase implements Verb{
    public static final String ARRAY_START = "[";

    public static final String ARRAY_END = "]";
    
    protected String id = "0";
    
    protected String dataId = "0";
    
    protected Advice advice;   
            
    protected String channel;
    
    protected Data data;

    protected Type type;
   
    protected String authToken;
    
    protected Boolean successful = Boolean.TRUE;
    
   
    protected String error = null;  
    
        
    protected Ext ext;

    // this is used to writing appropriate JSON format
    protected boolean first = false;
    protected boolean follow = false;
    protected boolean last = false;
    
    protected String clientId;
    
    protected String metaChannel = "";
 
    public VerbBase() {
    }

    
    public String getChannel() {
        return channel;
    }

    
    public void setChannel(String channel) {
        this.channel = channel;
    }

    
    public String getAuthToken() {
        return authToken;
    }

    
    public void setAuthToken(String autheToken) {
        this.authToken = autheToken;
    }


    public Type getType() {
        return type;
    }
    

    public Ext getExt() {
        return ext;
    }

    public void setExt(Ext ext) {
        this.ext = ext;
    }
       
    @Override
    public String toString(){
        return toJSON();
    }
 
    public Advice getAdvice() {
        return advice;
    }

    public void setAdvice(Advice advice) {
        this.advice = advice;
    } 

    /**
     * Since advice is optional, null advice is valid.
     */
    public boolean hasValidAdvice() {
        return ((getAdvice() != null)? getAdvice().isValid() : true);
    }
    
    
    public void setId(String id){
        this.id = id;        
    }
    
    
    public String getId(){
        return id;
    }
    
    
    public void setData(Data data){
        this.data = data;
    }
    
    
    public Data getData(){
        return data;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    public boolean isFollow() {
        return follow;
    }

    public void setFollow(boolean follow) {
        this.follow = follow;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isValid() {
        return true;
    }

    protected String getJSONPrefix() {
        String prefix = null;
        if (first) {
            prefix = ARRAY_START;
        } else if (follow) {
            prefix = ", ";
        } else {
            prefix = "";
        }
        return prefix;
    }

    protected String getJSONPostfix() {
        return ((last)? ARRAY_END : "");
    }
    
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }   
    
    
    /**
     * To be overriden by subclass used in isValid().
     */
    public String getMetaChannel() {
        return metaChannel;
    }
}
