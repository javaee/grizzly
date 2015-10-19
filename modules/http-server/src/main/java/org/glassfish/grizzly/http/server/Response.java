/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.CloseListener;
import org.glassfish.grizzly.CloseType;
import org.glassfish.grizzly.Closeable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.GenericCloseListener;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.io.InputBuffer;
import org.glassfish.grizzly.http.io.NIOOutputStream;
import org.glassfish.grizzly.http.io.NIOWriter;
import org.glassfish.grizzly.http.io.OutputBuffer;
import org.glassfish.grizzly.http.server.io.ServerOutputBuffer;
import org.glassfish.grizzly.http.server.util.Globals;
import org.glassfish.grizzly.http.server.util.HtmlHelper;
import org.glassfish.grizzly.http.util.CharChunk;

import static org.glassfish.grizzly.http.util.Constants.*;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.http.util.CookieSerializerUtils;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HeaderValue;
import org.glassfish.grizzly.http.util.HttpRequestURIDecoder;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http.util.UEncoder;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.DelayedExecutor;
import org.glassfish.grizzly.utils.DelayedExecutor.DelayQueue;

/**
 * Wrapper object for the Coyote response.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 1.2 $ $Date: 2006/11/02 20:01:44 $
 */

public class Response {

    enum SuspendState {
        NONE, SUSPENDED, RESUMING, RESUMED, CANCELLING, CANCELLED
    }

    private static final Logger LOGGER = Grizzly.logger(Response.class);

