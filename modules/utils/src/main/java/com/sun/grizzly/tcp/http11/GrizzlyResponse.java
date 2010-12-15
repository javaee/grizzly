/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.tcp.CompletionHandler;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.ResponseFilter;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UEncoder;
import com.sun.grizzly.util.http.Cookie;
import com.sun.grizzly.util.http.FastHttpDateFormat;
import com.sun.grizzly.util.http.HttpRequestURIDecoder;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.http.ServerCookie;
import com.sun.grizzly.util.res.StringManager;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;

/**
 * Wrapper object for the Coyote response.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 1.2 $ $Date: 2006/11/02 20:01:44 $
 */

public class GrizzlyResponse<A> {


    // ----------------------------------------------------------- Constructors

    public GrizzlyResponse() {
        this(false,false);
    }
 
    
    public GrizzlyResponse(boolean chunkingDisabled, boolean cacheEnabled) {    
        outputBuffer = new GrizzlyOutputBuffer(chunkingDisabled);
        outputStream = new GrizzlyOutputStream(outputBuffer);
        writer = new GrizzlyWriter(outputBuffer);
 
        urlEncoder.addSafeCharacter('/');
        
        this.cacheEnabled = cacheEnabled;
    }

    // ----------------------------------------------------- Instance Variables
    private boolean cacheEnabled = false;
    

    private String detailErrorMsg;

    private static final String HTTP_RESPONSE_DATE_HEADER =
        "EEE, dd MMM yyyy HH:mm:ss zzz";

    /**
     * The date format we will use for creating date headers.
     */
    protected SimpleDateFormat format = null;


