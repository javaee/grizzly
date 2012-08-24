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

package org.glassfish.grizzly.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Connection.CloseListener;
import org.glassfish.grizzly.Connection.CloseType;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.server.io.NIOOutputStream;
import org.glassfish.grizzly.http.server.io.NIOWriter;
import org.glassfish.grizzly.http.server.io.OutputBuffer;
import org.glassfish.grizzly.http.util.CharChunk;
import org.glassfish.grizzly.http.util.CookieSerializerUtils;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpRequestURIDecoder;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MessageBytes;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http.util.UEncoder;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;

import static org.glassfish.grizzly.http.util.Constants.*;

/**
 * Wrapper object for the Coyote response.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 1.2 $ $Date: 2006/11/02 20:01:44 $
 */

public class Response {

    enum SuspendState {
        NONE, SUSPENDED, RESUMING, RESUMED, CANCELLED
    }

    private static final Logger LOGGER = Grizzly.logger(Response.class);

//    private static final ThreadCache.CachedTypeIndex<Response> CACHE_IDX =
//            ThreadCache.obtainIndex(Response.class, 16);
    private static final ThreadLocal<Response> current = new ThreadLocal<Response>();

//    public static Response create() {
//        final Response response =
//                ThreadCache.takeFromCache(CACHE_IDX);
//        if (response != null) {
//            return response;
//        }
//
//        return new Response();
//    }

    static DelayQueue<Response> createDelayQueue(DelayedExecutor delayedExecutor) {
        return delayedExecutor.createDelayQueue(new DelayQueueWorker(),
                new DelayQueueResolver());
    }

    // ----------------------------------------------------------- Constructors


    protected Response() {

        urlEncoder.addSafeCharacter('/');

    }


    // ----------------------------------------------------- Instance Variables
    private boolean cacheEnabled = false;


    /**
     * Default locale as mandated by the spec.
     */
    private static Locale DEFAULT_LOCALE = Locale.getDefault();

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
        "org.glassfish.grizzly.http.server.Response/2.0";


    // ------------------------------------------------------------- Properties
    /**
     * The request with which this response is associated.
     */
    protected Request request = null;


    /**
     * Coyote response.
     */
    protected HttpResponsePacket response;

    /**
     * Grizzly {@link org.glassfish.grizzly.filterchain.FilterChain} context,
     * related to this HTTP request/response
     */
    protected FilterChainContext ctx;


    /**
     * The associated output buffer.
     */
    protected final OutputBuffer outputBuffer = new OutputBuffer();


    /**
     * The associated output stream.
     */
    private final NIOOutputStreamImpl outputStream = new NIOOutputStreamImpl();

    /**
     * The associated writer.
     */
    private final NIOWriterImpl writer = new NIOWriterImpl();


    /**
     * The application commit flag.
     */
    protected boolean appCommitted = false;


    /**
     * The error flag.
     */
    protected boolean error = false;


    /**
     * The set of Cookies associated with this Response.
     */
    protected final List<Cookie> cookies = new ArrayList<Cookie>(4);


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
    protected final UEncoder urlEncoder = new UEncoder();


    /**
     * Recyclable buffer to hold the redirect URL.
     */
    protected final MessageBytes redirectURLCC = MessageBytes.newInstance();


    protected DelayedExecutor.DelayQueue<Response> delayQueue;

    final AtomicReference<SuspendState> suspendState =
            new AtomicReference<SuspendState>(SuspendState.NONE);

    private final SuspendedContextImpl suspendedContext = new SuspendedContextImpl();

    private final SuspendStatus suspendStatus = new SuspendStatus();
    private boolean sendFileEnabled;
    
    // --------------------------------------------------------- Public Methods

    public SuspendStatus initialize(final Request request,
                           final HttpResponsePacket response,
                           final FilterChainContext ctx,
                           final DelayedExecutor.DelayQueue<Response> delayQueue,
                           final HttpServerFilter serverFilter) {
        this.request = request;
        this.response = response;
        sendFileEnabled = ((serverFilter != null)
                && serverFilter.getConfiguration().isSendFileEnabled());
        outputBuffer.initialize(this, ctx);
        this.ctx = ctx;
        this.delayQueue = delayQueue;
        return suspendStatus.reset();
    }

