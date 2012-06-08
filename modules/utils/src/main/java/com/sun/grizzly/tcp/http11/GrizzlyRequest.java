/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp.http11;

import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.B2CConverter;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.Cookie;
import com.sun.grizzly.util.http.Cookies;
import com.sun.grizzly.util.http.Enumerator;
import com.sun.grizzly.util.http.FastHttpDateFormat;
import com.sun.grizzly.util.http.Globals;
import com.sun.grizzly.util.http.ParameterMap;
import com.sun.grizzly.util.http.Parameters;
import com.sun.grizzly.util.http.ServerCookie;
import com.sun.grizzly.util.http.StringParser;
import com.sun.grizzly.util.res.StringManager;

import javax.security.auth.Subject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper object for the Coyote request.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 1.2 $ $Date: 2007/03/14 02:15:42 $
 */

public class GrizzlyRequest {
    private static final Random random = new Random();

    // ----------------------------------------------------------- Constructors


    public GrizzlyRequest() {
         // START OF SJSAS 6231069
        formats = (SimpleDateFormat[]) staticDateFormats.get();
        formats[0].setTimeZone(TimeZone.getTimeZone("GMT"));
        formats[1].setTimeZone(TimeZone.getTimeZone("GMT"));
        formats[2].setTimeZone(TimeZone.getTimeZone("GMT"));
        // END OF SJSAS 6231069
    }


    // ------------------------------------------------------------- Properties
    /**
     * The match string for identifying a session ID parameter.
     */
    private static final String match =
        ";" + Globals.SESSION_PARAMETER_NAME + "=";

    /**
     * The match string for identifying a session ID parameter.
     */
    private static final char[] SESSION_ID = match.toCharArray();
    
    
    private GrizzlySession session;
    
    
    // -------------------------------------------------------------------- //
    
    
    /**
     * Not Good. We need a better mechanism.
     * TODO: Move Session Management out of here
     */
    private static Map<String,GrizzlySession> sessions = new ConcurrentHashMap<String, GrizzlySession>();
    
    
    /**
     * Scheduled Thread that clean the cache every XX seconds.
     */
    private final static ScheduledThreadPoolExecutor sessionExpirer
        = new ScheduledThreadPoolExecutor(1, new ThreadFactory(){
            public Thread newThread(Runnable r) {
                return new SchedulerThread(r,"Grizzly");
            }
        });


    /**
     * Simple daemon thread.
     */
    private static class SchedulerThread extends Thread{
        public SchedulerThread(Runnable r, String name){
            super(r, name);
            setDaemon(true);
        }
    }
    
    
    /**
     * TODO: That code is far from optimal and needs to be rewrite appropriately.
     */ 
    static{
        sessionExpirer.scheduleAtFixedRate(new Runnable(){
            public void run(){
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<String,GrizzlySession>> iterator = sessions.entrySet().iterator();
                Map.Entry<String,GrizzlySession> entry;
                while (iterator.hasNext()){
                    entry = iterator.next();
                    
                    if (entry.getValue().getSessionTimeout() == -1) continue;
                    
                    if (currentTime - entry.getValue().getTimestamp() > 
                            entry.getValue().getSessionTimeout()){
                        entry.getValue().setIsValid(false);
                        iterator.remove();
                    }
                }
            }
            
        },5,5, TimeUnit.SECONDS);   
    }

    
    // --------------------------------------------------------------------- //
    
    /**
     * Grizzly request.
     */
    protected Request request;

    /**
     * Set the Coyote request.
     * 
     * @param request The Grizzly request
     */
    public void setRequest(Request request) {
        this.request = request;
        inputBuffer.setRequest(request);
    }

    /**
     * Get the Coyote request.
     */
    public Request getRequest() {
        return (this.request);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());


    /**
     * The set of cookies associated with this Request.
     */
    protected Cookie[] cookies = null;

    // START OF SJSAS 6231069
    /*
    protected SimpleDateFormat formats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    }*/

