/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 



package com.sun.grizzly.http;

import com.sun.grizzly.http.util.MessageBytes;
import com.sun.grizzly.http.util.UDecoder;



/**
 * This is a low-level, efficient representation of a server request. Most 
 * fields are GC-free, expensive operations are delayed until the  user code 
 * needs the information.
 *
 * Processing is delegated to modules, using a hook mechanism.
 * 
 * This class is not intended for user code - it is used internally by tomcat
 * for processing the request in the most efficient way. Users ( servlets ) can
 * access the information using a facade, which provides the high-level view
 * of the request.
 *
 * For lazy evaluation, the request uses the getInfo() hook. The following ids
 * are defined:
 * <ul>
 *  <li>req.encoding - returns the request encoding
 *  <li>req.attribute - returns a module-specific attribute ( like SSL keys, etc ).
 * </ul>
 *
 * Tomcat defines a number of attributes:
 * <ul>
 *   <li>"org.apache.tomcat.request" - allows access to the low-level
 *       request object in trusted applications 
 * </ul>
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author Alex Cruikshank [alex@epitonic.com]
 * @author Hans Bergsten [hans@gefionsoftware.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class HttpRequestImpl extends HttpPacketImpl implements HttpRequest {


    // ----------------------------------------------------- Instance Variables


    private int serverPort = -1;
    private MessageBytes serverNameMB = MessageBytes.newInstance();

    private String localHost;

    private int remotePort;
    private int localPort;

    private MessageBytes schemeMB = MessageBytes.newInstance();

    private MessageBytes methodMB = MessageBytes.newInstance();
    private MessageBytes uriMB = MessageBytes.newInstance();
    private MessageBytes queryMB = MessageBytes.newInstance();
    private MessageBytes protocolMB = MessageBytes.newInstance();

    // remote address/host
    private MessageBytes remoteAddrMB = MessageBytes.newInstance();
    private MessageBytes localNameMB = MessageBytes.newInstance();
    private MessageBytes remoteHostMB = MessageBytes.newInstance();
    private MessageBytes localAddrMB = MessageBytes.newInstance();

    /**
     * URL decoder.
     */
    private UDecoder urlDecoder = new UDecoder();


    /**
     * HTTP specific fields. (remove them ?)
     */
    protected MessageBytes contentTypeMB = null;
    private boolean charEncodingParsed = false;

    private HttpResponse response;

    // ----------------------------------------------------------- Constructors


    public HttpRequestImpl() {
        schemeMB.setString("http");
        methodMB.setString("GET");
        /* SJSWS 6376484
        uriMB.setString("/");
        */
        queryMB.setString("");
        protocolMB.setString("HTTP/1.0");

    }

    public UDecoder getURLDecoder() {
        return urlDecoder;
    }

    // -------------------- Request data --------------------

    public MessageBytes getProtocolMB() {
        return protocolMB;
    }

    @Override
    public String getProtocol() {
        return getProtocolMB().toString();
    }

    public MessageBytes getSchemeMB() {
        return schemeMB;
    }

    @Override
    public String getScheme() {
        return schemeMB.toString();
    }
    
    public MessageBytes getMethodMB() {
        return methodMB;
    }

    @Override
    public String getMethod() {
        return methodMB.toString();
    }
    
    public MessageBytes requestURI() {
        return uriMB;
    }

    @Override
    public String getRequestURI() {
        return uriMB.toString();
    }

    public MessageBytes getQueryStringMB() {
        return queryMB;
    }

    @Override
    public String getQueryString() {
        return queryMB.toString();
    }

    /** 
     * Return the buffer holding the server name, if
     * any. Use isNull() to check if there is no value
     * set.
     * This is the "virtual host", derived from the
     * Host: header.
     */
    public MessageBytes getServerNameMB() {
	return serverNameMB;
    }

    @Override
    public String getServerName() {
        return serverNameMB.toString();
    }

    @Override
    public int getServerPort() {
        return serverPort;
    }
    
    @Override
    public void setServerPort(int serverPort ) {
	this.serverPort=serverPort;
    }

    public MessageBytes getRemoteAddrMB() {
	return remoteAddrMB;
    }

    @Override
    public String getRemoteAddr() {
        return remoteAddrMB.toString();
    }

    public MessageBytes getRemoteHostMB() {
	return remoteHostMB;
    }

    @Override
    public String getRemoteHost() {
        return remoteHostMB.toString();
    }

    public MessageBytes getLocalNameMB() {
	return localNameMB;
    }    

    @Override
    public String getLocalName() {
        return localNameMB.toString();
    }

    public MessageBytes getLocalAddrMB() {
	return localAddrMB;
    }
    
    @Override
    public String getLocalAddr() {
        return localAddrMB.toString();
    }

    @Override
    public String getLocalHost() {
	return localHost;
    }

    @Override
    public void setLocalHost(String host) {
	this.localHost = host;
    }    
    
    @Override
    public int getRemotePort(){
        return remotePort;
    }
        
    @Override
    public void setRemotePort(int port){
        this.remotePort = port;
    }
    
    @Override
    public int getLocalPort(){
        return localPort;
    }
        
    @Override
    public void setLocalPort(int port){
        this.localPort = port;
    }

    // -------------------- encoding/type --------------------


    /**
     * Get the character encoding used for this request.
     */
    @Override
    public String getCharacterEncoding() {

        if (characterEncoding != null || charEncodingParsed) {
            return characterEncoding;
        }

        characterEncoding = ContentType.getCharsetFromContentType(getContentType());
        charEncodingParsed = true;

        return characterEncoding;
    }


    @Override
    public long getContentLength() {
        if( contentLength > -1 ) return contentLength;

        MessageBytes clB = headers.getValue("content-length");
        contentLength = (clB == null || clB.isNull()) ? -1 : clB.getLong();

        return contentLength;
    }


    @Override
    public String getContentType() {
        if (contentType != null) {
            return contentType;
        }
        
        getContentTypeMB();
        if ((contentTypeMB == null) || contentTypeMB.isNull()) 
            return null;
        contentType = contentTypeMB.toString();
        
        return contentType;
    }

    public MessageBytes getContentTypeMB() {
        if (contentTypeMB == null)
            contentTypeMB = headers.getValue("content-type");
        return contentTypeMB;
    }

    protected void setContentType(MessageBytes mb) {
        contentTypeMB=mb;
    }

    // -------------------- Associated response --------------------

    @Override
    public HttpResponse getResponse() {
        return response;
    }

    @Override
    public void setResponse(HttpResponse response) {
        this.response=response;
        response.setRequest( this );
    }

    // -------------------- debug --------------------

    @Override
    public String toString() {
	return "R( " + requestURI().toString() + ")";
    }

    // -------------------- Recycling -------------------- 
    @Override
    public void recycle() {
        super.recycle();

        contentTypeMB = null;
        charEncodingParsed = false;
        serverNameMB.recycle();
        serverPort=-1;
        localPort = -1;
        remotePort = -1;

        uriMB.recycle(); 
	queryMB.recycle();
	methodMB.recycle();
	protocolMB.recycle();

	// XXX Do we need such defaults ?
        schemeMB.recycle();
        schemeMB.setString("http");
	methodMB.setString("GET");
        
        queryMB.setString("");
        protocolMB.setString("HTTP/1.0");
    }
}
