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

import com.sun.grizzly.cometd.bayeux.Verb;
import com.sun.grizzly.cometd.bayeux.VerbUtils;
import com.sun.grizzly.cometd.util.JSONParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;


/**
 * From the Spec, an EventRouter is:
 *
 * The cometdContext router is channel based, with rules based on longest-prefix match
 * dispatching for registered listeners. The "/meta/*" channel is reserved for
 * communications with the cometdContext router itself (including connection setup,
 * tear-down, and re-connection), and all conformant clients and servers must
 * implement the following meta-channel verbs:
 *
 *      * handshake
 *	* connect
 *	* reconnect
 *	* disconnect
 *	* status
 *	* subscribe
 * 	* unsubscribe
 *	* ping
 *
 * @author Jeanfrancois Arcand
 * @author TAKAI, Naoto
 */
public class EventRouterImpl implements EventRouter{
       
    private final static String JSON_MESSAGES = "message";
    
    private final BayeuxParser bayeuxParser;
    
    public EventRouterImpl(BayeuxParser bayeuxParser){
        this.bayeuxParser = bayeuxParser;
    }

    /**
     * Route a CometD message.
     */    
    @SuppressWarnings("unchecked")
    public void route(final CometdRequest req,final CometdResponse res)
            throws IOException {

        String[] messages = getMessages(req);
        CometdContext cometdContext = null;
        if (messages != null && messages.length > 0){
            for(String message: messages){
                List<Verb> verbs = VerbUtils.parseRequest(JSONParser.parse(message));

                // Notify our listener;
                for (final Verb verb : verbs) {
                    cometdContext = new CometdContext() {

                        public CometdRequest getRequest() {
                            return req;
                        }

                        public CometdResponse getResponse() {
                            return res;
                        }

                        public Verb getVerb() {
                           return verb;
                        }
                    };
                    bayeuxParser.parse(cometdContext);
                }
            }
        } else {
             throw new IOException("Invalid request");
        }
    }   

    private String[] getMessages(CometdRequest req) throws IOException {
        String contentType = req.getContentType();
        String[] messages = null;
        if (contentType != null) {
            int semicolon = contentType.indexOf(';');
            if (semicolon >= 0) {
                contentType = contentType.substring(0, semicolon).trim();
            } else {
                contentType = contentType.trim();
            }

            if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType)) {
                messages = req.getParameterValues(JSON_MESSAGES); 
            } else if ("text/json".equalsIgnoreCase(contentType)) {
                int len = req.getContentLength();
                ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
                int s = -1;
                byte[] b = new byte[1024];
                InputStream is = req.getInputStream();
                while (len > 0 && (s = is.read(b)) > 0) {
                    baos.write(b, 0, s);
                    len -= s;
                }
                messages = new String[] { baos.toString(req.getCharacterEncoding()) };
            }
        }

        return messages;
    }
}
