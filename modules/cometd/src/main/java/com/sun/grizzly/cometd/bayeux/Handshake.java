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
 * Bayeux Handshake implementation. 
 * See http://svn.xantus.org/shortbus/trunk/bayeux/protocol.txt for the technical
 * details.
 *
 * This is an example of the messages exchanged during a
 * connection setup process:
 *
 *	// from client, to server
 *	[
 *		{
 *			"channel":			"/meta/handshake",
 *			// all meta channel messages MUST contain the protocol version the
 *			// client expects
 *			"version":			0.1,
 *			// the oldest version of the protocol that this client will support
 *			"minimumVersion":	0.1,
 *			"supportedConnectionTypes":	["iframe", "flash", "http-polling"],
 *			// the authScheme is outside the realm of this specification and
 *			// provided here for illustration only. It's also optional.
 *			"authScheme":		"SHA1",
 *			// the authUser and authToken are optional and authScheme dependent
 *			"authUser":			"alex",
 *			"authToken":		"HASHJIBBERISH"
 *		}
 *		// servers MUST ignore other messages in the envelope should the first
 *		// be a handshake request
 *	]
 *
 *	// from server, to client
 *	[
 *		{
 *			"channel":					"/meta/handshake",
 *			// preferred protocol version
 *			"version":					0.1,
 *			// the oldest version of the protocol that this server will support
 *			"minimumVersion":			0.1,
 *			"supportedConnectionTypes":	["iframe", "flash", "http-polling"],
 *			"clientId":					"SOME_UNIQUE_CLIENT_ID",
 *			"authSuccessful":			true,
 *			// authToken is auth scheme dependent and entirely optional
 *			"authToken":				"SOME_NONCE_THAT_NEEDS_TO_BE_PROVIDED_SUBSEQUENTLY",
 *			// advice determines the client behavior on errors
 *			"advice":	{
 *				"reconnect": "retry", // one of "none", "retry", "handshake", "recover"
 *
 *				// transport specializations of the top-level generalized
 *				// advice
 *				"transport": {
 *					"iframe": { },
 *					"flash": { },
 *					"http-polling": {
 *						// delay before reconnecting
 *						"interval": 5000 // ms
 *					}
 *				}
 *			}
 *		}
 *		// servers MUST send only a handshake message in response to a handshake request
 *	]
 *
 * @author Jeanfrancois Arcand
 */
abstract class Handshake extends VerbBase{
    public static final String META_HANDSHAKE = "/meta/handshake";
    
    private String version = "1.0";
    
    private String minimumVersion = "0.9";
    
    private String[] supportedConnectionTypes 
            = new String[] {"long-polling", "callback-polling"};
    
    private String authScheme="";
    
    private String authUser="";

    protected Boolean authSuccessful = Boolean.TRUE;
    
    protected Handshake() {
        type = Verb.Type.HANDSHAKE;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getMinimumVersion() {
        return minimumVersion;
    }

    public void setMinimumVersion(String minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    public String[] getSupportedConnectionTypes() {
        return supportedConnectionTypes;
    }

    public void setSupportedConnectionTypes(String[] supportedConnectionTypes) {
        this.supportedConnectionTypes = supportedConnectionTypes;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public void setAuthScheme(String authScheme) {
        this.authScheme = authScheme;
    }

    public String getAuthUser() {
        return authUser;
    }

    public void setAuthUser(String authUser) {
        this.authUser = authUser;
    }

    public boolean isValid() {
        float ver;

        try {
            ver = Float.parseFloat(getVersion());
        } catch(Exception ex) {
            return false;
        }

        //XXX need to check supportedConnectionTypes later

        return (ver <= 1.0f) && (ver >= 0.9f) &&
                META_HANDSHAKE.equals(getChannel());
    }

    protected String toJSON(boolean isResponse) {
        StringBuilder sb = new StringBuilder(
                getJSONPrefix() + "{"
                + "\"channel\":\"" + channel + "\""
                + ",\"version\":\"" + version + "\""
                );
        if (supportedConnectionTypes != null) {
            sb.append(",\"supportedConnectionTypes\":[");
            boolean first = true;
            for (String connType : supportedConnectionTypes) {
                if (!first) {
                    sb.append(",");  
                } else {
                    first = false;
                }
                sb.append("\"").append(connType).append("\"");
            }
            sb.append("]");
        }

        if (minimumVersion != null) {
            sb.append(",\"minimumVersion\":\"").append(minimumVersion).append("\"");
        }
        if (ext != null) {
            sb.append(",").append(ext.toJSON());
        }
        if (id != null) {
            sb.append(",\"id\":\"").append(id).append("\"");
        }

        if (isResponse) {
            sb.append(",\"clientId\":\"").append(clientId).append("\"");
            sb.append(",\"successful\":").append(successful);
            if (advice != null) {
                sb.append(",").append(advice.toJSON());
            }
            if (authSuccessful != null) {
                sb.append(",\"authSuccessful\":").append(authSuccessful);
            }
        }

        sb.append("}").append(getJSONPostfix());
        return sb.toString();
    } 

    protected String toErrorResponseJSON() {
        StringBuilder sb = new StringBuilder(
                getJSONPrefix() + "{"
                + "\"channel\":\"" + channel + "\""
                + ",\"successful\":" + successful
                + ",\"error\":\"" + error + "\""
                );

        if (version != null) {
            sb.append(",\"version\":\"").append(version).append("\"");
        }
        if (supportedConnectionTypes != null) {
            sb.append(",\"supportedConnectionTypes\":[");
            boolean first = true;
            for (String connType : supportedConnectionTypes) {
                if (!first) {
                    sb.append(",");  
                } else {
                    first = false;
                }
                sb.append("\"").append(connType).append("\"");
            }
            sb.append("]");
        }

        if (minimumVersion != null) {
            sb.append(",\"minimumVersion\":\"").append(minimumVersion).append("\"");
        }
        if (ext != null) {
            sb.append(",").append(ext.toJSON());
        }
        if (id != null) {
            sb.append(",\"id\":\"").append(id).append("\"");
        }
      
        if (authSuccessful != null) {
            sb.append(",\"authSuccessful\":").append(authSuccessful);
        }

        sb.append("}").append(getJSONPostfix());
        return sb.toString();
    }
}