    static DelayQueue<SuspendTimeout> createDelayQueue(
            final DelayedExecutor delayedExecutor) {
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
    private static final Locale DEFAULT_LOCALE = Locale.getDefault();

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
     * Grizzly {@link HttpContext} associated with the current Request/Response
     * processing.
     */
    protected HttpContext httpContext;

    /**
     * The associated output buffer.
     */
    protected final ServerOutputBuffer outputBuffer = new ServerOutputBuffer();


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
    protected final CharChunk redirectURLCC = new CharChunk();


    protected DelayedExecutor.DelayQueue<SuspendTimeout> delayQueue;

    SuspendState suspendState = SuspendState.NONE;

    private final SuspendedContextImpl suspendedContext = new SuspendedContextImpl();

    private SuspendStatus suspendStatus;
    private boolean sendFileEnabled;
    
    private ErrorPageGenerator errorPageGenerator;
    
    // --------------------------------------------------------- Public Methods

    public void initialize(final Request request,
                           final HttpResponsePacket response,
                           final FilterChainContext ctx,
                           final DelayedExecutor.DelayQueue<SuspendTimeout> delayQueue,
                           final HttpServerFilter serverFilter) {
        this.request = request;
        this.response = response;
        sendFileEnabled = ((serverFilter != null)
                && serverFilter.getConfiguration().isSendFileEnabled());
        outputBuffer.initialize(this, ctx);
        this.ctx = ctx;
        this.httpContext = HttpContext.get(ctx);
        this.delayQueue = delayQueue;
    }

    SuspendStatus initSuspendStatus() {
        suspendStatus = SuspendStatus.create();
        return suspendStatus;
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
        outputBuffer.recycle();
        outputStream.recycle();
        writer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        error = false;
        errorPageGenerator = null;
        request = null;
        response.recycle();
        sendFileEnabled = false;
        response = null;
        ctx = null;
        suspendState = SuspendState.NONE;

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
     * Encode the session identifier associated with this response
     * into the specified redirect URL, if necessary.
     *
     * @param url URL to be encoded
     */
    public String encodeRedirectURL(String url) {
        if (isEncodeable(toAbsolute(url, false))) {
            return toEncoded(url, request.getSession().getIdInternal());
        } else {
            return url;
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
        if (file.contains(";jsessionid=" + session.getIdInternal())) {
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

    /**
     * @return the {@link ErrorPageGenerator} to be used by
     * {@link #sendError(int)} or {@link #sendError(int, java.lang.String)}.
     */
    public ErrorPageGenerator getErrorPageGenerator() {
        return errorPageGenerator;
    }

    /**
     * Sets the {@link ErrorPageGenerator} to be used by
     * {@link #sendError(int)} or {@link #sendError(int, java.lang.String)}.
     * 
     * @param errorPageGenerator 
     */
    public void setErrorPageGenerator(ErrorPageGenerator errorPageGenerator) {
        this.errorPageGenerator = errorPageGenerator;
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
                LOGGER.log(Level.FINEST,
                        LogMessages.WARNING_GRIZZLY_HTTP_SERVER_RESPONSE_FINISH_ERROR(), e);
            }
        } catch (Throwable t) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_HTTP_SERVER_RESPONSE_FINISH_ERROR(), t);
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
        if (usingWriter)
            throw new IllegalStateException("Illegal attempt to call getOutputStream() after getWriter() has already been called.");

        usingOutputStream = true;
        outputStream.setOutputBuffer(outputBuffer);
        return outputStream;
    }

    /**
     * <p>
     * Return the {@link OutputStream} associated with this {@link Response}.
     * </p>
     * 
     * By default the returned {@link NIOOutputStream} will work as blocking
     * {@link java.io.OutputStream}, but it will be possible to call {@link NIOOutputStream#canWrite()} or
     * {@link NIOOutputStream#notifyCanWrite(org.glassfish.grizzly.WriteHandler)} to
     * avoid blocking.
     *
     * @return the {@link NIOOutputStream} associated with this {@link Response}.
     *
     * @throws IllegalStateException if {@link #getWriter()} or {@link #getNIOWriter()}
     *  were already invoked.
     *
     * @since 2.1.2
     */
    public OutputStream getOutputStream() {
        return getNIOOutputStream();
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
     * Return the {@link NIOWriter} associated with this {@link Response}.
     * </p>
     * 
     * By default the returned {@link NIOWriter} will work as blocking
     * {@link java.io.Writer}, but it will be possible to call {@link NIOWriter#canWrite()} or
     * {@link NIOWriter#notifyCanWrite(org.glassfish.grizzly.WriteHandler)} to
     * avoid blocking.
     *
     * @throws IllegalStateException if {@link #getOutputStream()} or
     *  {@link #getNIOOutputStream()} were already invoked.
     */
    public Writer getWriter() {
        return getNIOWriter();
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
        if (isCommitted()) {
            throw new IllegalStateException();
        }

        response.getHeaders().clear();
        response.setContentLanguage(null);
        if (response.getContentLength() > 0) {
            response.setContentLengthLong(-1L);
        }
        response.setCharacterEncoding(null);
        response.setStatus(null);
        response.setContentType((String) null);
        response.setLocale(null);
        outputBuffer.reset();
        usingWriter = false;
        usingOutputStream = false;
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
    public void resetBuffer(final boolean resetWriterStreamFlags) {

        if (isCommitted())
            throw new IllegalStateException("Cannot reset buffer after response has been committed.");

        outputBuffer.reset();

        if (resetWriterStreamFlags) {
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
    public void setBufferSize(final int size) {

        if (isCommitted()) {
            throw new IllegalStateException("Unable to change buffer size as the response has been committed"); 
        }
        outputBuffer.setBufferSize(size);

    }


    /**
     * Set the content length (in bytes) for this Response.
     *
     * If the <code>length</code> argument is negative - then {@link org.glassfish.grizzly.http.HttpPacket}
     * content-length value will be reset to <tt>-1</tt> and
     * <tt>Content-Length</tt> header (if present) will be removed.
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
     * If the <code>length</code> argument is negative - then {@link org.glassfish.grizzly.http.HttpPacket}
     * content-length value will be reset to <tt>-1</tt> and
     * <tt>Content-Length</tt> header (if present) will be removed.
     *
     * @param length The new content length
     */
    public void setContentLength(final int length) {
        setContentLengthLong(length);
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
     * Set the content type for this Response.
     *
     * @param type The new content type
     */
    public void setContentType(final ContentType type) {
        checkResponse();
        if (isCommitted())
            return;

        if (type == null) {
            response.setContentType((String) null);
            return;
        }
        
        if (!usingWriter) {
            response.setContentType(type);
        } else {
            // Ignore charset if getWriter() has already been called
            response.setContentType(type.getMimeType());
        }
    }
    
    /**
     * Set the Locale that is appropriate for this response, including
     * setting the appropriate character encoding.
     *
     * @param locale The new locale
     */
    public void setLocale(final Locale locale) {
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
        final Cookies cookies = new Cookies();
        cookies.setHeaders(response.getHeaders(), false);
        
        return cookies.get();
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
        return response.getHeader(name);
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
    public String[] getHeaderValues(final String name) {
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
    public void reset(final int status, final String message) {
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
    }

    /**
     * Special method for adding a session cookie as we should be overriding 
     * any previous 
     * @param cookie
     */
    protected void addSessionCookieInternal(final Cookie cookie) {
        if (isCommitted())
            return;

        String name = cookie.getName();
        final String headername = Header.SetCookie.toString();
        final String startsWith = name + "=";

        final StringBuilder sb = new StringBuilder();
        //web application code can receive a IllegalArgumentException
        //from the appendCookieValue invokation
        if (System.getSecurityManager() != null) {
            AccessController.doPrivileged(new PrivilegedAction<Object>() {
                @Override
                public Object run() {
                    CookieSerializerUtils.serializeServerCookie(sb, cookie);
                    return null;
                }
            });
        } else {
            CookieSerializerUtils.serializeServerCookie(sb, cookie);
        }
        
        final String cookieString = sb.toString();
        
        boolean set = false;
        MimeHeaders headers = response.getHeaders();
        int n = headers.size();
        for (int i = 0; i < n; i++) {
            if (headers.getName(i).toString().equals(headername)) {
                if (headers.getValue(i).toString().startsWith(startsWith)) {
                    headers.getValue(i).setString(cookieString);
                    set = true;
                }
            }
        }
        if (!set) {
            addHeader(headername, cookieString);
        }
    }
    
    /**
     * Removes any Set-Cookie response headers whose value contains the
     * string "JSESSIONID=" or "JSESSIONIDSSO="
     */
    protected void removeSessionCookies() {
        final String sessionCookieName = request.getSessionCookieName();
        final String pattern = sessionCookieName != null
                ? '^' + sessionCookieName + "(?:SSO)?=.*"
                : Globals.SESSION_COOKIE_PATTERN;
        
        response.getHeaders().removeHeaderMatches(Header.SetCookie, pattern);
    }
    
    /**
     * Add the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    public void addDateHeader(final String name, final long value) {

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
    public void addDateHeader(final Header header, final long value) {

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
    public void addHeader(final String name, final String value) {
        checkResponse();
        if (isCommitted())
            return;

        response.addHeader(name, value);
    }

    /**
     * Add the specified header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Value to be set
     * 
     * @since 2.3.8
     */
    public void addHeader(final String name, final HeaderValue value) {
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
     * Add the specified header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Value to be set
     *
     * @since 2.3.8
     */
    public void addHeader(final Header header, final HeaderValue value) {
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
    public void addIntHeader(final String name, final int value) {

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
    public void addIntHeader(final Header header, final int value) {

        if (isCommitted())
            return;

        addHeader(header, Integer.toString(value));

    }


    /**
     * Has the specified header been set already in this response?
     *
     * @param name Name of the header to check
     */
    public boolean containsHeader(final String name) {
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
    public void sendError(final int status, final String message)
            throws IOException {
        checkResponse();
        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to call sendError() after the response has been committed.");

        setError();

        response.getHeaders().removeHeader(Header.TransferEncoding);
        response.setContentLanguage(null);
        response.setContentLengthLong(-1L);
        response.setChunked(false);
        response.setCharacterEncoding(null);
        response.setContentType((String) null);
        response.setLocale(null);
        outputBuffer.reset();
        usingWriter = false;
        usingOutputStream = false;
        
        setStatus(status, message);
        
        String nonNullMsg = message;
        if (nonNullMsg == null) {
            final HttpStatus httpStatus = HttpStatus.getHttpStatus(status);
            if (httpStatus != null && httpStatus.getReasonPhrase() != null) {
                nonNullMsg = httpStatus.getReasonPhrase();
            } else {
                nonNullMsg = "Unknown Error";
            }
        }
        
        HtmlHelper.sendErrorPage(request, this, getErrorPageGenerator(),
                status, nonNullMsg, nonNullMsg, null);
        
        finish();
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
                   getOutputStream().write(sb.toString().getBytes(
                           org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET));
                } catch (IllegalStateException ise2) {
                   // ignore; the RFC says "SHOULD" so it is acceptable
                   // to omit the body in case of an error
                }
            }
        } catch (IllegalArgumentException e) {
            sendError(404);
        }

        finish();
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
    public void setHeader(final String name, final String value) {
        checkResponse();
        if (isCommitted())
            return;

        response.setHeader(name, value);
    }

    /**
     * Set the specified header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Value to be set
     * 
     * @since 2.3.8
     */
    public void setHeader(final String name, final HeaderValue value) {
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
     * Set the specified header to the specified value.
     *
     * @param header the {@link Header} to set
     * @param value Value to be set
     *
     * @since 2.3.8
     */
    public void setHeader(final Header header, final HeaderValue value) {
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
    protected String toAbsolute(final String location, final boolean normalize) {

        if (location == null)
            return location;

        final boolean leadingSlash = location.startsWith("/");

        if (leadingSlash || (!location.contains("://"))) {

            final String scheme = request.getScheme();

            final String name = request.getServerName();
            final int port = request.getServerPort();
            
            redirectURLCC.recycle();
            final CharChunk cc = redirectURLCC;
            
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
                    final int pos = relativePath.lastIndexOf('/');
                    relativePath = relativePath.substring(0, pos);

                    final String encodedURI;
                    if (System.getSecurityManager() != null) {
                        try {
                            final String frelativePath = relativePath;
                            encodedURI = AccessController.doPrivileged(
                                    new PrivilegedExceptionAction<String>() {
                                        @Override
                                        public String run() throws IOException {
                                            return urlEncoder.encodeURL(frelativePath);
                                        }
                                    });
                        } catch (PrivilegedActionException pae) {
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
                HttpRequestURIDecoder.normalizeChars(cc);
            }

            return cc.toString();

        } else {
            return location;
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

        final SuspendState state;
        synchronized (suspendedContext) {
            state = suspendState;
        }
        
        return state == SuspendState.SUSPENDED || state == SuspendState.RESUMING
                || state == SuspendState.CANCELLING;
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
     * @deprecated timeout parameters don't make any sense without CompletionHandler
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

        if (suspendState != SuspendState.NONE) {
            throw new IllegalStateException("Already Suspended");
        }

        suspendState = SuspendState.SUSPENDED;

        suspendStatus.suspend();

        suspendedContext.init(completionHandler, timeoutHandler);

        HttpServerProbeNotifier.notifyRequestSuspend(
                request.httpServerFilter, ctx.getConnection(), request);

        httpContext.getCloseable().addCloseListener(suspendedContext.closeListener);

        if (timeout > 0) {
            final long timeoutMillis =
                    TimeUnit.MILLISECONDS.convert(timeout, timeunit);

            delayQueue.add(suspendedContext.suspendTimeout,
                    timeoutMillis, TimeUnit.MILLISECONDS);
            suspendedContext.suspendTimeout.delayMillis = timeoutMillis;
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
     * 
     * @deprecated pls. use {@link #resume()}
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

    public boolean isSendFileEnabled() {
        return sendFileEnabled;
    }

    public final class SuspendedContextImpl implements SuspendContext {

        private int modCount;
        
        CompletionHandler<Response> completionHandler;
        SuspendTimeout suspendTimeout;
        
        private CloseListener closeListener;
        
        /**
         * Marks {@link Response} as resumed, but doesn't resume associated
         * {@link FilterChainContext} invocation.
         */
        public synchronized boolean markResumed() {
            modCount++;
            
            if (suspendState != SuspendState.SUSPENDED) {
                if (suspendState == SuspendState.CANCELLED ||
                        suspendState == SuspendState.CANCELLING) { // Siletly return if processing has been cancelled
                    return false;
                }
                
                throw new IllegalStateException("Not Suspended");
            }
            
            suspendState = SuspendState.RESUMING;

            httpContext.getCloseable().removeCloseListener(closeListener);

            if (completionHandler != null) {
                completionHandler.completed(Response.this);
            }

            reset();

            suspendState = SuspendState.RESUMED;

            HttpServerProbeNotifier.notifyRequestResume(request.httpServerFilter,
                    ctx.getConnection(), request);
            
            return true;
        }

        /**
         * Marks {@link Response} as cancelled, if expectedModCount corresponds
         * to the current modCount. This method doesn't resume associated
         * {@link FilterChainContext} invocation.
         */
        protected synchronized boolean markCancelled(final int expectedModCount) {
            if (modCount != expectedModCount) {
                return false;
            }
            
            modCount++;
            
            if (suspendState != SuspendState.SUSPENDED) {
                throw new IllegalStateException("Not Suspended");
            }

            suspendState = SuspendState.CANCELLING;
            
            httpContext.getCloseable().removeCloseListener(closeListener);

            if (completionHandler != null) {
                completionHandler.cancelled();
            }

            suspendState = SuspendState.CANCELLED;
            reset();

            HttpServerProbeNotifier.notifyRequestCancel(
                    request.httpServerFilter, ctx.getConnection(), request);
            
            final InputBuffer inputBuffer = request.getInputBuffer();
            if (!inputBuffer.isFinished()) {
                inputBuffer.terminate();
            }
            
            return true;
        }
        
        /**
         * Marks {@link Response} as cancelled, but doesn't resume associated
         * {@link FilterChainContext} invocation.
         * @deprecated
         */
        public synchronized void markCancelled() {
            markCancelled(modCount);
        }

        private void init(final CompletionHandler<Response> completionHandler,
                final TimeoutHandler timeoutHandler) {
            this.completionHandler = completionHandler;
            this.suspendTimeout = new SuspendTimeout(modCount, timeoutHandler);
            closeListener = new SuspendCloseListener(modCount);
        }
        
        void reset() {
            suspendTimeout.reset();
            suspendTimeout = null;
            completionHandler = null;
            closeListener = null;
        }

        @Override
        public CompletionHandler<Response> getCompletionHandler() {
            return completionHandler;
        }

        @Override
        public TimeoutHandler getTimeoutHandler() {
            return suspendTimeout.timeoutHandler;
        }

        @Override
        public long getTimeout(final TimeUnit timeunit) {
            return suspendTimeout.getTimeout(timeunit);
        }

        @Override
        public void setTimeout(final long timeout, final TimeUnit timeunit) {
            synchronized (suspendedContext) {
                if (suspendState != SuspendState.SUSPENDED ||
                        suspendTimeout == null) {
                    return;
                }
                
                suspendTimeout.setTimeout(timeout, timeunit);
            }
        }

        @Override
        public boolean isSuspended() {
            return Response.this.isSuspended();
        }
        
        public SuspendStatus getSuspendStatus() {
            return suspendStatus;
        }        
        
        private class SuspendCloseListener implements GenericCloseListener {
            private final int expectedModCount;

            public SuspendCloseListener(int expectedModCount) {
                this.expectedModCount = expectedModCount;
            }
            
            @Override
            public void onClosed(final Closeable connection,
                    final CloseType closeType) throws IOException {
                checkResponse();

                if (suspendedContext.markCancelled(expectedModCount)) {
//                    ctx.resume();
                }
            }
        }
    }

    protected class SuspendTimeout {
        private final int expectedModCount;

        TimeoutHandler timeoutHandler;
        long delayMillis;
        
        volatile long timeoutTimeMillis;

        private SuspendTimeout(int modCount, TimeoutHandler timeoutHandler) {
            this.expectedModCount = modCount;
            this.timeoutHandler = timeoutHandler;
        }

        boolean onTimeout() {
            timeoutTimeMillis = DelayedExecutor.UNSET_TIMEOUT;
            final TimeoutHandler localTimeoutHandler = timeoutHandler;
            if (localTimeoutHandler == null
                    || localTimeoutHandler.onTimeout(Response.this)) {
                HttpServerProbeNotifier.notifyRequestTimeout(
                        request.httpServerFilter, ctx.getConnection(), request);

                try {
                    checkResponse();

                    if (suspendedContext.markCancelled(expectedModCount)) {
//                        ctx.resume();
                    }
                } catch (Exception ignored) {
                }

                return true;
            } else {
                return false;
            }
        }

        private long getTimeout(TimeUnit timeunit) {
            if (delayMillis > 0) {
                return timeunit.convert(delayMillis, TimeUnit.MILLISECONDS);
            } else {
                return delayMillis;
            }
        }

        private void setTimeout(long timeout, TimeUnit timeunit) {
            if (timeout > 0) {
                delayMillis = TimeUnit.MILLISECONDS.convert(timeout, timeunit);
            } else {
                delayMillis = DelayedExecutor.UNSET_TIMEOUT;
            }

            delayQueue.add(this, delayMillis, TimeUnit.MILLISECONDS);
        }

        private void reset() {
            timeoutTimeMillis = DelayedExecutor.UNSET_TIMEOUT;
            timeoutHandler = null;
        }
    }
    
    private static class DelayQueueWorker implements
            DelayedExecutor.Worker<SuspendTimeout> {

        @Override
        public boolean doWork(final SuspendTimeout element) {
            return element.onTimeout();
        }
        
    }

    private static class DelayQueueResolver implements
            DelayedExecutor.Resolver<SuspendTimeout> {

        @Override
        public boolean removeTimeout(final SuspendTimeout element) {
            if (element.timeoutTimeMillis != DelayedExecutor.UNSET_TIMEOUT) {
                element.timeoutTimeMillis = DelayedExecutor.UNSET_TIMEOUT;
                return true;
            }

            return false;
        }

        @Override
        public long getTimeoutMillis(final SuspendTimeout element) {
            return element.timeoutTimeMillis;
        }

        @Override
        public void setTimeoutMillis(final SuspendTimeout element, final long timeoutMillis) {
            element.timeoutTimeMillis = timeoutMillis;
        }

    }
}