    /**
     * Descriptive information about this Response implementation.
     */
    protected static final String info =
        "com.sun.grizzly.util.tcp.GrizzlyResponse/1.0";


    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());


    // ------------------------------------------------------------- Properties
    /**
     * The request with which this response is associated.
     */
    protected GrizzlyRequest request = null;

    
    /**
     * Return the Request with which this Response is associated.
     */
    public GrizzlyRequest getRequest() {
        return (this.request);
    }

    /**
     * Set the Request with which this Response is associated.
     *
     * @param request The new associated request
     */
    public void setRequest(GrizzlyRequest request) {
        this.request = request;
    }
    
    /**
     * Coyote response.
     */
    protected Response response;

    /**
     * Set the Coyote response.
     * 
     * @param response The Coyote response
     */
    public void setResponse(Response response) {
        this.response = response;
        outputBuffer.setResponse(response);
    }

    /**
     * Get the Coyote response.
     */
    public Response getResponse() {
        return (response);
    }


    /**
     * The associated output buffer.
     */
    // START OF SJSAS 6231069    
    //protected OutputBuffer outputBuffer = new OutputBuffer();
    protected final GrizzlyOutputBuffer outputBuffer;
    // END OF SJSAS 6231069    

    /**
     * The associated output stream.
     */    
    // START OF SJSAS 6231069 
    /*protected GrizzlyOutputStream outputStream =
        new GrizzlyOutputStream(outputBuffer);*/
    protected GrizzlyOutputStream outputStream;
    // END OF SJSAS 6231069    

    /**
     * The associated writer.
     */
    // START OF SJSAS 6231069 
    // protected GrizzlyWriter writer = new GrizzlyWriter(outputBuffer);
    protected GrizzlyWriter writer;
    // END OF SJSAS 6231069    

    
    /**
     * The application commit flag.
     */
    protected boolean appCommitted = false;


    /**
     * The included flag.
     */
    protected boolean included = false;

    
    /**
     * The characterEncoding flag
     */
    private boolean isCharacterEncodingSet = false;
    
    /**
     * The contextType flag
     */    
    private boolean isContentTypeSet = false;

    
    /**
     * The error flag.
     */
    protected boolean error = false;


    /**
     * The set of Cookies associated with this Response.
     */
    protected ArrayList cookies = new ArrayList(4);


    /**
     * Using output stream flag.
     */
    protected boolean usingOutputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingWriter = false;


    /**
     * URL encoder.
     */
    protected UEncoder urlEncoder = new UEncoder();


    /**
     * Recyclable buffer to hold the redirect URL.
     */
    protected MessageBytes redirectURLCC = new MessageBytes();


    // --------------------------------------------------------- Public Methods


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    public void recycle() {

        outputBuffer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        included = false;
        error = false;
        isContentTypeSet = false;
        isCharacterEncodingSet = false;
        detailErrorMsg = null;

        cookies.clear();

        if (System.getSecurityManager() != null) {
            if (outputStream != null) {
                outputStream.clear();
                outputStream = null;
            }
            if (writer != null) {
                writer.clear();
                writer = null;
            }
        } else {
            writer.recycle();
        }
        cacheEnabled = false;

    }


    // ------------------------------------------------------- Response Methods

    /**
     * Encode the session identifier associated with this response
     * into the specified URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeURL(String url) {
        
        String absolute = toAbsolute(url, false);
        if (isEncodeable(absolute)) {
            // W3c spec clearly said 
            if (url.equalsIgnoreCase("")){
                url = absolute;
            }
            return toEncoded(url,request.getSession().getIdInternal());
        } else {
            return (url);
        }

    }
 

    /**
     * Return <tt>true</tt> if the specified URL should be encoded with
     * a session identifier.  This will be true if all of the following
     * conditions are met:
     * <ul>
     * <li>The request we are responding to asked for a valid session
     * <li>The requested session ID was not received via a cookie
     * <li>The specified URL points back to somewhere within the web
     *     application that is responding to this request
     * </ul>
     *
     * @param location Absolute URL to be validated
     */
    protected boolean isEncodeable(final String location) {

        if (location == null)
            return (false);

        // Is this an intra-document reference?
        if (location.startsWith("#"))
            return (false);

        final GrizzlySession session = request.getSession(false);
        if (session == null)
            return (false);
        
        if (request.isRequestedSessionIdFromCookie())
            return (false);
        
  
        return doIsEncodeable(request, session, location);
        
    }

    private boolean doIsEncodeable(GrizzlyRequest request, GrizzlySession session,
                                   String location){
        // Is this a valid absolute URL?
        URL url = null;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            return (false);
        }

        // Does this URL match down to (and including) the context path?
        if (!request.getScheme().equalsIgnoreCase(url.getProtocol()))
            return (false);
        if (!request.getServerName().equalsIgnoreCase(url.getHost()))
            return (false);
        int serverPort = request.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(request.getScheme()))
                serverPort = 443;
            else
                serverPort = 80;
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol()))
                urlPort = 443;
            else
                urlPort = 80;
        }
        if (serverPort != urlPort)
            return (false);

        String contextPath = "/";
        if ( contextPath != null ) {
            String file = url.getFile();
            if ((file == null) || !file.startsWith(contextPath))
                return (false);
            if( file.indexOf(";jsessionid=" + session.getIdInternal()) >= 0 )
                return (false);
        }

        // This URL belongs to our web application, so it is encodeable
        return (true);

    }
    
    
    /**
     * Return the number of bytes actually written to the output stream.
     */
    public int getContentCount() {
        return outputBuffer.getContentWritten();
    }


    /**
     * Set the application commit flag.
     * 
     * @param appCommitted The new application committed flag value
     */
    public void setAppCommitted(boolean appCommitted) {
        this.appCommitted = appCommitted;
    }


    /**
     * Application commit flag accessor.
     */
    public boolean isAppCommitted() {
        return (this.appCommitted || isCommitted() || isBufferSuspended()
                || ((getContentLength() > 0) 
                    && (getContentCount() >= getContentLength())));
    }


    /**
     * Return the "processing inside an include" flag.
     */
    public boolean getIncluded() {
        return included;
    }


    /**
     * Set the "processing inside an include" flag.
     *
     * @param included <tt>true</tt> if we are currently inside a
     *  RequestDispatcher.include(), else <tt>false</tt>
     */
    public void setIncluded(boolean included) {
        this.included = included;
    }


    /**
     * Return descriptive information about this Response implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    /**
     * Return the output stream associated with this Response.
     */
    public OutputStream getStream() {
        if (outputStream == null) {
            outputStream = new GrizzlyOutputStream(outputBuffer);
        }
        return outputStream;
    }


    /**
     * Set the output stream associated with this Response.
     *
     * @param stream The new output stream
     */
    public void setStream(OutputStream stream) {
        // This method is evil
    }


    /**
     * Set the suspended flag.
     * 
     * @param suspended The new suspended flag value
     */
    public void setSuspended(boolean suspended) {
        outputBuffer.setSuspended(suspended);
    }


    /**
     * Suspended flag accessor.
     */
    public boolean isBufferSuspended() {
        return outputBuffer.isSuspended();
    }


    /**
     * Set the error flag.
     */
    public void setError() {
        error = true;
    }


    /**
     * Error flag accessor.
     */
    public boolean isError() {
        return error;
    }


    // BEGIN S1AS 4878272
    /**
     * Sets detail error message.
     *
     * @param message detail error message
     */
    public void setDetailMessage(String message) {
        this.detailErrorMsg = message;
    }


    /**
     * Gets detail error message.
     *
     * @return the detail error message
     */
    public String getDetailMessage() {
        return this.detailErrorMsg;
    }
    // END S1AS 4878272


    /**
     * Create and return a ServletOutputStream to write the content
     * associated with this Response.
     *
     * @exception IOException if an input/output error occurs
     */
    public GrizzlyOutputStream createOutputStream() 
        throws IOException {
        // Probably useless
        if (outputStream == null) {
            outputStream = new GrizzlyOutputStream(outputBuffer);
        }
        return outputStream;
    }


    /**
     * Perform whatever actions are required to flush and close the output
     * stream or writer, in a single operation.
     *
     * @exception IOException if an input/output error occurs
     */
    public void finishResponse() 
        throws IOException {
        // Writing leftover bytes
        try {
            outputBuffer.close();
        } catch(IOException e) {
        } catch(Throwable t) {
        }
    }


    /**
     * Return the content length that was set or calculated for this Response.
     */
    public int getContentLength() {
        checkResponse();
        return (response.getContentLength());
    }


    /**
     * Return the content type that was set or calculated for this response,
     * or <code>null</code> if no content type was set.
     */
    public String getContentType() {
        checkResponse();        
        return (response.getContentType());
    }


    /**
     * Return a PrintWriter that can be used to render error messages,
     * regardless of whether a stream or writer has already been acquired.
     *
     * @return Writer which can be used for error reports. If the response is
     * not an error report returned using sendError or triggered by an
     * unexpected exception thrown during the servlet processing
     * (and only in that case), null will be returned if the response stream
     * has already been used.
     *
     * @exception IOException if an input/output error occurs
     */
    public PrintWriter getReporter() throws IOException {
        if (outputBuffer.isNew()) {
            outputBuffer.checkConverter();
            if (writer == null) {
                writer = new GrizzlyWriter(outputBuffer);
            }
            return writer;
        } else {
            return null;
        }
    }


    // ------------------------------------------------ ServletResponse Methods


    /**
     * Flush the buffer and commit this response.
     *
     * @exception IOException if an input/output error occurs
     */
    public void flushBuffer() 
        throws IOException {
        outputBuffer.flush();
    }


    /**
     * Return the actual buffer size used for this Response.
     */
    public int getBufferSize() {
        return outputBuffer.getBufferSize();
    }


    /**
     * Return the character encoding used for this Response.
     */
    public String getCharacterEncoding() {
        checkResponse();
        return (response.getCharacterEncoding());
    }


    /**
     * Return the servlet output stream associated with this Response.
     *
     * @exception IllegalStateException if <code>getWriter</code> has
     *  already been called for this response
     * @exception IOException if an input/output error occurs
     */
    public GrizzlyOutputStream getOutputStream() 
        throws IOException {

        if (usingWriter)
            throw new IllegalStateException
                (sm.getString("response.getOutputStream.ise"));

        usingOutputStream = true;
        if (outputStream == null) {
            outputStream = new GrizzlyOutputStream(outputBuffer);
        }
        return outputStream;

    }


    /**
     * Return the Locale assigned to this response.
     */
    public Locale getLocale() {
        checkResponse();
        return (response.getLocale());
    }


    /**
     * Return the writer associated with this Response.
     *
     * @exception IllegalStateException if <code>getOutputStream</code> has
     *  already been called for this response
     * @exception IOException if an input/output error occurs
     */
    public PrintWriter getWriter() 
        throws IOException {

        if (usingOutputStream)
            throw new IllegalStateException
                (sm.getString("response.getWriter.ise"));

        /*
         * If the response's character encoding has not been specified as
         * described in <code>getCharacterEncoding</code> (i.e., the method
         * just returns the default value <code>ISO-8859-1</code>),
         * <code>getWriter</code> updates it to <code>ISO-8859-1</code>
         * (with the effect that a subsequent call to getContentType() will
         * include a charset=ISO-8859-1 component which will also be
         * reflected in the Content-Type response header, thereby satisfying
         * the Servlet spec requirement that containers must communicate the
         * character encoding used for the servlet response's writer to the
         * client).
         */
        setCharacterEncoding(getCharacterEncoding());

        usingWriter = true;
        outputBuffer.checkConverter();
        if (writer == null) {
            writer = new GrizzlyWriter(outputBuffer);
        }
        return writer;

    }

    
    /**
     * Has the output of this response already been committed?
     */
    public boolean isCommitted() {
        checkResponse();
        return (response.isCommitted());
    }


    /**
     * Clear any content written to the buffer.
     *
     * @exception IllegalStateException if this response has already
     *  been committed
     */
    public void reset() {
        checkResponse();
        if (included)
            return;     // Ignore any call from an included servlet

        response.reset();
        outputBuffer.reset();
    }


    /**
     * Reset the data buffer but not any status or header information.
     *
     * @exception IllegalStateException if the response has already
     *  been committed
     */
    public void resetBuffer() {
        resetBuffer(false);
    }
 
    
    /**
     * Reset the data buffer and the using Writer/Stream flags but not any
     * status or header information.
     *
     * @param resetWriterStreamFlags <code>true</code> if the internal
     *        <code>usingWriter</code>, <code>usingOutputStream</code>,
     *        <code>isCharacterEncodingSet</code> flags should also be reset
     * 
     * @exception IllegalStateException if the response has already
     *  been committed
     */
    public void resetBuffer(boolean resetWriterStreamFlags) {

        if (isCommitted())
            throw new IllegalStateException
                (sm.getString("response.resetBuffer.ise"));

        outputBuffer.reset();
                
        if(resetWriterStreamFlags) {
            usingOutputStream = false;
            usingWriter = false;
            isCharacterEncodingSet = false;
        }

    }


    /**
     * Set the buffer size to be used for this Response.
     *
     * @param size The new buffer size
     *
     * @exception IllegalStateException if this method is called after
     *  output has been committed for this response
     */
    public void setBufferSize(int size) {

        if (isCommitted() || !outputBuffer.isNew())
            throw new IllegalStateException
                (sm.getString("response.setBufferSize.ise"));

        outputBuffer.setBufferSize(size);

    }
    
    
    /**
     * Set the content length (in bytes) for this Response.
     *
     * @param length The new content length
     */
    public void setContentLengthLong(long length) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;
        
        if (usingWriter)
            return;
        
        response.setContentLengthLong(length);

    }

    
    /**
     * Set the content length (in bytes) for this Response.
     *
     * @param length The new content length
     */
    public void setContentLength(int length) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;
        
        if (usingWriter)
            return;
        
        response.setContentLength(length);

    }


    /**
     * Set the content type for this Response.
     *
     * @param type The new content type
     */
    public void setContentType(String type) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        // Ignore charset if getWriter() has already been called
        if (usingWriter) {
            if (type != null) {
                int index = type.indexOf(";");
                if (index != -1) {
                    type = type.substring(0, index);
                }
            }
        }

        response.setContentType(type);

        // Check to see if content type contains charset
        if (type != null) {
            int index = type.indexOf(";");
            if (index != -1) {
                int len = type.length();
                index++;
                while (index < len && Character.isSpace(type.charAt(index))) {
                    index++;
                }
                if (index+7 < len
                        && type.charAt(index) == 'c'
                        && type.charAt(index+1) == 'h'
                        && type.charAt(index+2) == 'a'
                        && type.charAt(index+3) == 'r'
                        && type.charAt(index+4) == 's'
                        && type.charAt(index+5) == 'e'
                        && type.charAt(index+6) == 't'
                        && type.charAt(index+7) == '=') {
                    isCharacterEncodingSet = true;
                }
            }
        }

        isContentTypeSet = true;    
    }


    /*
     * Overrides the name of the character encoding used in the body
     * of the request. This method must be called prior to reading
     * request parameters or reading input using getReader().
     *
     * @param charset String containing the name of the chararacter encoding.
     */
    public void setCharacterEncoding(String charset) {
        checkResponse();
        if (isCommitted())
            return;
        
        // Ignore any call from an included servlet
        if (included)
            return;     
        
        // Ignore any call made after the getWriter has been invoked
        // The default should be used
        if (usingWriter)
            return;

        response.setCharacterEncoding(charset);
        isCharacterEncodingSet = true;
    }

    
    
    /**
     * Set the Locale that is appropriate for this response, including
     * setting the appropriate character encoding.
     *
     * @param locale The new locale
     */
    public void setLocale(Locale locale) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        response.setLocale(locale);

        // Ignore any call made after the getWriter has been invoked.
        // The default should be used
        if (usingWriter)
            return;

        if (isCharacterEncodingSet) {
            return;
        }

    }


    // --------------------------------------------------- HttpResponse Methods


    /**
     * Return an array of all cookies set for this response, or
     * a zero-length array if no cookies have been set.
     */
    public Cookie[] getCookies() {
        return ((Cookie[]) cookies.toArray(new Cookie[cookies.size()]));
    }


    /**
     * Return the value for the specified header, or <code>null</code> if this
     * header has not been set.  If more than one value was added for this
     * name, only the first is returned; use getHeaderValues() to retrieve all
     * of them.
     *
     * @param name Header name to look up
     */
    public String getHeader(String name) {
        checkResponse();
        return response.getMimeHeaders().getHeader(name);
    }


    /**
     * Return an array of all the header names set for this response, or
     * a zero-length array if no headers have been set.
     */
    public String[] getHeaderNames() {
        checkResponse();
        MimeHeaders headers = response.getMimeHeaders();
        int n = headers.size();
        String[] result = new String[n];
        for (int i = 0; i < n; i++) {
            result[i] = headers.getName(i).toString();
        }
        return result;

    }


    /**
     * Return an array of all the header values associated with the
     * specified header name, or an zero-length array if there are no such
     * header values.
     *
     * @param name Header name to look up
     */
    public String[] getHeaderValues(String name) {
        checkResponse();
        final Enumeration<String> e = response.getMimeHeaders().values(name);
        final Collection<String> result = new LinkedList<String>();
        while (e.hasMoreElements()) {
            result.add(e.nextElement());
        }
        
        return result.toArray(new String[result.size()]);

    }


    /**
     * Return the error message that was set with <code>sendError()</code>
     * for this Response.
     */
    public String getMessage() {
        checkResponse();
        return response.getMessage();
    }


    /**
     * Return the HTTP status code associated with this Response.
     */
    public int getStatus() {
        checkResponse();
        return response.getStatus();
    }


    /**
     * Reset this response, and specify the values for the HTTP status code
     * and corresponding message.
     *
     * @exception IllegalStateException if this response has already been
     *  committed
     */
    public void reset(int status, String message) {
        reset();
        setStatus(status, message);
    }


    // -------------------------------------------- HttpServletResponse Methods


    /**
     * Add the specified Cookie to those that will be included with
     * this Response.
     *
     * @param cookie Cookie to be added
     */
    public void addCookie(final Cookie cookie) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        final StringBuffer sb = new StringBuffer();
        //web application code can receive a IllegalArgumentException 
        //from the appendCookieValue invokation
        if (System.getSecurityManager() != null) {
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run(){
                    ServerCookie.appendCookieValue
                        (sb, cookie.getVersion(), cookie.getName(), 
                         cookie.getValue(), cookie.getPath(), 
                         cookie.getDomain(), cookie.getComment(), 
                         cookie.getMaxAge(), cookie.getSecure());
                    return null;
                }
            });
        } else {
            ServerCookie.appendCookieValue
                (sb, cookie.getVersion(), cookie.getName(), cookie.getValue(),
                     cookie.getPath(), cookie.getDomain(), cookie.getComment(), 
                     cookie.getMaxAge(), cookie.getSecure());
        }

        // if we reached here, no exception, cookie is valid
        // the header name is Set-Cookie for both "old" and v.1 ( RFC2109 )
        // RFC2965 is not supported by browsers and the Servlet spec
        // asks for 2109.
        addHeader("Set-Cookie", sb.toString());

        cookies.add(cookie);
    }


    /**
     * Add the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    public void addDateHeader(String name, long value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        addHeader(name, FastHttpDateFormat.formatDate(value, format));

    }


    /**
     * Add the specified header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Value to be set
     */
    public void addHeader(String name, String value) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        response.addHeader(name, value);

    }


    /**
     * Add the specified integer header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Integer value to be set
     */
    public void addIntHeader(String name, int value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        addHeader(name, "" + value);

    }


    /**
     * Has the specified header been set already in this response?
     *
     * @param name Name of the header to check
     */
    public boolean containsHeader(String name) {
        checkResponse();
        return response.containsHeader(name);
    }


    /**
     * Send an acknowledgment of a request.
     * 
     * @exception IOException if an input/output error occurs
     */
    public void sendAcknowledgement() throws IOException {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return; 

        response.acknowledge();

    }


    /**
     * Send an error response with the specified status and a
     * default message.
     *
     * @param status HTTP status code to send
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception IOException if an input/output error occurs
     */
    public void sendError(int status) 
        throws IOException {
        sendError(status, null);
    }


    /**
     * Send an error response with the specified status and message.
     *
     * @param status HTTP status code to send
     * @param message Corresponding message to send
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception IOException if an input/output error occurs
     */
    public void sendError(int status, String message) throws IOException {
        checkResponse();
        if (isCommitted())
            throw new IllegalStateException
                (sm.getString("response.sendError.ise"));

        // Ignore any call from an included servlet
        if (included)
            return; 

        setError();

        response.setStatus(status);
        response.setMessage(message);

        // Clear any data content that has been buffered
        resetBuffer();

        // Cause the response to be finished (from the application perspective)
        setSuspended(true);

    }


    /**
     * Send a temporary redirect to the specified redirect location URL.
     *
     * @param location Location URL to redirect to
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception IOException if an input/output error occurs
     */
    public void sendRedirect(String location) 
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (sm.getString("response.sendRedirect.ise"));

        // Ignore any call from an included servlet
        if (included)
            return; 

        // Clear any data content that has been buffered
        resetBuffer();

        // Generate a temporary redirect to the specified location
        try {
            String absolute = toAbsolute(location, true);
            // END RIMOD 4642650   
            setStatus(302);
            setHeader("Location", absolute);

            // According to RFC2616 section 10.3.3 302 Found,
            // the response SHOULD contain a short hypertext note with
            // a hyperlink to the new URI.
            setContentType("text/html");
            setLocale(Locale.getDefault());

            String filteredMsg = filter(absolute);
            StringBuilder sb = new StringBuilder(150 + absolute.length());

            sb.append("<html>\r\n");
            sb.append("<head><title>Document moved</title></head>\r\n");
            sb.append("<body><h1>Document moved</h1>\r\n");
            sb.append("This document has moved <a href=\"");
            sb.append(filteredMsg);
            sb.append("\">here</a>.<p>\r\n");
            sb.append("</body>\r\n");
            sb.append("</html>\r\n");

            try {
                getWriter().write(sb.toString());
                getWriter().flush();
            } catch (IllegalStateException ise1) {
                try {
                   getOutputStream().print(sb.toString());
                } catch (IllegalStateException ise2) {
                   // ignore; the RFC says "SHOULD" so it is acceptable
                   // to omit the body in case of an error
                }
            }
        } catch (IllegalArgumentException e) {
            setStatus(404);
        }

        // Cause the response to be finished (from the application perspective)
        setSuspended(true);

    }


    /**
     * Set the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    public void setDateHeader(String name, long value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        setHeader(name, FastHttpDateFormat.formatDate(value, format));

    }


    /**
     * Set the specified header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Value to be set
     */
    public void setHeader(String name, String value) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        response.setHeader(name, value);

    }


    /**
     * Set the specified integer header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Integer value to be set
     */
    public void setIntHeader(String name, int value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        setHeader(name, "" + value);

    }


    /**
     * Set the HTTP status to be returned with this response.
     *
     * @param status The new HTTP status
     */
    public void setStatus(int status) {
        setStatus(status, null);
    }


    /**
     * Set the HTTP status and message to be returned with this response.
     *
     * @param status The new HTTP status
     * @param message The associated text message
     *
     */
    public void setStatus(int status, String message) {
        checkResponse();
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        response.setStatus(status);
        response.setMessage(message);

    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Convert (if necessary) and return the absolute URL that represents the
     * resource referenced by this possibly relative URL.  If this URL is
     * already absolute, return it unchanged.
     *
     * @param location URL to be (possibly) converted and then returned
     *
     * @exception IllegalArgumentException if a MalformedURLException is
     *  thrown when converting the relative URL to an absolute one
     */
    protected String toAbsolute(String location, boolean normalize) {

        if (location == null)
            return (location);

        boolean leadingSlash = location.startsWith("/");

        if (leadingSlash 
            || (!leadingSlash && (location.indexOf("://") == -1))) {

            redirectURLCC.recycle();

            String scheme = request.getScheme();

            String name = request.getServerName();
            int port = request.getServerPort();
            CharChunk cc = redirectURLCC.getCharChunk();
            try {
                cc.append(scheme, 0, scheme.length());
                cc.append("://", 0, 3);
                cc.append(name, 0, name.length());
                if ((scheme.equals("http") && port != 80)
                    || (scheme.equals("https") && port != 443)) {
                    cc.append(':');
                    String portS = port + "";
                    cc.append(portS, 0, portS.length());
                }
                if (!leadingSlash) {
                    String relativePath = request.getDecodedRequestURI();
                    int pos = relativePath.lastIndexOf('/');
                    if (pos != -1){
                        relativePath = relativePath.substring(0, pos);
                    } else {
                        relativePath = "";
                    }
                    
                    String encodedURI = null;
                    final String frelativePath = relativePath;
                    
                     if (System.getSecurityManager() != null ){
                        try{
                            encodedURI = (String)AccessController.doPrivileged( 
                                new PrivilegedExceptionAction(){                                
                                    public Object run() throws IOException{
                                        return urlEncoder.encodeURL(frelativePath);
                                    }
                           });   
                        } catch (PrivilegedActionException pae){
                            IllegalArgumentException iae =
                                new IllegalArgumentException(location);
                            iae.initCause(pae.getCause());
                            throw iae;
                        }
                    } else {
                        encodedURI = urlEncoder.encodeURL(relativePath);
                    }
                          
                    cc.append(encodedURI, 0, encodedURI.length());
                    cc.append('/');
                }
                cc.append(location, 0, location.length());
            } catch (IOException e) {
                IllegalArgumentException iae =
                    new IllegalArgumentException(location);
                iae.initCause(e);
                throw iae;
            }

            if (normalize){
                HttpRequestURIDecoder.normalize(redirectURLCC);
            }
            
            return cc.toString();

        } else {

            return (location);

        }

    }
    
    
    /**
     * Filter the specified message string for characters that are sensitive
     * in HTML.  This avoids potential attacks caused by including JavaScript
     * codes in the request URL that is often reported in error messages.
     *
     * @param message The message string to be filtered
     */
    public static String filter(String message) {

        if (message == null)
            return (null);

        char content[] = new char[message.length()];
        message.getChars(0, message.length(), content, 0);
        StringBuffer result = new StringBuffer(content.length + 50);
        for (int i = 0; i < content.length; i++) {
            switch (content[i]) {
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            case '&':
                result.append("&amp;");
                break;
            case '"':
                result.append("&quot;");
                break;
            default:
                result.append(content[i]);
            }
        }
        return (result.toString());

    }

    /**
     * Return the specified URL with the specified session identifier
     * suitably encoded.
     *
     * @param url URL to be encoded with the session id
     * @param sessionId Session id to be included in the encoded URL
     */
    protected String toEncoded(String url, String sessionId) {

        if ((url == null) || (sessionId == null))
            return (url);

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuilder sb = new StringBuilder(path);
        if( sb.length() > 0 ) { // jsessionid can't be first.
            sb.append(";jsessionid=");
            sb.append(sessionId);
        }

        String jrouteId = request.getHeader(Constants.PROXY_JROUTE);
        if (jrouteId != null) {
            sb.append(":");
            sb.append(jrouteId);
        }
        
        sb.append(anchor);
        sb.append(query);
        return (sb.toString());

    }

    /**
     * Is the file cache enabled?
     */
    public boolean isCacheEnabled(){
        return cacheEnabled;
    }
    
    
    /**
     * Enable/disable the cache
     */
    public void enableCache(boolean cacheEnabled){
        this.cacheEnabled = cacheEnabled;  
        outputBuffer.enableCache(cacheEnabled);
    } 
    
    
    /**
     * Return the underlying {@link OutputBuffer}
     */
    public GrizzlyOutputBuffer getOutputBuffer(){
        return outputBuffer;
    }
    
    
    /**
     * Enabled or Disable response chunking.
     * @param chunkingDisabled
     */
    public void setChunkingDisabled(boolean chunkingDisabled){
        if (outputBuffer != null){
            outputBuffer.setChunkingDisabled(chunkingDisabled);
        }
    }
    
    
    /**
     * Is chunking enabled?
     */
    public boolean getChunkingDisabled(){
        if (outputBuffer != null){
            outputBuffer.getChunkingDisabled();
        }
        return true; // The default.
    }
    
    
    /**
     * Complete the {@link GrizzlyResponse} and finish/commit it. If a 
     * {@link CompletionHandler} has been defined, its {@link CompletionHandler#resumed(A)}
     * will first be invoked, then the {@link GrizzlyResponse#finishResponse()}. 
     * Those operations commit the response.
     */
    public void resume(){
        checkResponse();
        response.resume();
    }
    
    /**
     * Cancel the {@link GrizzlyResponse} and finish/commit it. If a 
     * {@link CompletionHandler} has been defined, its {@link CompletionHandler#cancelled(A)}
     * will first be invoked, then the {@link GrizzlyResponse#finishResponse()}. 
     * Those operations commit the response.
     */   
    public void cancel(){
        checkResponse();
        response.cancel();
    }
        
    /**
     * Return <tt>true<//tt> if that {@link GrizzlyResponse#suspend()} has been 
     * invoked and set to <tt>true</tt>
     * @return <tt>true<//tt> if that {@link GrizzlyResponse#suspend()} has been 
     * invoked and set to <tt>true</tt>
     */
    public boolean isSuspended(){
        checkResponse();
        return response.isSuspended();
    }
       
    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response.
     */
    public void suspend(){
        suspend(Long.MAX_VALUE);
    }

    /**
     * Suspend the {@link GrizzlyResponse}. Suspending a {@link GrizzlyResponse} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response.
     * 
     * @param timeout The maximum amount of time, in milliseconds, 
     * a {@link GrizzlyResponse} can be suspended. When the timeout expires (because 
     * nothing has been written or because the {@link Response#resume()} 
     * or {@link GrizzlyResponse#cancel()}), the {@link GrizzlyResponse} will be automatically
     * resumed and commited. Usage of any methods of a {@link GrizzlyResponse} that
     * times out will throw an {@link IllegalStateException}.
     *        
     */   
    public void suspend(long timeout){
        suspend(timeout,null,null);
    }
    
    /**
     * Suspend the {@link GrizzlyResponse}. Suspending a {@link GrizzlyResponse} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response. When the 
     * {@link GrizzlyResponse#resume()} is invoked, the container will 
     * make sure {@link CompletionHandler#resumed}
     * is invoked with the original <tt>attachement</tt>. When the 
     * {@link GrizzlyResponse#cancel()} is invoked, the container will 
     * make sure {@link CompletionHandler#cancelled}
     * is invoked with the original <tt>attachement</tt>. If the timeout expires, the 
     * {@link CompletionHandler#cancelled} is invoked with the original <tt>attachement</tt> and 
     * the {@link Response} commited.
     * 
     * @param timeout The maximum amount of time, in milliseconds, 
     * a {@link GrizzlyResponse} can be suspended. When the timeout expires (because 
     * nothing has been written or because the {@link GrizzlyResponse#resume()} 
     * or {@link GrizzlyResponse#cancel()}), the {@link GrizzlyResponse} will be automatically
     * resumed and commited. Usage of any methods of a {@link Response} that
     * times out will throw an {@link IllegalStateException}.
     * @param attachment Any Object that will be passed back to the {@link CompletionHandler}        
     * @param competionHandler a {@link CompletionHandler}
     */     
    public void suspend(long timeout,A attachment, CompletionHandler<? super A> competionHandler){
        checkResponse();
        response.suspend(timeout, attachment, competionHandler, 
                new GrizzlyResponseAttachment(timeout, attachment, competionHandler, this));
    }
    
    
    /**
     * Make sure the {@link Response} object has been set. 
     */
    void checkResponse(){
        if (response == null){
            throw new IllegalStateException("Internal " +
                    "com.sun.grizzly.tcp.Response has not been set");
        }
    }
    
    private static class GrizzlyResponseAttachment<A> extends Response.ResponseAttachment{
        private final GrizzlyResponse grizzlyResponse;
        
        public GrizzlyResponseAttachment(long timeout,A attachment,
                CompletionHandler<? super A> completionHandler,
                GrizzlyResponse grizzlyResponse) {
            super(timeout, attachment, completionHandler, grizzlyResponse.getResponse());
            this.grizzlyResponse = grizzlyResponse;
        }
        
        @Override
        public void resume(){
            getCompletionHandler().resumed(getAttachment());
            try{
                grizzlyResponse.finishResponse();
            } catch (IOException ex){
                LoggerUtils.getLogger().log(Level.FINEST,"resume",ex);
            }
        }
        
        @Override
        public boolean timeout(){
            // If the buffers are empty, commit the response header
            try{                             
                cancel();
            } finally {
                if (!grizzlyResponse.isCommitted()){
                    try{
                        grizzlyResponse.finishResponse();
                    } catch (IOException ex){
                        // Swallow?
                    }
                }
            }

            return true;
        }


        
    }

    /**
     * Add a {@link ResponseFilter}, which will be called every bytes are
     * ready to be written.
     */
    public void addResponseFilter(final ResponseFilter responseFilter){
        response.addResponseFilter(responseFilter);
    }
}