    /**
     * The set of SimpleDateFormat formats to use in getDateHeader().
     */
    private static ThreadLocal staticDateFormats = new ThreadLocal() {
        @Override
        protected Object initialValue() {
            SimpleDateFormat[] f = new SimpleDateFormat[3];
            f[0] = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", 
                                        Locale.US);
            f[1] = new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", 
                                        Locale.US);
            f[2] = new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US);
            return f;
        }
    };
    protected SimpleDateFormat formats[];
    // END OF SJSAS 6231069    

    /**
     * The default Locale if none are specified.
     */
    protected static final Locale defaultLocale = Locale.getDefault();


    /**
     * The attributes associated with this Request, keyed by attribute name.
     */
    protected HashMap<String, Object> attributes = new HashMap<String, Object>();


    /**
     * List of read only attributes for this Request.
     */
    private HashMap readOnlyAttributes = new HashMap();


    /**
     * The preferred Locales associated with this Request.
     */
    protected ArrayList locales = new ArrayList();


    /**
     * Internal notes associated with this request by Catalina components
     * and event listeners.
     */
    private transient HashMap notes = new HashMap();


    /**
     * Authentication type.
     */
    protected String authType = null;


    /**
     * The current dispatcher type.
     */
    protected Object dispatcherType = null;


    /**
     * The associated input buffer.
     */
    protected GrizzlyInputBuffer inputBuffer = new GrizzlyInputBuffer();


    /**
     * GrizzlyInputStream.
     */
    protected GrizzlyInputStream inputStream = 
        new GrizzlyInputStream(inputBuffer);


    /**
     * Reader.
     */
    protected GrizzlyReader reader = new GrizzlyReader(inputBuffer);


    /**
     * Using stream flag.
     */
    protected boolean usingInputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingReader = false;


    /**
     * User principal.
     */
    protected Principal userPrincipal = null;


    /**
     * GrizzlySession parsed flag.
     */
    protected boolean sessionParsed = false;


    /**
     * Request parameters parsed flag.
     */
    protected boolean requestParametersParsed = false;


     /**
     * Cookies parsed flag.
     */
    protected boolean cookiesParsed = false;


    /**
     * Secure flag.
     */
    protected boolean secure = false;

    
    /**
     * The Subject associated with the current AccessControllerContext
     */
    protected Subject subject = null;


    /**
     * Post data buffer.
     */
    protected static final int CACHED_POST_LEN = 8192;
    protected byte[] postData = null;


    /**
     * Hash map used in the getParametersMap method.
     */
    protected ParameterMap parameterMap = new ParameterMap();


    /**
     * The current request dispatcher path.
     */
    protected Object requestDispatcherPath = null;


    /**
     * Was the requested session ID received in a cookie?
     */
    protected boolean requestedSessionCookie = false;


    /**
     * The requested session ID (if any) for this request.
     */
    protected String requestedSessionId = null;


    /**
     * Was the requested session ID received in a URL?
     */
    protected boolean requestedSessionURL = false;


    /**
     * The socket through which this Request was received.
     */
    protected Socket socket = null;


    /**
     * Parse locales.
     */
    protected boolean localesParsed = false;


    /**
     * The string parser we will use for parsing request lines.
     */
    private StringParser parser = new StringParser();

    /**
     * Local port
     */
    protected int localPort = -1;

    /**
     * Remote address.
     */
    protected String remoteAddr = null;


    /**
     * Remote host.
     */
    protected String remoteHost = null;

    
    /**
     * Remote port
     */
    protected int remotePort = -1;
    
    /**
     * Local address
     */
    protected String localName = null;


    /**
     * Local address
     */
    protected String localAddr = null;


    // START S1AS 4703023
    /**
     * The current application dispatch depth.
     */
    private int dispatchDepth = 0;

    /**
     * The maximum allowed application dispatch depth.
     */
    private static int maxDispatchDepth = Constants.DEFAULT_MAX_DISPATCH_DEPTH;
    // END S1AS 4703023


    // START SJSAS 6346226
    private String jrouteId;
    // END SJSAS 6346226

    
    /**
     * The response with which this request is associated.
     */
    protected GrizzlyResponse response = null;

    /**
     * Return the Response with which this Request is associated.
     */
    public GrizzlyResponse getResponse() {
        return response;
    }

    /**
     * Set the Response with which this Request is associated.
     *
     * @param response The new associated response
     */
    public void setResponse(GrizzlyResponse response) {
        this.response = response;
    }
    
    // --------------------------------------------------------- Public Methods

    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    public void recycle() {

        dispatcherType = null;
        requestDispatcherPath = null;

        authType = null;
        inputBuffer.recycle();
        usingInputStream = false;
        usingReader = false;
        userPrincipal = null;
        subject = null;
        sessionParsed = false;
        requestParametersParsed = false;
        cookiesParsed = false;
        locales.clear();
        localesParsed = false;
        secure = false;
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;        

        attributes.clear();
        cookies = null;
        requestedSessionId = null;        
        session = null;
        dispatchDepth = 0; // S1AS 4703023

        parameterMap.setLocked(false);
        parameterMap.clear();

        if (System.getSecurityManager() != null) {
            if (inputStream != null) {
                inputStream.clear();
                inputStream = null;
            }
            if (reader != null) {
                reader.clear();
                reader = null;
            }
        }

    }


    // -------------------------------------------------------- Request Methods


    /**
     * Return the authorization credentials sent with this request.
     */
    public String getAuthorization() {
        return (request.getHeader(Constants.AUTHORIZATION_HEADER));
    }

    /**
     * Set the authorization credentials sent with this request.
     *
     * @param authorization The new authorization credentials
     */
    public void setAuthorization(String authorization) {
        // Not used
    }


    /**
     * Return the Socket (if any) through which this Request was received.
     * This should <strong>only</strong> be used to access underlying state
     * information about this Socket, such as the SSLSession associated with
     * an SSLSocket.
     */
    public Socket getSocket() {
        return (socket);
    }

    /**
     * Set the Socket (if any) through which this Request was received.
     *
     * @param socket The socket through which this request was received
     */
    public void setSocket(Socket socket) {
        this.socket = socket;
        remoteHost = null;
        remoteAddr = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;
    }


    /**
     * Return the input stream associated with this Request.
     */
    public InputStream getStream() {
        if (inputStream == null) {
            inputStream = new GrizzlyInputStream(inputBuffer);
        }
        return inputStream;
    }

    /**
     * Set the input stream associated with this Request.
     *
     * @param stream The new input stream
     */
    public void setStream(InputStream stream) {
        // Ignore
    }


    /**
     * URI byte to char converter (not recycled).
     */
    protected B2CConverter URIConverter = null;

    /**
     * Return the URI converter.
     */
    public B2CConverter getURIConverter() {
        return URIConverter;
    }

    /**
     * Set the URI converter.
     * 
     * @param URIConverter the new URI connverter
     */
    public void setURIConverter(B2CConverter URIConverter) {
        this.URIConverter = URIConverter;
    }


    // ------------------------------------------------- Request Public Methods


    /**
     * Create and return a GrizzlyInputStream to read the content
     * associated with this Request.
     *
     * @exception IOException if an input/output error occurs
     */
    public GrizzlyInputStream createInputStream() 
        throws IOException {
        if (inputStream == null) {
            inputStream = new GrizzlyInputStream(inputBuffer);
        }
        return inputStream;
    }


    /**
     * Perform whatever actions are required to flush and close the input
     * stream or reader, in a single operation.
     *
     * @exception IOException if an input/output error occurs
     */
    public void finishRequest() throws IOException {
        // The reader and input stream don't need to be closed
    }


    /**
     * Return the object bound with the specified name to the internal notes
     * for this request, or <code>null</code> if no such binding exists.
     *
     * @param name Name of the note to be returned
     */
    public Object getNote(String name) {
        return (notes.get(name));
    }


    /**
     * Return an Iterator containing the String names of all notes bindings
     * that exist for this request.
     */
    public Iterator getNoteNames() {
        return (notes.keySet().iterator());
    }


    /**
     * Remove any object bound to the specified name in the internal notes
     * for this request.
     *
     * @param name Name of the note to be removed
     */
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * Bind an object to a specified name in the internal notes associated
     * with this request, replacing any existing binding for this name.
     *
     * @param name Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


    /**
     * Set the content length associated with this Request.
     *
     * @param length The new content length
     */
    public void setContentLength(int length) {
        // Not used
    }


    /**
     * Set the content type (and optionally the character encoding)
     * associated with this Request.  For example,
     * <code>text/html; charset=ISO-8859-4</code>.
     *
     * @param type The new content type
     */
    public void setContentType(String type) {
        // Not used
    }


    /**
     * Set the protocol name and version associated with this Request.
     *
     * @param protocol Protocol name and version
     */
    public void setProtocol(String protocol) {
        // Not used
    }


    /**
     * Set the IP address of the remote client associated with this Request.
     *
     * @param remoteAddr The remote IP address
     */
    public void setRemoteAddr(String remoteAddr) {
        // Not used
    }


    /**
     * Set the fully qualified name of the remote client associated with this
     * Request.
     *
     * @param remoteHost The remote host name
     */
    public void setRemoteHost(String remoteHost) {
        // Not used
    }


    /**
     * Set the name of the scheme associated with this request.  Typical values
     * are <code>http</code>, <code>https</code>, and <code>ftp</code>.
     *
     * @param scheme The scheme
     */
    public void setScheme(String scheme) {
        // Not used
    }


    /**
     * Set the value to be returned by <code>isSecure()</code>
     * for this Request.
     *
     * @param secure The new isSecure value
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
    }


    /**
     * Set the name of the server (virtual host) to process this request.
     *
     * @param name The server name
     */
    public void setServerName(String name) {
        request.serverName().setString(name);
    }


    /**
     * Set the port number of the server to process this request.
     *
     * @param port The server port
     */
    public void setServerPort(int port) {
        request.setServerPort(port);
    }


    // ------------------------------------------------- ServletRequest Methods


    /**
     * Return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     *
     * @param name Name of the request attribute to return
     */
    public Object getAttribute(String name) {
        Object attr=attributes.get(name);

        if(attr!=null)
            return(attr);

        attr =  request.getAttribute(name);
        if(attr != null)
            return attr;
        // XXX Should move to Globals
        if(Constants.SSL_CERTIFICATE_ATTR.equals(name)) {
            request.action(ActionCode.ACTION_REQ_SSL_CERTIFICATE, null);
            attr = getAttribute(Globals.CERTIFICATES_ATTR);
            if(attr != null)
                attributes.put(name, attr);
        } else if( isSSLAttribute(name) ) {
            request.action(ActionCode.ACTION_REQ_SSL_ATTRIBUTE, 
                                 request);
            attr = request.getAttribute(Globals.CERTIFICATES_ATTR);
            if( attr != null) {
                attributes.put(Globals.CERTIFICATES_ATTR, attr);
            }
            attr = request.getAttribute(Globals.CIPHER_SUITE_ATTR);
            if(attr != null) {
                attributes.put(Globals.CIPHER_SUITE_ATTR, attr);
            }
            attr = request.getAttribute(Globals.KEY_SIZE_ATTR);
            if(attr != null) {
                attributes.put(Globals.KEY_SIZE_ATTR, attr);
            }
            attr = attributes.get(name);
        }
        return attr;
    }


    /**
     * Test if a given name is one of the special Servlet-spec SSL attributes.
     */
    static boolean isSSLAttribute(String name) {
        return Globals.CERTIFICATES_ATTR.equals(name) ||
            Globals.CIPHER_SUITE_ATTR.equals(name) ||
            Globals.KEY_SIZE_ATTR.equals(name);
    }

    /**
     * Return the names of all request attributes for this Request, or an
     * empty <code>Enumeration</code> if there are none.
     */
    public Enumeration<String> getAttributeNames() {
        return new Enumerator<String>(attributes.keySet(), true);
    }


    /**
     * Return the character encoding for this Request.
     */
    public String getCharacterEncoding() {
      return (request.getCharacterEncoding());
    }


    /**
     * Return the content length for this Request.
     */
    public int getContentLength() {
        return (request.getContentLength());
    }


    /**
     * Return the content type for this Request.
     */
    public String getContentType() {
        return (request.getContentType());
    }


    /**
     * Return the servlet input stream for this Request.  The default
     * implementation returns a servlet input stream created by
     * <code>createInputStream()</code>.
     *
     * @exception IllegalStateException if <code>getReader()</code> has
     *  already been called for this request
     * @exception IOException if an input/output error occurs
     */
    public GrizzlyInputStream getInputStream() throws IOException {

        if (usingReader)
            throw new IllegalStateException
                (sm.getString("request.getInputStream.ise"));

        usingInputStream = true;
        if (inputStream == null) {
            inputStream = new GrizzlyInputStream(inputBuffer);
        }
        return inputStream;

    }


    /**
     * Return the preferred Locale that the client will accept content in,
     * based on the value for the first <code>Accept-Language</code> header
     * that was encountered.  If the request did not specify a preferred
     * language, the server's default Locale is returned.
     */
    public Locale getLocale() {

        if (!localesParsed)
            parseLocales();

        if (locales.size() > 0) {
            return ((Locale) locales.get(0));
        } else {
            return (defaultLocale);
        }

    }


    /**
     * Return the set of preferred Locales that the client will accept
     * content in, based on the values for any <code>Accept-Language</code>
     * headers that were encountered.  If the request did not specify a
     * preferred language, the server's default Locale is returned.
     */
    public Enumeration getLocales() {

        if (!localesParsed)
            parseLocales();

        if (locales.size() > 0)
            return (new Enumerator(locales));
        ArrayList results = new ArrayList();
        results.add(defaultLocale);
        return (new Enumerator(results));

    }


    /**
     * Return the value of the specified request parameter, if any; otherwise,
     * return <code>null</code>.  If there is more than one value defined,
     * return only the first one.
     *
     * @param name Name of the desired request parameter
     */
    public String getParameter(String name) {

        if (!requestParametersParsed)
            parseRequestParameters();

        return request.getParameters().getParameter(name);

    }



    /**
     * Returns a {@link Map} of the parameters of this request.
     * Request parameters are extra information sent with the request.
     * For HTTP servlets, parameters are contained in the query string
     * or posted form data.
     *
     * @return A {@link Map} containing parameter names as keys
     *  and parameter values as map values.
     */
    public Map getParameterMap() {

        if (parameterMap.isLocked())
            return parameterMap;

        Enumeration e = getParameterNames();
        while (e.hasMoreElements()) {
            String name = e.nextElement().toString();
            String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);

        return parameterMap;

    }


    /**
     * Return the names of all defined request parameters for this request.
     */
    public Enumeration getParameterNames() {

        if (!requestParametersParsed)
            parseRequestParameters();

        return request.getParameters().getParameterNames();

    }


    /**
     * Return the defined values for the specified request parameter, if any;
     * otherwise, return <code>null</code>.
     *
     * @param name Name of the desired request parameter
     */
    public String[] getParameterValues(String name) {

        if (!requestParametersParsed)
            parseRequestParameters();

        return request.getParameters().getParameterValues(name);

    }


    /**
     * Return the protocol and version used to make this Request.
     */
    public String getProtocol() {
        return request.protocol().toString();
    }


    /**
     * Read the Reader wrapping the input stream for this Request.  The
     * default implementation wraps a <code>BufferedReader</code> around the
     * servlet input stream returned by <code>createInputStream()</code>.
     *
     * @exception IllegalStateException if <code>getInputStream()</code>
     *  has already been called for this request
     * @exception IOException if an input/output error occurs
     */
    public BufferedReader getReader() throws IOException {

        if (usingInputStream)
            throw new IllegalStateException
                (sm.getString("request.getReader.ise"));

        usingReader = true;
        inputBuffer.checkConverter();
        if (reader == null) {
            reader = new GrizzlyReader(inputBuffer);
        }
        return reader;

    }


    /**
     * Return the remote IP address making this Request.
     */
    public String getRemoteAddr() {
        if (remoteAddr == null) {

            if (socket != null) {
                InetAddress inet = socket.getInetAddress();
                remoteAddr = inet.getHostAddress();
            } else {
                request.action
                    (ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE, request);
                remoteAddr = request.remoteAddr().toString();
            }
        }
        return remoteAddr;
    }


    /**
     * Return the remote host name making this Request.
     */
    public String getRemoteHost() {
        if (remoteHost == null) {
            if (socket != null) {
                InetAddress inet = socket.getInetAddress();
                remoteHost = inet.getHostName();
            } else {
                request.action
                    (ActionCode.ACTION_REQ_HOST_ATTRIBUTE, request);
                remoteHost = request.remoteHost().toString();
            }
        }
        return remoteHost;
    }

    /**
     * Returns the Internet Protocol (IP) source port of the client
     * or last proxy that sent the request.
     */    
    public int getRemotePort(){
        if (remotePort == -1) {
            if (socket != null) {
                remotePort = socket.getPort();
            } else {
                request.action
                    (ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE, request);
                remotePort = request.getRemotePort();
            }
        }
        return remotePort;    
    }

    /**
     * Returns the host name of the Internet Protocol (IP) interface on
     * which the request was received.
     */
    public String getLocalName(){
       if (localName == null) {
            if (socket != null) {
                InetAddress inet = socket.getLocalAddress();
                localName = inet.getHostName();
            } else {
                request.action
                    (ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE, request);                
                localName = request.localName().toString();
            }
        }
        return localName;
    }

    /**
     * Returns the Internet Protocol (IP) address of the interface on
     * which the request  was received.
     */       
    public String getLocalAddr(){
        if (localAddr == null) {
            if (socket != null) {
                InetAddress inet = socket.getLocalAddress();
                localAddr = inet.getHostAddress();
            } else {
                request.action
                    (ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE, request);
                localAddr = request.localAddr().toString();
            }
        }
        return localAddr;    
    }


    /**
     * Returns the Internet Protocol (IP) port number of the interface
     * on which the request was received.
     */
    public int getLocalPort(){
        if (localPort == -1){
            if (socket != null) {
                localPort = socket.getLocalPort();
            } else {
                request.action
                    (ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE, request);
                localPort = request.getLocalPort();
            }
        }
        return localPort;
    }
    
 
    /**
     * Return the scheme used to make this Request.
     */
    public String getScheme() {
        String scheme = (request.scheme().toString());
        if (scheme == null){
            scheme = "http";
        }
        return scheme;
    }


    /**
     * Return the server name responding to this Request.
     */
    public String getServerName() {
        return (request.serverName().toString());
    }


    /**
     * Return the server port responding to this Request.
     */
    public int getServerPort() {
        return (request.getServerPort());
    }


    /**
     * Was this request received on a secure connection?
     */
    public boolean isSecure() {
        return (secure);
    }
    
    
    /**
     * Return <tt>true</tt> if the session identifier included in this
     * request identifies a valid session.
     */
    public boolean isRequestedSessionIdValid() {

        if (requestedSessionId == null)
            return (false);
  
        if (session != null
                && requestedSessionId.equals(session.getIdInternal())) {
            return session.isValid();
        }
        
        GrizzlySession localSession = sessions.get(requestedSessionId);
        if ((localSession != null) && localSession.isValid())
            return (true);
        else
            return (false);

    }
    
    /**
     * Return <tt>true</tt> if the session identifier included in this
     * request came from a cookie.
     */
    public boolean isRequestedSessionIdFromCookie() {

        if (requestedSessionId != null)
            return (requestedSessionCookie);
        else
            return (false);

    }


    /**
     * Return <tt>true</tt> if the session identifier included in this
     * request came from the request URI.
     */
    public boolean isRequestedSessionIdFromURL() {

        if (requestedSessionId != null)
            return (requestedSessionURL);
        else
            return (false);

    }

    /**
     * Remove the specified request attribute if it exists.
     *
     * @param name Name of the request attribute to remove
     */
    public void removeAttribute(String name) {
        Object value = null;
        boolean found = false;

        // Remove the specified attribute
        // Check for read only attribute
        // requests are per thread so synchronization unnecessary
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }
        found = attributes.containsKey(name);
        if (found) {
            value = attributes.get(name);
            attributes.remove(name);
        }
    }


    /**
     * Set the specified request attribute to the specified value.
     *
     * @param name Name of the request attribute to set
     * @param value The associated value
     */
    public void setAttribute(String name, Object value) {
	
        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("request.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            dispatcherType = value;
            return;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // Add or replace the specified attribute
        // Check for read only attribute
        // requests are per thread so synchronization unnecessary
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }

        oldValue = attributes.put(name, value);
        if (oldValue != null) {
            replaced = true;
        }
        
        // START SJSAS 6231069
        // Pass special attributes to the ngrizzly layer
        if (name.startsWith("grizzly.")) {
            request.setAttribute(name, value);
        }
        // END SJSAS 6231069
    }


    /**
     * Overrides the name of the character encoding used in the body of this
     * request.
     *
     * This method must be called prior to reading request parameters or
     * reading input using <code>getReader()</code>. Otherwise, it has no
     * effect.
     * 
     * @param enc      <code>String</code> containing the name of
     *                 the character encoding.
     * @throws         java.io.UnsupportedEncodingException if this
     *                 ServletRequest is still in a state where a
     *                 character encoding may be set, but the specified
     *                 encoding is invalid
     *
     * @since Servlet 2.3
     */
    public void setCharacterEncoding(String enc)
        throws UnsupportedEncodingException {

        // START SJSAS 4936855
        if (requestParametersParsed || usingReader) {
            return;
        }
        // END SJSAS 4936855

        // Ensure that the specified encoding is valid
        byte buffer[] = new byte[1];
        buffer[0] = (byte) 'a';

        // START S1AS 6179607: Workaround for 6181598. Workaround should be
        // removed once the underlying issue in J2SE has been fixed.
        /*
         * String dummy = new String(buffer, enc);
         */
        // END S1AS 6179607
        // START S1AS 6179607
        final byte[] finalBuffer = buffer;
        final String finalEnc = enc;
        String dummy = null;
        if (System.getSecurityManager() != null) {
            try {
                dummy = (String) AccessController.doPrivileged(
                    new PrivilegedExceptionAction() {
                        public Object run() throws UnsupportedEncodingException {
                            return new String(finalBuffer, finalEnc);
                        }
                    });
            } catch (PrivilegedActionException pae) {
                throw (UnsupportedEncodingException) pae.getCause();
            }
        } else {
            dummy = new String(buffer, enc);
        }
        
        assert dummy != null;
        // END S1AS 6179607

        // Save the validated encoding
        request.setCharacterEncoding(enc);

    }


    // START S1AS 4703023
    /**
     * Static setter method for the maximum dispatch depth
     */
    public static void setMaxDispatchDepth(int depth) {
        maxDispatchDepth = depth;
    }


    public static int getMaxDispatchDepth(){
        return maxDispatchDepth;
    }

    /**
     * Increment the depth of application dispatch
     */
    public int incrementDispatchDepth() {
        return ++dispatchDepth;
    }


    /**
     * Decrement the depth of application dispatch
     */
    public int decrementDispatchDepth() {
        return --dispatchDepth;
    }


    /**
     * Check if the application dispatching has reached the maximum
     */
    public boolean isMaxDispatchDepthReached() {
        return dispatchDepth > maxDispatchDepth;
    }
    // END S1AS 4703023


    // ---------------------------------------------------- HttpRequest Methods


    /**
     * Add a Cookie to the set of Cookies associated with this Request.
     *
     * @param cookie The new cookie
     */
    public void addCookie(Cookie cookie) {

        // For compatibility only
        if (!cookiesParsed)
            parseCookies();

        int size = 0;
        if (cookie != null) {
            size = cookies.length;
        }

        Cookie[] newCookies = new Cookie[size + 1];
        System.arraycopy(cookies, 0, newCookies, 0, size);
        newCookies[size] = cookie;

        cookies = newCookies;

    }


    /**
     * Add a Header to the set of Headers associated with this Request.
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void addHeader(String name, String value) {
        // Not used
    }


    /**
     * Add a Locale to the set of preferred Locales for this Request.  The
     * first added Locale will be the first one returned by getLocales().
     *
     * @param locale The new preferred Locale
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }


    /**
     * Add a parameter name and corresponding set of values to this Request.
     * (This is used when restoring the original request on a form based
     * login).
     *
     * @param name Name of this request parameter
     * @param values Corresponding values for this request parameter
     */
    public void addParameter(String name, String values[]) {
        request.getParameters().addParameterValues(name, values);
    }


    /**
     * Clear the collection of Cookies associated with this Request.
     */
    public void clearCookies() {
        cookiesParsed = true;
        cookies = null;
    }


    /**
     * Clear the collection of Headers associated with this Request.
     */
    public void clearHeaders() {
        // Not used
    }


    /**
     * Clear the collection of Locales associated with this Request.
     */
    public void clearLocales() {
        locales.clear();
    }


    /**
     * Clear the collection of parameters associated with this Request.
     */
    public void clearParameters() {
        // Not used
    }


    /**
     * Set the authentication type used for this request, if any; otherwise
     * set the type to <code>null</code>.  Typical values are "BASIC",
     * "DIGEST", or "SSL".
     *
     * @param type The authentication type used
     */
    public void setAuthType(String type) {
        this.authType = type;
    }


    /**
     * Set the HTTP request method used for this Request.
     *
     * @param method The request method
     */
    public void setMethod(String method) {
        // Not used
    }


    /**
     * Set the query string for this Request.  This will normally be called
     * by the HTTP Connector, when it parses the request headers.
     *
     * @param query The query string
     */
    public void setQueryString(String query) {
        // Not used
    }


    /**
     * Set the unparsed request URI for this Request.  This will normally be
     * called by the HTTP Connector, when it parses the request headers.
     *
     * @param uri The request URI
     */
    public void setRequestURI(String uri) {
        // Not used
    }


    /**
     * Set the decoded request URI.
     * 
     * @param uri The decoded request URI
     */
    public void setDecodedRequestURI(String uri) {
        // Not used
    }


    /**
     * Get the decoded request URI.
     * 
     * @return the URL decoded request URI
     */
    public String getDecodedRequestURI() {
        return (request.decodedURI().toString());
    }


    /**
     * Get the decoded request URI.
     * 
     * @return the URL decoded request URI
     */
    public MessageBytes getDecodedRequestURIMB() {
        return (request.decodedURI());
    }


    /**
     * Set the Principal who has been authenticated for this Request.  This
     * value is also used to calculate the value to be returned by the
     * <code>getRemoteUser()</code> method.
     *
     * @param principal The user Principal
     */
    public void setUserPrincipal(Principal principal) {
        this.userPrincipal = principal;
    }


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * Return the authentication type used for this Request.
     */
    public String getAuthType() {
        return (authType);
    }

    
    /**
     * Return the set of Cookies received with this Request.
     */
    public Cookie[] getCookies() {

        if (!cookiesParsed)
            parseCookies();

        return cookies;

    }


    /**
     * Set the set of cookies recieved with this Request.
     */
    public void setCookies(Cookie[] cookies) {

        this.cookies = cookies;

    }


    /**
     * Return the value of the specified date header, if any; otherwise
     * return -1.
     *
     * @param name Name of the requested date header
     *
     * @exception IllegalArgumentException if the specified header value
     *  cannot be converted to a date
     */
    public long getDateHeader(String name) {

        String value = getHeader(name);
        if (value == null)
            return (-1L);

        // Attempt to convert the date header in a variety of formats
        long result = FastHttpDateFormat.parseDate(value, formats);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);

    }


    /**
     * Return the first value of the specified header, if any; otherwise,
     * return <code>null</code>
     *
     * @param name Name of the requested header
     */
    public String getHeader(String name) {
        return request.getHeader(name);
    }


    /**
     * Return all of the values of the specified header, if any; otherwise,
     * return an empty enumeration.
     *
     * @param name Name of the requested header
     */
    public Enumeration getHeaders(String name) {
        return request.getMimeHeaders().values(name);
    }


    /**
     * Return the names of all headers received with this request.
     */
    public Enumeration getHeaderNames() {
        return request.getMimeHeaders().names();
    }


    /**
     * Return the value of the specified header as an integer, or -1 if there
     * is no such header for this request.
     *
     * @param name Name of the requested header
     *
     * @exception IllegalArgumentException if the specified header value
     *  cannot be converted to an integer
     */
    public int getIntHeader(String name) {

        String value = getHeader(name);
        if (value == null) {
            return (-1);
        } else {
            return (Integer.parseInt(value));
        }

    }


    /**
     * Return the HTTP request method used in this Request.
     */
    public String getMethod() {
        return request.method().toString();
    }

    
    /**
     * Return the query string associated with this request.
     */
    public String getQueryString() {
        String queryString = request.queryString().toString();

       if (queryString == null || queryString.equals("")) {
            return (null);
        } else {
            return queryString;
        }
    }


    /**
     * Return the name of the remote user that has been authenticated
     * for this Request.
     */
    public String getRemoteUser() {

        if (userPrincipal != null) {
            return (userPrincipal.getName());
        } else {
            if (!request.getRemoteUser().isNull()) {
                final String remoteUser = request.getRemoteUser().toString();
                userPrincipal = new GrizzlyPrincipal(remoteUser);
                return remoteUser;
            }
            
            return (null);
        }

    }

    
    /**
     * Return the session identifier included in this request, if any.
     */
    public String getRequestedSessionId() {
        return (requestedSessionId);
    }


    /**
     * Return the request URI for this request.
     */
    public String getRequestURI() {
        return request.requestURI().toString();
    }


    /**
     * Reconstructs the URL the client used to make the request.
     * The returned URL contains a protocol, server name, port
     * number, and server path, but it does not include query
     * string parameters.
     * <p>
     * Because this method returns a <code>StringBuffer</code>,
     * not a <code>String</code>, you can modify the URL easily,
     * for example, to append query parameters.
     * <p>
     * This method is useful for creating redirect messages and
     * for reporting errors.
     *
     * @return A <code>StringBuffer</code> object containing the
     *  reconstructed URL
     */
    public StringBuffer getRequestURL() {

        StringBuilder url = new StringBuilder();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return (new StringBuffer(url));

    }

    /**
     * Return the principal that has been authenticated for this Request.
     */
    public Principal getUserPrincipal() {

        if (userPrincipal == null) {
            if (getRequest().scheme().equals("https")) {
                X509Certificate certs[] = (X509Certificate[])
                        getAttribute(Globals.CERTIFICATES_ATTR);
                if ((certs == null) || (certs.length < 1)) {
                    certs = (X509Certificate[])
                            getAttribute(Globals.SSL_CERTIFICATE_ATTR);
                }
                if ((certs == null) || (certs.length < 1)) {
                    userPrincipal = null;
                } else {
                    userPrincipal = certs[0].getSubjectX500Principal();
                }
            }
        }
        return (userPrincipal);

    }

    protected String unescape(String s) {
        if (s == null) {
            return null;
        }
        if (s.indexOf('\\') == -1) {
            return s;
        }

        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                buf.append(c);
            } else {
                if (++i >= s.length()) {
                    //invalid escape, hence invalid cookie
                    throw new IllegalArgumentException();
                }
                c = s.charAt(i);
                buf.append(c);
            }
        }
        return buf.toString();
    }        

    /**
     * Parse cookies.
     */
    protected void parseCookies() {

        cookiesParsed = true;

        Cookies serverCookies = request.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0)
            return;

        cookies = new Cookie[count];

        int idx = 0;
        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            try {
                // we must unescape the '\\' escape character
                Cookie cookie = new Cookie(scookie.getName().toString(), null);
                int version = scookie.getVersion();
                cookie.setVersion(version);
                cookie.setValue(unescape(scookie.getValue().toString()));
                cookie.setPath(unescape(scookie.getPath().toString()));
                String domain = scookie.getDomain().toString();
                if (domain != null) {
                    cookie.setDomain(unescape(domain));//avoid NPE
                }
                String comment = scookie.getComment().toString();
                cookie.setComment((version == 1) ? unescape(comment) : null);
                cookies[idx++] = cookie;
            } catch(IllegalArgumentException e) {
                ; // Ignore bad cookie.
            }
        }
        if( idx < count ) {
            Cookie [] ncookies = new Cookie[idx];
            System.arraycopy(cookies, 0, ncookies, 0, idx);
            cookies = ncookies;
        }

    }


    /**
     * Parse request parameters.
     */
    protected void parseRequestParameters() {

        /* SJSAS 4936855
        requestParametersParsed = true;
        */

        Parameters parameters = request.getParameters();

        // getCharacterEncoding() may have been overridden to search for
        // hidden form field containing request encoding
        String enc = getCharacterEncoding();
        // START SJSAS 4936855
        // Delay updating requestParametersParsed to TRUE until
        // after getCharacterEncoding() has been called, because
        // getCharacterEncoding() may cause setCharacterEncoding() to be
        // called, and the latter will ignore the specified encoding if
        // requestParametersParsed is TRUE
        requestParametersParsed = true;
        // END SJSAS 4936855
        if (enc != null) {
            parameters.setEncoding(enc);
            parameters.setQueryStringEncoding(enc);
        } else {
            parameters.setEncoding
                (com.sun.grizzly.tcp.Constants.DEFAULT_CHARACTER_ENCODING);
            parameters.setQueryStringEncoding
                (com.sun.grizzly.tcp.Constants.DEFAULT_CHARACTER_ENCODING);
        }

        parameters.handleQueryParameters();

        // getInputStream() or getReader() may have been called on the request,
        // but if no read() methods have been invoked before getRequestParameters()
        // was invoked, it's safe to continue parameter processing.
        if (inputBuffer.state != inputBuffer.INITIAL_STATE) {
            return;
        }

        if (!getMethod().equalsIgnoreCase("POST"))
            return;

        String contentType = getContentType();
        if (contentType == null)
            contentType = "";
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon).trim();
        } else {
            contentType = contentType.trim();
        }
        if (!("application/x-www-form-urlencoded".equals(contentType)))
            return;

        int len = getContentLength();

    if (len > 0) {
            try {
                byte[] formData = getPostBody();
                if (formData != null) {
                    parameters.processParameters(formData, 0, len);
                }
            } catch (Throwable t) {
                ; // Ignore
            }
        }

    }


    // START SJSAS 6346738
    /**
     * Gets the POST body of this request.
     *
     * @return The POST body of this request
     */
    protected byte[] getPostBody() throws IOException {

        int len = getContentLength();
        byte[] formData = null;

        if (len < CACHED_POST_LEN) {
            if (postData == null)
                postData = new byte[CACHED_POST_LEN];
            formData = postData;
        } else {
            formData = new byte[len];
        }
        int actualLen = readPostBody(formData, len);
        if (actualLen == len) {
            return formData;
        }

        return null;
    }
    // END SJSAS 6346738


    /**
     * Read post body in an array.
     */
    protected int readPostBody(byte body[], int len)
        throws IOException {

        int offset = 0;
        do {
            int inputLen = getStream().read(body, offset, len - offset);
            if (inputLen <= 0) {
                return offset;
            }
            offset += inputLen;
        } while ((len - offset) > 0);
        return len;

    }


    /**
     * Parse request locales.
     */
    protected void parseLocales() {

        localesParsed = true;

        Enumeration values = getHeaders("accept-language");

        while (values.hasMoreElements()) {
            String value = values.nextElement().toString();
            parseLocalesHeader(value);
        }

    }


    /**
     * Parse accept-language header value.
     */
    protected void parseLocalesHeader(String value) {

        // Store the accumulated languages that have been requested in
        // a local collection, sorted by the quality value (so we can
        // add Locales in descending order).  The values will be ArrayLists
        // containing the corresponding Locales to be added
        TreeMap locales = new TreeMap();

        // Preprocess the value to remove all whitespace
        int white = value.indexOf(' ');
        if (white < 0)
            white = value.indexOf('\t');
        if (white >= 0) {
            StringBuilder sb = new StringBuilder();
            int len = value.length();
            for (int i = 0; i < len; i++) {
                char ch = value.charAt(i);
                if ((ch != ' ') && (ch != '\t'))
                    sb.append(ch);
            }
            value = sb.toString();
        }

        // Process each comma-delimited language specification
        parser.setString(value);        // ASSERT: parser is available to us
        int length = parser.getLength();
        while (true) {

            // Extract the next comma-delimited entry
            int start = parser.getIndex();
            if (start >= length)
                break;
            int end = parser.findChar(',');
            String entry = parser.extract(start, end).trim();
            parser.advance();   // For the following entry

            // Extract the quality factor for this entry
            double quality = 1.0;
            int semi = entry.indexOf(";q=");
            if (semi >= 0) {
                final String qvalue = entry.substring(semi + 3);
                // qvalues, according to the RFC, may not contain more
                // than three values after the decimal.
                if (qvalue.length() <= 5) {
                    try {
                        quality = Double.parseDouble(qvalue);
                    } catch (NumberFormatException e) {
                        quality = 0.0;
                    }
                } else {
                    quality = 0.0;
                }
                entry = entry.substring(0, semi);
            }

            // Skip entries we are not going to keep track of
            if (quality < 0.00005)
                continue;       // Zero (or effectively zero) quality factors
            if ("*".equals(entry))
                continue;       // FIXME - "*" entries are not handled

            // Extract the language and country for this entry
            String language = null;
            String country = null;
            String variant = null;
            int dash = entry.indexOf('-');
            if (dash < 0) {
                language = entry;
                country = "";
                variant = "";
            } else {
                language = entry.substring(0, dash);
                country = entry.substring(dash + 1);
                int vDash = country.indexOf('-');
                if (vDash > 0) {
                    String cTemp = country.substring(0, vDash);
                    variant = country.substring(vDash + 1);
                    country = cTemp;
                } else {
                    variant = "";
                }
            }

            // Add a new Locale to the list of Locales for this quality level
            Locale locale = new Locale(language, country, variant);
            Double key = new Double(-quality);  // Reverse the order
            ArrayList values = (ArrayList) locales.get(key);
            if (values == null) {
                values = new ArrayList();
                locales.put(key, values);
            }
            values.add(locale);

        }

        // Process the quality values in highest->lowest order (due to
        // negating the Double value when creating the key)
        for (Object o : locales.keySet()) {
            Double key = (Double) o;
            ArrayList list = (ArrayList) locales.get(key);
            for (Object aList : list) {
                Locale locale = (Locale) aList;
                addLocale(locale);
            }
        }

    }

    /**
     * Parses the value of the JROUTE cookie, if present.
     */
    void parseJrouteCookie() {

        Cookies serverCookies = request.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0) {
            return;
        }

        for (int i=0; i<count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            if (scookie.getName().equals(Constants.JROUTE_COOKIE)) {
                setJrouteId(scookie.getValue().toString());
                break;
            }
        }
    }

    /**
     * Sets the jroute id of this request.
     * 
     * @param jrouteId The jroute id
     */
    void setJrouteId(String jrouteId) {
        this.jrouteId = jrouteId;
    }

    /**
     * Gets the jroute id of this request, which may have been 
     * sent as a separate <code>JROUTE</code> cookie or appended to the
     * session identifier encoded in the URI (if cookies have been disabled).
     * 
     * @return The jroute id of this request, or null if this request does not
     * carry any jroute id
     */
    public String getJrouteId() {
        return jrouteId;
    }

    // ------------------------------------------------------ GrizzlySession support --/

    
    /**
     * Return the session associated with this Request, creating one
     * if necessary.
     */
    public GrizzlySession getSession() {
        return doGetSession(true);
    }


    /**
     * Return the session associated with this Request, creating one
     * if necessary and requested.
     *
     * @param create Create a new session if one does not exist
     */
    public GrizzlySession getSession(boolean create) {
        return  doGetSession(create);
    }
    
    
    protected GrizzlySession doGetSession(boolean create) {
        // Return the current session if it exists and is valid
        if ((session != null) && !session.isValid()) {
            session = null;
        }
        
        if (session != null)
            return (session);

        if (requestedSessionId != null) {
            session = sessions.get(requestedSessionId);
            if ((session != null) && !session.isValid()) {
                session = null;
                sessions.remove(requestedSessionId);
            }

            if (session != null) {
                return (session);
            }

            requestedSessionId = null;
        }

        // Create a new session if requested and the response is not committed
        if (!create)
            return (null);

        if (requestedSessionId != null) {
            session = new GrizzlySession(requestedSessionId);
            
        } else {
            requestedSessionId = String.valueOf(Math.abs(random.nextLong()));
            session = new GrizzlySession(requestedSessionId);
        }
        sessions.put(requestedSessionId,session);

        // Creating a new session cookie based on the newly created session
        if (session != null) {
            Cookie cookie = new Cookie(Globals.SESSION_COOKIE_NAME,
                                       session.getIdInternal());
            configureSessionCookie(cookie);
            response.addCookie(cookie);

        }

        if (session != null) {
            return (session);
        } else {
            return (null);
        }

    }

    /**
     * Configures the given JSESSIONID cookie.
     *
     * @param cookie The JSESSIONID cookie to be configured
     */
    protected void configureSessionCookie(Cookie cookie) {
        cookie.setMaxAge(-1);
        String contextPath = null;
        cookie.setPath("/");
 
        if (isSecure()) {
            cookie.setSecure(true);
        }
    }

    
    protected Cookie makeCookie(ServerCookie scookie) {
        return makeCookie(scookie, false);
    }

    protected Cookie makeCookie(ServerCookie scookie, boolean decode) {

        String name = scookie.getName().toString();
        String value = scookie.getValue().toString();

        if (decode) {
            try {
                name = URLDecoder.decode(name, "UTF-8");
                value = URLDecoder.decode(value, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                name = URLDecoder.decode(name);
                value = URLDecoder.decode(value);
            }
        }

        return new Cookie(name, value);
    }
    
    /**
     * Parse session id in URL.
     */
    protected void parseSessionId() {

        ByteChunk uriBC = request.requestURI().getByteChunk();
        int semicolon = uriBC.indexOf(match, 0, match.length(), 0);

        if (semicolon > 0) {

            // Parse session ID, and extract it from the decoded request URI
            int start = uriBC.getStart();
            int end = uriBC.getEnd();

            int sessionIdStart = start + semicolon + match.length();
            int semicolon2 = uriBC.indexOf(';', sessionIdStart);
            String sessionId = null;
            if (semicolon2 >= 0) {
                sessionId = new String(uriBC.getBuffer(), sessionIdStart,
                                       semicolon2 - semicolon - match.length());
            } else {
                sessionId = new String(uriBC.getBuffer(), sessionIdStart,
                                       end - sessionIdStart);
            }
            int jrouteIndex = sessionId.lastIndexOf(':');
            if (jrouteIndex > 0) {
                setRequestedSessionId(sessionId.substring(0, jrouteIndex));
                if (jrouteIndex < (sessionId.length()-1)) {
                    setJrouteId(sessionId.substring(jrouteIndex+1));
                }
            } else {
                setRequestedSessionId(sessionId);
            }

            setRequestedSessionURL(true);

            if (!request.requestURI().getByteChunk().isNull()) {
                parseSessionIdFromRequestURI();
            }
        } else {
            setRequestedSessionId(null);
            setRequestedSessionURL(false);
        }
    }
 
    /**
     * Extracts the session ID from the request URI.
     */
    protected void parseSessionIdFromRequestURI() {

        int start, end, sessionIdStart, semicolon, semicolon2;

        MessageBytes requestURI = request.requestURI();
        ByteChunk uriBC = requestURI.getByteChunk();
        start = uriBC.getStart();
        end = uriBC.getEnd();
        semicolon = uriBC.indexOf(match, 0, match.length(), 0);

        if (semicolon > 0) {
            sessionIdStart = start + semicolon;
            semicolon2 = uriBC.indexOf
                (';', semicolon + match.length());
            byte[] buf = uriBC.getBuffer();
            if (semicolon2 >= 0) {
                System.arraycopy(buf, start + semicolon2, buf, sessionIdStart, end - start - semicolon2);
                requestURI.setBytes(buf, start, semicolon
                               + (end - start - semicolon2));
            } else {
                requestURI.setBytes(buf, start, semicolon);
            }
        }
    }

    /**
     * Set a flag indicating whether or not the requested session ID for this
     * request came in through a cookie.  This is normally called by the
     * HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionCookie(boolean flag) {

        this.requestedSessionCookie = flag;

    }


    /**
     * Set the requested session ID for this request.  This is normally called
     * by the HTTP Connector, when it parses the request headers.
     *
     * @param id The new session id
     */
    public void setRequestedSessionId(String id) {

        this.requestedSessionId = id;

    }


    /**
     * Set a flag indicating whether or not the requested session ID for this
     * request came in through a URL.  This is normally called by the
     * HTTP Connector, when it parses the request headers.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionURL(boolean flag) {

        this.requestedSessionURL = flag;

    }
}
