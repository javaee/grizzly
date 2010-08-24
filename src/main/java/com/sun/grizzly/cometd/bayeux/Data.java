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

import java.util.Map;
import java.util.Iterator;
import java.util.Random;

/**
 * Data representation of client/server interaction.
 *
 *	window.parent.cometd.deliver([
 *           {
 *                    // user-sent data
 *                    "data": {
 *                           "someField":	["some", "random", "values"],
 *                    },
 *                    // the usual message meta-data
 *                    "channel":		"/originating/channel",
 *                    // event ID
 *                    "id":			"slkjdlkj32",
 *                    "timestamp":	"TimeAtServer",
 *                    // optional meta-data
 *                    "authToken":	"SOME_NONCE_THAT_NEEDS_TO_BE_PROVIDED_SUBSEQUENTLY"
 *            },
 *            {
 *                    "data": {
 *                            "blah blah":	["more", "random", "values"],
 *                    },
 *                    // the usual message meta-data
 *                    "channel":		"/originating/channel",
 *                    // event ID
 *                    "id":			"slkjdlkj31",
 *                    "timestamp":	"TimeAtServer",
 *                    "authToken":	"SOME_NONCE_THAT_NEEDS_TO_BE_PROVIDED_SUBSEQUENTLY"
 *            }
 *            // , ...
 *    ]);
 *
 * @author Jeanfrancois Arcand
 */
public class Data extends VerbBase {
    
    private Random random = new Random();
    
    private Map<String,Object> data;

    private String connectionId;

    public Data() {
        type = Verb.Type.DATA;
    }

    public Map<String,Object> getMapData() {
        return data;
    }

    public void setMapData(Map<String,Object> data) {
        this.data = data;
    }
    
    public String toJSON(){
        StringBuffer response = new StringBuffer();
        
        response.append("\"data\":{");        
        
        if (data != null) {
            Iterator<String> iterator = data.keySet().iterator();
            String key = "";
            Object value;
            int size = data.size() -1;
            int i = 0;
            while(iterator.hasNext()){
                key = iterator.next();
                value = data.get(key);
                response.append("\"");
                response.append(key);
                response.append("\":");
                if (value instanceof String){
                    response.append("\"" + value + "\"");   
                } else {
                    response.append(value);  
                }
            
                if (i++ < size){
                    response.append(",");
                }
            }
        }
        response.append("}"); 
        return response.toString();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
}