    /**
     * Return the Request with which this Response is associated.
     */
    public Request getRequest() {
        return request;
    }


    /**
     * Get the {@link HttpResponsePacket}.
     */
    public HttpResponsePacket getResponse() {
        return response;
    }


    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    protected void recycle() {
        delayQueue = null;
        suspendedContext.reset();
        outputBuffer.recycle();
        outputStream.recycle();
        writer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        error = false;
        request = null;
        response.recycle();
        sendFileEnabled = false;
        response = null;
        ctx = null;
        suspendState.set(SuspendState.NONE);
        if (!cookies.isEmpty()) {
            for (Cookie cookie : cookies) {
                cookie.recycle();
            }
            cookies.clear();
        }

        cacheEnabled = false;
//
//        ThreadCache.putToCache(CACHE_IDX, this);
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

        final Session session = request.getSession(false);
        if (session == null)
            return (false);

        if (request.isRequestedSessionIdFromCookie())
            return (false);


        return doIsEncodeable(request, session, location);

    }

    private static boolean doIsEncodeable(Request request, Session session,
                                   String location){
        // Is this a valid absolute URL?
        URL url;
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

        String file = url.getFile();
        if ((file == null) || !file.startsWith(contextPath)) {
            return (false);
        }
        if (file.indexOf(";jsessionid=" + session.getIdInternal()) >= 0) {
            return (false);
        }


        // This URL belongs to our web application, so it is encodeable
        return (true);

    }


    /**
     * Return descriptive information about this Response implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return info;
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
        checkResponse();
        response.setReasonPhrase(message);
    }


    /**
     * Gets detail error message.
     *
     * @return the detail error message
     */
    public String getDetailMessage() {
        checkResponse();
        return response.getReasonPhrase();
    }
    // END S1AS 4878272


    /**
     * Perform whatever actions are required to flush and close the output
     * stream or writer, in a single operation.
     */
    public void finish() {
        // Writing leftover bytes
        try {
            outputBuffer.endRequest();
        } catch (IOException e) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "ACTION_CLIENT_FLUSH", e);
            }
        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "ACTION_CLIENT_FLUSH", t);
            }
        }
    }


    /**
     * Return the content length that was set or calculated for this Response.
     */
    public int getContentLength() {
        checkResponse();
        return (int) response.getContentLength();
    }

    /**
     * Return the content length that was set or calculated for this Response.
     */
    public long getContentLengthLong() {
        checkResponse();
        return response.getContentLength();
    }

    /**
     * Return the content type that was set or calculated for this response,
     * or <code>null</code> if no content type was set.
     */
    public String getContentType() {
        checkResponse();
        return response.getContentType();
    }


    // ------------------------------------------------ ServletResponse Methods


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
        final String characterEncoding = response.getCharacterEncoding();

        if (characterEncoding == null) {
            return DEFAULT_HTTP_CHARACTER_ENCODING;
        }

        return characterEncoding;
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

        // Ignore any call made after the getWriter has been invoked
        // The default should be used
        if (usingWriter)
            return;

        response.setCharacterEncoding(charset);
    }

    /**
     * Create and return a ServletOutputStream to write the content
     * associated with this Response.
     */
    public NIOOutputStream createOutputStream() {
        outputStream.setOutputBuffer(outputBuffer);
        return outputStream;
    }

    /**
     * <p>
     * Return the {@link NIOOutputStream} associated with this {@link Response}.
     * This {@link NIOOutputStream} will write content in a non-blocking manner.
     * </p>
     *
    * @throws IllegalStateException if {@link #getWriter()} or {@link #getNIOWriter()}
     *  were already invoked.
     */
    public NIOOutputStream getNIOOutputStream() {
        return getOutputStream0(false);
    }

    /**
     * <p>
     * Return the {@link OutputStream} associated with this {@link Response}.
     * This {@link OutputStream} will write content in a blocking manner.
     * </p>
     *
     * @return the {@link NIOOutputStream} associated with this {@link Response}.
     *
     * @throws IllegalStateException if {@link #getWriter()} or {@link #getNIOWriter()}
     *  were already invoked.
     *
     * @since 2.1.2
     */
    public OutputStream getOutputStream() {
        return getOutputStream0(true);
    }

    private NIOOutputStream getOutputStream0(final boolean blocking) {

        if (usingWriter)
            throw new IllegalStateException("Illegal attempt to call getOutputStream() after getWriter() has already been called.");

        usingOutputStream = true;
        outputBuffer.setAsyncEnabled(!blocking);
        outputStream.setOutputBuffer(outputBuffer);
        return outputStream;

    }

    /**
     * Return the Locale assigned to this response.
     */
    public Locale getLocale() {
        checkResponse();
        Locale locale = response.getLocale();
        if (locale == null) {
            locale = DEFAULT_LOCALE;
            response.setLocale(locale);
        }
        return locale;
    }


    /**
     * <p>
     * Return the {@link Writer} associated with this {@link Response}.
     * The {@link Writer} will write content in a blocking manner.
     * </p>
     *
     * @throws IllegalStateException if {@link #getOutputStream()} or
     *  {@link #getNIOOutputStream()} were already invoked.
     */
    public Writer getWriter() {

        return getWriter0(true);

    }


    /**
     * <p>
     * Return the {@link NIOWriter} associated with this {@link Response}.
     * The {@link NIOWriter} will write content in a non-blocking manner.
     * </p>
     *
     * @return the {@link NIOWriter} associated with this {@link Response}.
     *
     * @throws IllegalStateException if {@link #getOutputStream()} or
     *  {@link #getNIOOutputStream()} were already invoked.
     *
     * @since 2.1.2
     */
    public NIOWriter getNIOWriter() {
        return getWriter0(false);
    }

    private NIOWriter getWriter0(final boolean blocking) {
        if (usingOutputStream)
            throw new IllegalStateException("Illegal attempt to call getWriter() after getOutputStream() has already been called.");

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
        outputBuffer.prepareCharacterEncoder();
        outputBuffer.setAsyncEnabled(!blocking);
        writer.setOutputBuffer(outputBuffer);
        return writer;

    }


    /**
     * Has the output of this response already been committed?
     */
    public boolean isCommitted() {
        checkResponse();
        return response.isCommitted();
    }


    /**
     * Flush the current buffered content to the network.
     * @throws IOException
     */
    public void flush() throws IOException {
        outputBuffer.flush();
    }


    /**
     * @return the {@link OutputBuffer} associated with this
     *  <code>Response</code>.
     */
    public OutputBuffer getOutputBuffer() {
        return outputBuffer;
    }


    /**
     * Clears any data that exists in the buffer as well as the status code
     * and headers.
     *
     * @exception IllegalStateException if this response has already
     *  been committed
     */
    public void reset() {
        checkResponse();
        response.getHeaders().clear();
        response.setStatus(null);
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
            throw new IllegalStateException("Cannot reset buffer after response has been committed.");

        outputBuffer.reset();

        if(resetWriterStreamFlags) {
            usingOutputStream = false;
            usingWriter = false;
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

        if (isCommitted()) {
            throw new IllegalStateException("Unable to change buffer size as the response has been committed"); 
        }
        outputBuffer.setBufferSize(size);

    }


    /**
     * Set the content length (in bytes) for this Response.
     *
     * @param length The new content length
     */
    public void setContentLengthLong(final long length) {
        checkResponse();
        if (isCommitted())
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
    public void setContentLength(final int length) {
        checkResponse();
        if (isCommitted())
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

        response.setLocale(locale);
    }


    // --------------------------------------------------- HttpResponsePacket Methods


    /**
     * Return an array of all cookies set for this response, or
     * a zero-length array if no cookies have been set.
     */
    public Cookie[] getCookies() {
        return (cookies.toArray(new Cookie[cookies.size()]));
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
        return response.getHeaders().getHeader(name);
    }


    /**
     * Return an array of all the header names set for this response, or
     * a zero-length array if no headers have been set.
     */
    public String[] getHeaderNames() {
        checkResponse();
        MimeHeaders headers = response.getHeaders();
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
        final Collection<String> result = new LinkedList<String>();
        for (final String headerValue : response.getHeaders().values(name)) {
            result.add(headerValue);
        }
        
        return result.toArray(new String[result.size()]);
    }


    /**
     * Return the error message that was set with <code>sendError()</code>
     * for this Response.
     */
    public String getMessage() {
        checkResponse();
        return response.getReasonPhrase();
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
    @SuppressWarnings({"unchecked"})
    public void addCookie(final Cookie cookie) {

        if (isCommitted())
            return;

        final StringBuilder sb = new StringBuilder();
        //web application code can receive a IllegalArgumentException
        //from the appendCookieValue invokation
        if (System.getSecurityManager() != null) {
            AccessController.doPrivileged(new PrivilegedAction() {
                @Override
                public Object run() {
                    CookieSerializerUtils.serializeServerCookie(sb, cookie);
                    return null;
                }
            });
        } else {
            CookieSerializerUtils.serializeServerCookie(sb, cookie);
        }

        // if we reached here, no exception, cookie is valid
        // the header name is Set-Cookie for both "old" and v.1 ( RFC2109 )
        // RFC2965 is not supported by browsers and the Servlet spec
        // asks for 2109.
        addHeader(Header.SetCookie, sb.toString());

        cookies.add(cookie);
    }

    /**
     * Removes any Set-Cookie response headers whose value contains the
     * string "JSESSIONID=" or "JSESSIONIDSSO="
     */
    public void removeSessionCookies() {
        response.getHeaders().removeHeaderMatches(Header.SetCookie,
                Constants.SESSION_COOKIE_PATTERN);
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

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        addHeader(name, FastHttpDateFormat.formatDate(value, format));

    }

    /**
     * Add the specified date header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Date value to be set
     *
     * @since 2.1.2
     */
    public void addDateHeader(final Header header, long value) {

        if (isCommitted())
            return;

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        addHeader(header, FastHttpDateFormat.formatDate(value, format));

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

        response.addHeader(name, value);

    }

    /**
     * Add the specified header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Value to be set
     *
     * @since 2.1.2
     */
    public void addHeader(final Header header, final String value) {
        checkResponse();
        if (isCommitted())
            return;

        response.addHeader(header, value);

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

        addHeader(name, "" + value);

    }

    /**
     * Add the specified integer header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Integer value to be set
     *
     * @since 2.1.2
     */
    public void addIntHeader(final Header header, int value) {

        if (isCommitted())
            return;

        addHeader(header, Integer.toString(value));

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
     * Has the specified header been set already in this response?
     *
     * @param header the {@link Header} to check
     *
     * @since 2.1.2
     */
    public boolean containsHeader(final Header header) {
        checkResponse();
        return response.containsHeader(header);
    }


    /**
     * Send an acknowledgment of a request.   An acknowledgment in this
     * case is simply an HTTP response status line, i.e.
     * <code>HTTP/1.1 [STATUS] [REASON-PHRASE]<code>.
     *
     * @exception java.io.IOException if an input/output error occurs
     */
    public void sendAcknowledgement() throws IOException {
        if (isCommitted() || !request.requiresAcknowledgement())
            return;

        response.setAcknowledgement(true);
        outputBuffer.acknowledge();

    }


    /**
     * Send an error response with the specified status and a
     * default message.
     *
     * @param status HTTP status code to send
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception java.io.IOException if an input/output error occurs
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
     * @exception java.io.IOException if an input/output error occurs
     */
    public void sendError(int status, String message) throws IOException {
        checkResponse();
        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to call sendError() after the response has been committed.");

        setError();

        response.setStatus(status);
        response.setReasonPhrase(message);
        //response.setMessage(message);

        // Clear any data content that has been buffered
        resetBuffer();

        finish();
        // Cause the response to be finished (from the application perspective)
//        setSuspended(true);

    }


    /**
     * Send a temporary redirect to the specified redirect location URL.
     *
     * @param location Location URL to redirect to
     *
     * @exception IllegalStateException if this response has
     *  already been committed
     * @exception java.io.IOException if an input/output error occurs
     */
    public void sendRedirect(String location)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to redirect the response as the response has been committed.");

        // Clear any data content that has been buffered
        resetBuffer();

        // Generate a temporary redirect to the specified location
        try {
            String absolute = toAbsolute(location, true);
            // END RIMOD 4642650
            setStatus(HttpStatus.FOUND_302);
            setHeader(Header.Location, absolute);

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
                   getOutputStream().write(sb.toString().getBytes());
                } catch (IllegalStateException ise2) {
                   // ignore; the RFC says "SHOULD" so it is acceptable
                   // to omit the body in case of an error
                }
            }
        } catch (IllegalArgumentException e) {
            setStatus(HttpStatus.NOT_FOUND_404);
        }

        finish();
        // Cause the response to be finished (from the application perspective)
//        setSuspended(true);

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

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        setHeader(name, FastHttpDateFormat.formatDate(value, format));

    }

    /**
     * Set the specified date header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Date value to be set
     *
     * @since 2.1.2
     */
    public void setDateHeader(final Header header, long value) {

        if (isCommitted())
            return;

        if (format == null) {
            format = new SimpleDateFormat(HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        setHeader(header, FastHttpDateFormat.formatDate(value, format));

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

        response.setHeader(name, value);

    }

    /**
     * Set the specified header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Value to be set
     *
     * @since 2.1.2
     */
    public void setHeader(final Header header, final String value) {
        checkResponse();
        if (isCommitted())
            return;

        response.setHeader(header, value);

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

        setHeader(name, "" + value);

    }


    /**
     * Set the specified integer header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Integer value to be set
     *
     * @since 2.1.2
     */
    public void setIntHeader(final Header header, final int value) {

        if (isCommitted())
            return;

        setHeader(header, Integer.toString(value));

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

        response.setStatus(status);
        response.setReasonPhrase(message);

    }


    /**
     * Set the HTTP status and message to be returned with this response.
     * @param status {@link HttpStatus} to set
     */
    public void setStatus(HttpStatus status) {

        checkResponse();
        if (isCommitted())
            return;

        status.setValues(response);

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
    @SuppressWarnings({"unchecked"})
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
                    relativePath = relativePath.substring(0, pos);

                    String encodedURI;
                    final String frelativePath = relativePath;

                     if (System.getSecurityManager() != null ){
                        try{
                            encodedURI = AccessController.doPrivileged(
                                new PrivilegedExceptionAction<String>() {
                                @Override
                                    public String run() throws IOException{
                                        return urlEncoder.encodeURL(frelativePath);
                                    }
                           });
                        } catch (PrivilegedActionException pae){
                            throw new IllegalArgumentException(location, pae.getCause());
                        }
                    } else {
                        encodedURI = urlEncoder.encodeURL(relativePath);
                    }

                    cc.append(encodedURI, 0, encodedURI.length());
                    cc.append('/');
                }
                cc.append(location, 0, location.length());
            } catch (IOException e) {
                throw new IllegalArgumentException(location, e);
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
        final StringBuilder result = new StringBuilder(content.length + 50);
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
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    /**
     * Get the context of the suspended <tt>Response</tt>.
     *
     * @return the context of the suspended <tt>Response</tt>.
     */
    public SuspendContext getSuspendContext() {
        return suspendedContext;
    }
    
    /**
     * Return <tt>true<//tt> if that {@link Response#suspend()} has been
     * invoked and set to <tt>true</tt>
     * @return <tt>true<//tt> if that {@link Response#suspend()} has been
     * invoked and set to <tt>true</tt>
     */
    public boolean isSuspended() {
        checkResponse();

        final SuspendState state = suspendState.get();
        return state == SuspendState.SUSPENDED || state == SuspendState.RESUMING;
    }

    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid committing response.
     */
    public void suspend() {
        suspend(DelayedExecutor.UNSET_TIMEOUT, TimeUnit.MILLISECONDS);
    }

    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid committing response.
     *
     * @param timeout The maximum amount of time,
     * a {@link Response} can be suspended. When the timeout expires (because
     * nothing has been written or because the {@link Response#resume()}
     * or {@link Response#cancel()}), the {@link Response} will be automatically
     * resumed and committed. Usage of any methods of a {@link Response} that
     * times out will throw an {@link IllegalStateException}.
     * @param timeunit timeout units
     *
     */
    public void suspend(final long timeout, final TimeUnit timeunit) {
        suspend(timeout, timeunit, null);
    }

    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid committing response. When the
     * {@link Response#resume()} is invoked, the container will
     * make sure {@link CompletionHandler#completed(Object)}
     * is invoked with the original <tt>attachment</tt>. When the
     * {@link Response#cancel()} is invoked, the container will
     * make sure {@link org.glassfish.grizzly.CompletionHandler#cancelled()}
     * is invoked with the original <tt>attachment</tt>. If the timeout expires, the
     * {@link org.glassfish.grizzly.CompletionHandler#cancelled()} is invoked with the original <tt>attachment</tt> and
     * the {@link Response} committed.
     *
     * @param timeout The maximum amount of time the {@link Response} can be suspended.
     * When the timeout expires (because nothing has been written or because the
     * {@link Response#resume()} or {@link Response#cancel()}), the {@link Response}
     * will be automatically resumed and committed. Usage of any methods of a
     * {@link Response} that times out will throw an {@link IllegalStateException}.
     * @param timeunit timeout units
     * @param completionHandler a {@link org.glassfish.grizzly.CompletionHandler}
     */
    public void suspend(final long timeout, final TimeUnit timeunit,
            final CompletionHandler<Response> completionHandler) {
        suspend(timeout, timeunit, completionHandler, null);
    }

    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid committing response. When the
     * {@link Response#resume()} is invoked, the container will
     * make sure {@link CompletionHandler#completed(Object)}
     * is invoked with the original <tt>attachment</tt>. When the
     * {@link Response#cancel()} is invoked, the container will
     * make sure {@link org.glassfish.grizzly.CompletionHandler#cancelled()}
     * is invoked with the original <tt>attachment</tt>. If the timeout expires, the
     * {@link org.glassfish.grizzly.CompletionHandler#cancelled()} is invoked with the original <tt>attachment</tt> and
     * the {@link Response} committed.
     *
     * @param timeout The maximum amount of time the {@link Response} can be suspended.
     * When the timeout expires (because nothing has been written or because the
     * {@link Response#resume()} or {@link Response#cancel()}), the {@link Response}
     * will be automatically resumed and committed. Usage of any methods of a
     * {@link Response} that times out will throw an {@link IllegalStateException}.
     * @param timeunit timeout units
     * @param completionHandler a {@link org.glassfish.grizzly.CompletionHandler}
     * @param timeoutHandler {@link TimeoutHandler} to customize the suspended <tt>Response</tt> timeout logic.
     */
    public void suspend(final long timeout, final TimeUnit timeunit,
            final CompletionHandler<Response> completionHandler,
            final TimeoutHandler timeoutHandler) {

        checkResponse();

        if (!suspendState.compareAndSet(SuspendState.NONE, SuspendState.SUSPENDED)) {
            throw new IllegalStateException("Already Suspended");
        }

        suspendedContext.completionHandler = completionHandler;
        suspendedContext.timeoutHandler = timeoutHandler;

        final Connection connection = ctx.getConnection();

        HttpServerProbeNotifier.notifyRequestSuspend(
                request.httpServerFilter, connection, request);

        suspendStatus.set();

        connection.addCloseListener(suspendedContext);

        if (timeout > 0) {
            final long timeoutMillis =
                    TimeUnit.MILLISECONDS.convert(timeout, timeunit);
            suspendedContext.delayMillis = timeoutMillis;


            delayQueue.add(this, timeoutMillis, TimeUnit.MILLISECONDS);
        }

    }

    /**
     * Complete the {@link Response} and finish/commit it. If a
     * {@link CompletionHandler} has been defined, its {@link CompletionHandler#completed(Object)}
     * will first be invoked, then the {@link Response#finish()}.
     * Those operations commit the response.
     */
    @SuppressWarnings({"unchecked"})
    public void resume() {
        checkResponse();

        suspendedContext.markResumed();
        
        ctx.resume();
    }

    /**
     * Cancel the {@link Response} and finish/commit it. If a
     * {@link CompletionHandler} has been defined, its {@link CompletionHandler#cancelled()}
     * will first be invoked, then the {@link Response#finish()}.
     * Those operations commit the response.
     */
    public void cancel() {
        checkResponse();

        suspendedContext.markCancelled();

        ctx.resume();
    }
    
    /**
     * Make sure the {@link Response} object has been set.
     */
    final void checkResponse() {
        if (response == null) {
            throw new IllegalStateException("Internal " +
                    "org.glassfish.grizzly.http.server.Response has not been set");
        }
    }

    public static Response getCurrent() {
        return current.get();
    }

    public static void setCurrent(Response response) {
        current.set(response);
    }

    public boolean isSendFileEnabled() {
        return sendFileEnabled;
    }

    public final class SuspendedContextImpl implements SuspendContext,
            CloseListener {

        volatile CompletionHandler<Response> completionHandler;
        volatile TimeoutHandler timeoutHandler;
        long delayMillis;
        volatile long timeoutTimeMillis;

        /**
         * Marks {@link Response} as resumed, but doesn't resume associated
         * {@link FilterChainContext} invocation.
         */
        public void markResumed() {
            if (!suspendState.compareAndSet(SuspendState.SUSPENDED, SuspendState.RESUMING)) {
                throw new IllegalStateException("Not Suspended");
            }

            final Connection connection = ctx.getConnection();

            connection.removeCloseListener(this);

            if (completionHandler != null) {
                completionHandler.completed(Response.this);
            }

            reset();

            suspendState.set(SuspendState.RESUMED);

            HttpServerProbeNotifier.notifyRequestResume(request.httpServerFilter,
                    connection, request);
        }

        /**
         * Marks {@link Response} as cancelled, but doesn't resume associated
         * {@link FilterChainContext} invocation.
         */
        public void markCancelled() {
            if (!suspendState.compareAndSet(SuspendState.SUSPENDED, SuspendState.RESUMING)) {
                throw new IllegalStateException("Not Suspended");
            }

            final Connection connection = ctx.getConnection();

            connection.removeCloseListener(this);

            if (completionHandler != null) {
                completionHandler.cancelled();
            }

            suspendState.set(SuspendState.CANCELLED);
            reset();

            HttpServerProbeNotifier.notifyRequestCancel(
                    request.httpServerFilter, connection, request);
        }

        boolean onTimeout() {
            timeoutTimeMillis = DelayedExecutor.UNSET_TIMEOUT;
            final TimeoutHandler localTimeoutHandler = timeoutHandler;
            if (localTimeoutHandler == null
                    || localTimeoutHandler.onTimeout(Response.this)) {
                HttpServerProbeNotifier.notifyRequestTimeout(
                        request.httpServerFilter, ctx.getConnection(), request);

                try {
                    cancel();
                } catch (Exception ignored) {
                }

                return true;
            } else {
                return false;
            }
        }

        void reset() {
            timeoutTimeMillis = DelayedExecutor.UNSET_TIMEOUT;
            completionHandler = null;
            timeoutHandler = null;
        }

        @Override
        public void onClosed(final Connection connection,
                final CloseType closeType) throws IOException {
            cancel();
        }

        @Override
        public CompletionHandler<Response> getCompletionHandler() {
            return completionHandler;
        }

        @Override
        public TimeoutHandler getTimeoutHandler() {
            return timeoutHandler;
        }

        @Override
        public long getTimeout(final TimeUnit timeunit) {
            if (delayMillis > 0) {
                return timeunit.convert(delayMillis, TimeUnit.MILLISECONDS);
            } else {
                return delayMillis;
            }
        }

        @Override
        public void setTimeout(final long timeout, final TimeUnit timeunit) {
            if (suspendState.get() != SuspendState.SUSPENDED) {
                return;
            }

            if (timeout > 0) {
                delayMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
            } else {
                delayMillis = DelayedExecutor.UNSET_TIMEOUT;
            }

            delayQueue.add(Response.this, delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public boolean isSuspended() {
            return Response.this.isSuspended();
        }

        public SuspendStatus getSuspendStatus() {
            return suspendStatus;
        }
    }

    private static class DelayQueueWorker implements
            DelayedExecutor.Worker<Response> {

        @Override
        public boolean doWork(final Response element) {
            return element.suspendedContext.onTimeout();
        }
        
    }

    private static class DelayQueueResolver implements
            DelayedExecutor.Resolver<Response> {

        @Override
        public boolean removeTimeout(final Response element) {
            if (element.suspendedContext.timeoutTimeMillis != DelayedExecutor.UNSET_TIMEOUT) {
                element.suspendedContext.timeoutTimeMillis = DelayedExecutor.UNSET_TIMEOUT;
                return true;
            }

            return false;
        }

        @Override
        public Long getTimeoutMillis(final Response element) {
            return element.suspendedContext.timeoutTimeMillis;
        }

        @Override
        public void setTimeoutMillis(final Response element, final long timeoutMillis) {
            element.suspendedContext.timeoutTimeMillis = timeoutMillis;
        }

    }
}
