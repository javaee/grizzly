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

import java.io.CharConversionException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.Subject;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.ReadHandler;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.Cookie;
import org.glassfish.grizzly.http.Cookies;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Note;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.io.InputBuffer;
import org.glassfish.grizzly.http.io.NIOInputStream;
import org.glassfish.grizzly.http.io.NIOReader;
import org.glassfish.grizzly.http.server.io.ServerInputBuffer;
import org.glassfish.grizzly.http.server.util.Globals;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.server.util.ParameterMap;
import org.glassfish.grizzly.http.server.util.RequestUtils;
import org.glassfish.grizzly.http.server.util.SimpleDateFormats;
import org.glassfish.grizzly.http.server.util.StringParser;
import org.glassfish.grizzly.http.util.Chunk;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.utils.JdkVersion;

import static org.glassfish.grizzly.http.util.Constants.FORM_POST_CONTENT_TYPE;
/**
 * Wrapper object for the Coyote request.
 *
 * @author Remy Maucherat
 * @author Craig R. McClanahan
 * @version $Revision: 1.2 $ $Date: 2007/03/14 02:15:42 $
 */

public class Request {
    // @TODO remove this property support once we're sure nobody
    // relies on this functionality.
    private static final Boolean FORCE_CLIENT_AUTH_ON_GET_USER_PRINCIPAL =
            Boolean.getBoolean(Request.class.getName() + ".force-client-auth-on-get-user-principal");
    
    private static final Logger LOGGER = Grizzly.logger(Request.class);
    
    private static final ThreadCache.CachedTypeIndex<Request> CACHE_IDX =
            ThreadCache.obtainIndex(Request.class, 16);

    private static final LocaleParser localeParser;
    static {
        LocaleParser lp;
        final JdkVersion version = JdkVersion.getJdkVersion();
        
        if (version.compareTo("1.7.0") >= 0) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends LocaleParser> localeParserClazz =
                        (Class<? extends LocaleParser>)
                                Class.forName("org.glassfish.grizzly.http.server.TagLocaleParser");
                lp = localeParserClazz.newInstance();
            } catch (Throwable e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Can't load JDK7 TagLocaleParser", e);
                }
                lp = new LegacyLocaleParser();
            }
        } else {
            lp = new LegacyLocaleParser();
        }
        
        localeParser = lp;
        
        assert localeParser != null;
    }

    public static Request create() {
        final Request request =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (request != null) {
            return request;
        }

        return new Request(new Response());
    }

    /**
     * Request attribute will be associated with a boolean value indicating
     * whether or not it's possible to transfer a {@link java.io.File} using sendfile.
     *
     * @since 2.2
     */
    public static final String SEND_FILE_ENABLED_ATTR = "org.glassfish.grizzly.http.SEND_FILE_ENABLED";

    /**
     * <p>
     * The value of this request attribute, as set by the developer must be a {@link java.io.File}
     * that exists, is not a directory, and is readable.  This {@link java.io.File} will be
     * transferred using sendfile if {@link #SEND_FILE_ENABLED_ATTR} is true.  If sendfile
     * support isn't enabled, an IllegalStateException will be raised at runtime.
     * The {@link HttpHandler} using this functionality should refrain from writing content
     * via the response.
     * </p>
     *
     * <p>
     * Note that once this attribute is set, the sendfile process will begin.
     * </p>
     *
     * @since 2.2
     */
    public static final String SEND_FILE_ATTR = "org.glassfish.grizzly.http.SEND_FILE";

    /**
     * <p>
     * The value of this request attribute signifies the starting offset of the file
     * transfer.  If not specified, an offset of zero will be assumed.   The type of
     * the value must be {@link Long}.
     * </p>
     *
     * <p>
     * NOTE:  In order for this attribute to take effect, it <em>must</em> be
     * set <em>before</em> the {@link #SEND_FILE_ATTR} is set.
     * </p>
     *
     * @since 2.2
     */
    public static final String SEND_FILE_START_OFFSET_ATTR = "org.glassfish.grizzly.http.FILE_START_OFFSET";

    /**
     * <p>
     * The value of this request attribute signifies the total number of bytes to
     * transfer.  If not specified, the entire file will be transferred.
     * The type of the value must be {@link Long}
     * </p>
     *
     * <p>
     * NOTE:  In order for this attribute to take effect, it <em>must</em> be
     * set <em>before</em> the {@link #SEND_FILE_ATTR} is set.
     * </p>
     *
     * @since 2.2
     */
    public static final String SEND_FILE_WRITE_LEN_ATTR = "org.glassfish.grizzly.http.FILE_WRITE_LEN";

    // ------------------------------------------------------------- Properties
    /**
     * The match string for identifying a session ID parameter.
     */
    private static final String match =
        ';' + Globals.SESSION_PARAMETER_NAME + '=';

    // -------------------------------------------------------------------- //


    public final MappingData obtainMappingData() {
        if (cachedMappingData == null) {
            cachedMappingData = new MappingData();
        }

        return cachedMappingData;
    }


    // --------------------------------------------------------------------- //

    // ----------------------------------------------------- Instance Variables

    /**
     * HTTP Request Packet
     */
    protected HttpRequestPacket request;

    protected FilterChainContext ctx;

    protected HttpServerFilter httpServerFilter;

    protected final List<AfterServiceListener> afterServicesList =
            new ArrayList<AfterServiceListener>(4);

    private Session session;

    /**
     * The HTTP request scheme.
     */
    private String scheme;

    private final PathData contextPath = new PathData(this, "", null);
    private final PathData httpHandlerPath = new PathData(this);
    private final PathData pathInfo = new PathData(this);

    private MappingData cachedMappingData;

    /**
     * The set of cookies associated with this Request.
     */
    protected Cookie[] cookies = null;


    protected Cookies rawCookies;
    
    // Session cookie name
    protected String sessionCookieName;
    
    // Session manager
    protected SessionManager sessionManager;
    
    /**
     * The default Locale if none are specified.
     */
    protected static final Locale defaultLocale = Locale.getDefault();


    /**
     * The preferred Locales associated with this Request.
     */
    protected final ArrayList<Locale> locales = new ArrayList<Locale>();
    

    /**
     * The current dispatcher type.
     */
    protected Object dispatcherType = null;


    /**
     * The associated input buffer.
     */
    protected final ServerInputBuffer inputBuffer = new ServerInputBuffer();


    /**
     * NIOInputStream.
     */
    private final NIOInputStreamImpl inputStream = new NIOInputStreamImpl();


    /**
     * Reader.
     */
    private final NIOReaderImpl reader = new NIOReaderImpl();


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
     * Session parsed flag.
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
     * Hash map used in the getParametersMap method.
     */
    protected final ParameterMap parameterMap = new ParameterMap();


    protected final Parameters parameters = new Parameters();


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
     * Parse locales.
     */
    protected boolean localesParsed = false;


    /**
     * The string parser we will use for parsing request lines.
     */
    private StringParser parser;

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
     * The {@link RequestExecutorProvider} responsible for executing user's code
     * in {@link HttpHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}
     * and notifying {@link ReadHandler}, {@link WriteHandler} registered by the user
     */
     private RequestExecutorProvider requestExecutorProvider;
    
    /**
     * The response with which this request is associated.
     */
    protected final Response response;

    // ----------------------------------------------------------- Constructors
    /**
     * Temporarily introduce public constructor to fix GRIZZLY-1782.
     * Just to make request instances proxiable.
     * This constructor is not intended for client code consumption
     * and should not be used explicitly as it creates an invalid instance.
     * 
     * @deprecated
     */
    public Request() {
        this.response = null;
    }

    protected Request(final Response response) {
        this.response = response;
    }

    // --------------------------------------------------------- Public Methods

    public void initialize(final HttpRequestPacket request,
                           final FilterChainContext ctx,
                           final HttpServerFilter httpServerFilter) {
        this.request = request;
        this.ctx = ctx;
        this.httpServerFilter = httpServerFilter;
        inputBuffer.initialize(this, ctx);
        
        parameters.setHeaders(request.getHeaders());
        parameters.setQuery(request.getQueryStringDC());

        final DataChunk remoteUser = request.remoteUser();

        if (httpServerFilter != null) {
            final ServerFilterConfiguration configuration =
                    httpServerFilter.getConfiguration();
            parameters.setQueryStringEncoding(configuration.getDefaultQueryEncoding());

            final BackendConfiguration backendConfiguration =
                    configuration.getBackendConfiguration();
            
            if (backendConfiguration != null) {
                // Set the protocol scheme based on backend config
                if (backendConfiguration.getScheme() != null) {
                    scheme = backendConfiguration.getScheme();
                } else if (backendConfiguration.getSchemeMapping() != null) {
                    scheme = request.getHeader(backendConfiguration.getSchemeMapping());
                }

                if ("https".equalsIgnoreCase(scheme)) {
                    // this ensures that JSESSIONID cookie has the "Secure" attribute
                    // when using scheme-mapping
                    request.setSecure(true);
                }
                
                if (remoteUser.isNull()
                        && backendConfiguration.getRemoteUserMapping() != null) {
                    remoteUser.setString(request.getHeader(
                            backendConfiguration.getRemoteUserMapping()));
                }
            }
        }
        
        if (scheme == null) {
            scheme = request.isSecure() ? "https" : "http";
        }

        if (!remoteUser.isNull()) {
            setUserPrincipal(new GrizzlyPrincipal(remoteUser.toString()));
        }
    }

    final HttpServerFilter getServerFilter() {
        return httpServerFilter;
    }

    /**
     * @return the Coyote request.
     */
    public HttpRequestPacket getRequest() {
        return this.request;
    }

    /**
     * @return the Response with which this Request is associated.
     */
    public Response getResponse() {
        return response;
    }

    /**
     * @return session cookie name, if not set default JSESSIONID name will be used
     */
    public String getSessionCookieName() {
        return sessionCookieName;
    }

    /**
     * Set the session cookie name, if not set default JSESSIONID name will be used
     * @param sessionCookieName 
     */
    public void setSessionCookieName(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }
    
    /**
     * @return {@link #sessionCookieName} if set, or {@link Globals#SESSION_COOKIE_NAME} if
     * {@link #sessionCookieName} is not set
     */
    protected String obtainSessionCookieName() {
        return sessionCookieName != null ? sessionCookieName : Globals.SESSION_COOKIE_NAME;
    }

    /**
     * @return {@link SessionManager}
     */
    protected SessionManager getSessionManager() {
        return sessionManager != null
                ? sessionManager
                : DefaultSessionManager.instance();
    }

    /**
     * Set {@link SessionManager}, <tt>null</tt> value implies {@link DefaultSessionManager}
     * @param sessionManager 
     */    
    protected void setSessionManager(final SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }
        
    /**
     * @return the {@link Executor} responsible for notifying {@link ReadHandler},
     * {@link WriteHandler} associated with this <tt>Request</tt> processing.
     */    
    public Executor getRequestExecutor() {
        return requestExecutorProvider.getExecutor(this);
    }

    /**
     * Sets @return the {@link RequestExecutorProvider} responsible for executing
     * user's code in {@link HttpHandler#service(org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}
     * and notifying {@link ReadHandler}, {@link WriteHandler} registered by the user.
     * 
     * @param requestExecutorProvider {@link RequestExecutorProvider}
     */
    protected void setRequestExecutorProvider(
            final RequestExecutorProvider requestExecutorProvider) {
        this.requestExecutorProvider = requestExecutorProvider;
    }

    /**
     * Add the listener, which will be notified, once <tt>Request</tt> processing will be finished.
     * @param listener the listener, which will be notified, once <tt>Request</tt> processing will be finished.
     */
    public void addAfterServiceListener(final AfterServiceListener listener) {
        afterServicesList.add(listener);
    }

    /**
     * Remove the "after-service" listener, which was previously added by {@link #addAfterServiceListener(org.glassfish.grizzly.http.server.AfterServiceListener)}.
     * @param listener the "after-service" listener, which was previously added by {@link #addAfterServiceListener(org.glassfish.grizzly.http.server.AfterServiceListener)}.
     */
    public void removeAfterServiceListener(final AfterServiceListener listener) {
        afterServicesList.remove(listener);
    }

    protected void onAfterService() {
        if (!inputBuffer.isFinished()) {
            inputBuffer.terminate();
        }

        if (!afterServicesList.isEmpty()) {
            for (int i = 0, size = afterServicesList.size(); i < size; i++) {
                final AfterServiceListener anAfterServicesList = afterServicesList.get(i);
                try {
                    anAfterServicesList.onAfterService(this);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            LogMessages.WARNING_GRIZZLY_HTTP_SERVER_REQUEST_AFTERSERVICE_NOTIFICATION_ERROR(), e);
                }
            }
        }
    }

    /**
     * Release all object references, and initialize instance variables, in
     * preparation for reuse of this object.
     */
    protected void recycle() {
        scheme = null;
        contextPath.setPath("");
        httpHandlerPath.reset();
        pathInfo.reset();
        dispatcherType = null;
        requestDispatcherPath = null;
        
        inputBuffer.recycle();
        inputStream.recycle();
        reader.recycle();
        usingInputStream = false;
        usingReader = false;
        userPrincipal = null;
        subject = null;
        sessionParsed = false;
        requestParametersParsed = false;
        cookiesParsed = false;

        if (rawCookies != null) {
            rawCookies.recycle();
        }

        locales.clear();
        localesParsed = false;
        secure = false;

        request.recycle();
        request = null;
        ctx = null;
        httpServerFilter = null;

        cookies = null;
        requestedSessionId = null;
        sessionCookieName = null;
        sessionManager = null;
        session = null;
        dispatchDepth = 0; // S1AS 4703023

        parameterMap.setLocked(false);
        parameterMap.clear();
        parameters.recycle();

        requestExecutorProvider = null;
        
        afterServicesList.clear();

        // Notes holder shouldn't be recycled.
        //        notesHolder.recycle();

        if (cachedMappingData != null) {
            cachedMappingData.recycle();
        }

        ThreadCache.putToCache(CACHE_IDX, this);
    }


    // -------------------------------------------------------- Request Methods


    /**
     * Return the authorization credentials sent with this request.
     */
    public String getAuthorization() {
        return request.getHeader(Constants.AUTHORIZATION_HEADER);
    }

    // ------------------------------------------------- Request Public Methods


    /**
     * Replays request's payload by setting new payload {@link Buffer}.
     * If request parameters have been parsed based on prev. request's POST
     * payload - the parameters will be recycled and ready to be parsed again.
     * 
     * @param buffer payload
     * 
     * @throws IllegalStateException, if previous request payload has not been read off.
     */
    public void replayPayload(final Buffer buffer) {
        inputBuffer.replayPayload(buffer);
        usingReader = false;
        usingInputStream = false;
        
        if (Method.POST.equals(getMethod()) && requestParametersParsed) {
            requestParametersParsed = false;
            parameterMap.setLocked(false);
            parameterMap.clear();
            parameters.recycle();
        }
    }
    
    /**
     * Create and return a NIOInputStream to read the content
     * associated with this Request.
     * 
     * @return {@link NIOInputStream}
     */
    public NIOInputStream createInputStream() {

        inputStream.setInputBuffer(inputBuffer);
        return inputStream;
    }


    /**
     * Create a named {@link Note} associated with this Request.
     *
     * @param <E> the {@link Note} type.
     * @param name the {@link Note} name.
     * @return the {@link Note}.
     */
    @SuppressWarnings({"unchecked"})
    public static <E> Note<E> createNote(final String name) {
        return HttpRequestPacket.createNote(name);
    }

    /**
     * Return the {@link Note} value associated with this <tt>Request</tt>,
     * or <code>null</code> if no such binding exists.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @param note {@link Note} value to be returned
     */
    public <E> E getNote(final Note<E> note) {
        return request.getNote(note);
    }


    /**
     * Return a {@link Set} containing the String names of all note bindings
     * that exist for this request.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @return a {@link Set} containing the String names of all note bindings
     * that exist for this request.
     */
    public Set<String> getNoteNames() {
        return request.getNoteNames();
    }


    /**
     * Remove the {@link Note} value associated with this request.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @param note {@link Note} value to be removed
     */
    public <E> E removeNote(final Note<E> note) {
        return request.removeNote(note);
    }


    /**
     * Bind the {@link Note} value to this Request,
     * replacing any existing binding for this name.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @param note {@link Note} to which the object should be bound
     * @param value the {@link Note} value be bound to the specified {@link Note}.
     */
    public <E> void setNote(final Note<E> note, final E value) {
        request.setNote(note, value);
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

    /**
     * @return {@link HttpServerFilter}, which dispatched this request
     */
    public HttpServerFilter getHttpFilter() {
        return httpServerFilter;
    }
    
    /**
     * Returns the portion of the request URI that indicates the context of the request.
     * The context path always comes first in a request URI.
     * The path starts with a "/" character but does not end with a "/" character.
     * For {@link HttpHandler}s in the default (root) context, this method returns "".
     * The container does not decode this string.
     *
     * @return a String specifying the portion of the request URI that indicates the context of the request
     */
    public String getContextPath() {
        return contextPath.get();
    }

    protected void setContextPath(final String contextPath) {
        this.contextPath.setPath(contextPath);
    }
    
    protected void setContextPath(final PathResolver contextPath) {
        this.contextPath.setResolver(contextPath);
    }

    /**
     * Returns the part of this request's URL that calls the HttpHandler.
     * This includes either the HttpHandler name or a path to the HttpHandler,
     * but does not include any extra path information or a query string.
     * 
     * @return a String containing the name or path of the HttpHandler being
     * called, as specified in the request URL
     * @throws IllegalStateException if HttpHandler path was not set explicitly
     *          and attempt to URI-decode {@link org.glassfish.grizzly.http.util.RequestURIRef#getDecodedURI()}
     *          failed.
     */
    public String getHttpHandlerPath() {
        return httpHandlerPath.get();
    }

    protected void setHttpHandlerPath(final String httpHandlerPath) {
        this.httpHandlerPath.setPath(httpHandlerPath);
    }

    protected void setHttpHandlerPath(final PathResolver httpHandlerPath) {
        this.httpHandlerPath.setResolver(httpHandlerPath);
    }

    /**
     * Returns any extra path information associated with the URL the client
     * sent when it made this request.
     * The extra path information follows the HttpHandler path but precedes
     * the query string. This method returns null if there was no extra path
     * information.
     * 
     * @return a String specifying extra path information that comes after the
     * HttpHandler path but before the query string in the request URL;
     * or null if the URL does not have any extra path information
     */
    public String getPathInfo() {
        return pathInfo.get();
    }

    protected void setPathInfo(final String pathInfo) {
        this.pathInfo.setPath(pathInfo);
    }

    protected void setPathInfo(final PathResolver pathInfo) {
        this.pathInfo.setResolver(pathInfo);
    }
    
    // ------------------------------------------------- ServletRequest Methods

    /**
     * Return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     *
     * @param name Name of the request attribute to return
     * @return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     */
    public Object getAttribute(final String name) {
        if (SEND_FILE_ENABLED_ATTR.equals(name)) {
            return response.isSendFileEnabled();
        }
        Object attribute = request.getAttribute(name);

        if (attribute != null) {
            return attribute;
        }

        if (Globals.SSL_CERTIFICATE_ATTR.equals(name)) {
            attribute = RequestUtils.populateCertificateAttribute(this);

            if (attribute != null) {
                request.setAttribute(name, attribute);
            }
        } else if (isSSLAttribute(name)) {
            RequestUtils.populateSSLAttributes(this);

            attribute = request.getAttribute(name);
        } else if (Globals.DISPATCHER_REQUEST_PATH_ATTR.equals(name)) {
            return requestDispatcherPath;
        }

        return attribute;
    }


    /**
     * Test if a given name is one of the special Servlet-spec SSL attributes.
     */
    static boolean isSSLAttribute(final String name) {
        return Globals.CERTIFICATES_ATTR.equals(name)
                || Globals.CIPHER_SUITE_ATTR.equals(name)
                || Globals.KEY_SIZE_ATTR.equals(name);
    }

    /**
     * Return the names of all request attributes for this Request, or an
     * empty {@link Set} if there are none.
     */
    public Set<String> getAttributeNames() {
        return request.getAttributeNames();
    }


    /**
     * Return the character encoding for this Request.
     */
    public String getCharacterEncoding() {
      return request.getCharacterEncoding();
    }


    /**
     * Return the content length for this Request.
     */
    public int getContentLength() {
        return (int) request.getContentLength();
    }


    /**
     * Return the content length for this Request represented by Java long type.
     */
    public long getContentLengthLong() {
        return request.getContentLength();
    }

    /**
     * Return the content type for this Request.
     */
    public String getContentType() {
        return request.getContentType();
    }

    /**
     * <p>
     * Return the {@link InputStream} for this {@link Request}.
     * </p>
     * 
     * By default the returned {@link NIOInputStream} will work as blocking
     * {@link InputStream}, but it will be possible to call {@link NIOInputStream#isReady()},
     * {@link NIOInputStream#available()}, or {@link NIOInputStream#notifyAvailable(org.glassfish.grizzly.ReadHandler)}
     * to avoid blocking.
     *
     * @return the {@link NIOInputStream} for this {@link Request}.
     *
     * @exception IllegalStateException if {@link #getReader()} or
     *  {@link #getNIOReader()} has already been called for this request.
     *
     * @since 2.2
     */
    public InputStream getInputStream() {
        return getNIOInputStream();
    }

    /**
     * <p>
     * Return the {@link NIOInputStream} for this {@link Request}.   This stream
     * will not block when reading content.
     * </p>
     *
     * <p>
     * NOTE: For now, in order to use non-blocking functionality, this
     * method must be invoked before the {@link HttpHandler#service(Request, Response)}
     * method returns.  We hope to have this addressed in the next release.
     * </p>
     *
     * @return the {@link NIOInputStream} for this {@link Request}.
     *
     * @exception IllegalStateException if {@link #getReader()} or
     *  {@link #getNIOReader()} has already been called for this request.
     */
    public NIOInputStream getNIOInputStream() {
        if (usingReader)
            throw new IllegalStateException("Illegal attempt to call getInputStream() after getReader() has already been called.");

        usingInputStream = true;
        inputStream.setInputBuffer(inputBuffer);
        return inputStream;
    }


    /**
     * @return <code>true</code> if the current input source is operating in
     * non-blocking mode. In other words {@link #getNIOInputStream()} or
     *  {@link #getNIOReader()} were invoked.
     * @deprecated will always return true
     */
    public boolean asyncInput() {

        return inputBuffer.isAsyncEnabled();

    }


    /**
     * @return <code>true</code> if this request requires acknowledgment.
     */
    public boolean requiresAcknowledgement() {
        return request.requiresAcknowledgement();
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

        if (!locales.isEmpty()) {
            return (locales.get(0));
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
    public List<Locale> getLocales() {

        if (!localesParsed)
            parseLocales();

        if (!locales.isEmpty())
            return locales;

        final ArrayList<Locale> results = new ArrayList<Locale>();
        results.add(defaultLocale);
        return results;

    }

    /**
     * Returns the low-level parameters holder for finer control over parameters.
     * 
     * @return {@link Parameters}.
     */
    public Parameters getParameters() {
        return parameters;
    }
    
    /**
     * Return the value of the specified request parameter, if any; otherwise,
     * return <code>null</code>.  If there is more than one value defined,
     * return only the first one.
     *
     * @param name Name of the desired request parameter
     */
    public String getParameter(final String name) {

        if (!requestParametersParsed) {
            parseRequestParameters();
        }

        return parameters.getParameter(name);

    }



    /**
     * Returns a {@link java.util.Map} of the parameters of this request.
     * Request parameters are extra information sent with the request.
     * For HTTP servlets, parameters are contained in the query string
     * or posted form data.
     *
     * @return A {@link java.util.Map} containing parameter names as keys
     *  and parameter values as map values.
     */
    public Map<String,String[]> getParameterMap() {

        if (parameterMap.isLocked())
            return parameterMap;

        for (final String name : getParameterNames()) {
            final String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);

        return parameterMap;

    }


    /**
     * Return the names of all defined request parameters for this request.
     */
    public Set<String> getParameterNames() {

        if (!requestParametersParsed)
            parseRequestParameters();

        return parameters.getParameterNames();

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

        return parameters.getParameterValues(name);

    }


    /**
     * Return the protocol and version used to make this Request.
     */
    public Protocol getProtocol() {
        return request.getProtocol();
    }


    /**
     * <p>
     * Returns the {@link Reader} associated with this {@link Request}.
     * </p>
     * 
     * By default the returned {@link NIOReader} will work as blocking
     * {@link java.io.Reader}, but it will be possible to call {@link NIOReader#isReady()}
     * or {@link NIOReader#notifyAvailable(org.glassfish.grizzly.ReadHandler)}
     * to avoid blocking.
     * 
     * @return the {@link NIOReader} associated with this {@link Request}.
     *
     * @throws IllegalStateException if {@link #getInputStream()} or
     *  {@link #getNIOInputStream()} has already been called for this request.
     *
     * @since 2.2
     */
    public Reader getReader() {
        return getNIOReader();
    }

    /**
     * <p>
     * Returns the {@link NIOReader} associated with this {@link Request}.
     * This {@link NIOReader} will not block while reading content.
     * </p>
     *
     * @return {@link NIOReader}
     * @throws IllegalStateException if {@link #getInputStream()} or
     *  {@link #getNIOInputStream()} has already been called for this request.
     */
    public NIOReader getNIOReader() {
        if (usingInputStream)
            throw new IllegalStateException("Illegal attempt to call getReader() after getInputStream() has alread been called.");

        usingReader = true;
        inputBuffer.processingChars();
        reader.setInputBuffer(inputBuffer);
        return reader;
    }


    /**
     * Return the remote IP address making this Request.
     */
    public String getRemoteAddr() {
        return request.getRemoteAddress();
    }


    /**
     * Return the remote host name making this Request.
     */
    public String getRemoteHost() {
        return request.getRemoteHost();
    }

    /**
     * Returns the Internet Protocol (IP) source port of the client
     * or last proxy that sent the request.
     */
    public int getRemotePort(){
        return request.getRemotePort();
    }

    /**
     * Returns the host name of the Internet Protocol (IP) interface on
     * which the request was received.
     */
    public String getLocalName(){
       return request.getLocalName();
    }

    /**
     * Returns the Internet Protocol (IP) address of the interface on
     * which the request  was received.
     */
    public String getLocalAddr(){
        return request.getLocalAddress();
    }


    /**
     * Returns the Internet Protocol (IP) port number of the interface
     * on which the request was received.
     */
    public int getLocalPort(){
        return request.getLocalPort();
    }


    /**
     * Return the scheme used to make this Request.
     */
    public String getScheme() {
        return scheme;
    }


    /**
     * Return the server name responding to this Request.
     */
    public String getServerName() {
        return request.serverName().toString();
    }


    /**
     * Return the server port responding to this Request.
     */
    public int getServerPort() {
        return request.getServerPort();
    }


    /**
     * Was this request received on a secure connection?
     */
    public boolean isSecure() {
        return request.isSecure();
    }


    /**
     * Remove the specified request attribute if it exists.
     *
     * @param name Name of the request attribute to remove
     */
    public void removeAttribute(String name) {
        request.removeAttribute(name);
    }


    /**
     * Set the specified request attribute to the specified value.
     *
     * @param name Name of the request attribute to set
     * @param value The associated value
     */
    public void setAttribute(final String name, final Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException("Argument 'name' cannot be null");

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

        request.setAttribute(name, value);

        if (response.isSendFileEnabled() && SEND_FILE_ATTR.equals(name)) {
            RequestUtils.handleSendFile(this);
        }

    }


    /**
     * Overrides the name of the character encoding used in the body of this
     * request.
     *
     * This method must be called prior to reading request parameters or
     * reading input using <code>getReader()</code>. Otherwise, it has no
     * effect.
     *
     * @param encoding      <code>String</code> containing the name of
     *                 the character encoding.
     * @throws         java.io.UnsupportedEncodingException if this
     *                 ServletRequest is still in a state where a
     *                 character encoding may be set, but the specified
     *                 encoding is invalid
     *
     * @since Servlet 2.3
     */
    @SuppressWarnings({"unchecked"})
    public void setCharacterEncoding(final String encoding)
        throws UnsupportedEncodingException {

        // START SJSAS 4936855
        if (requestParametersParsed || usingReader) {
            return;
        }
        // END SJSAS 4936855

        Charsets.lookupCharset(encoding);

        // Save the validated encoding
        request.setCharacterEncoding(encoding);

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
        parameters.addParameterValues(name, values);
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
     * Get the decoded request URI.
     *
     * @return the URL decoded request URI
     */
    public String getDecodedRequestURI() throws CharConversionException {
        return request.getRequestURIRef().getDecodedURI();
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
        return request.authType().toString();
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
     * Set the set of cookies received with this Request.
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

        final SimpleDateFormats formats = SimpleDateFormats.create();

        try {
            // Attempt to convert the date header in a variety of formats
            long result = FastHttpDateFormat.parseDate(value, formats.getFormats());
            if (result != (-1L)) {
                return result;
            }
            throw new IllegalArgumentException(value);
        } finally {
            formats.recycle();
        }
    }

    /**
     * Return the value of the specified date header, if any; otherwise
     * return -1.
     *
     * @param header the requested date {@link Header}
     *
     * @exception IllegalArgumentException if the specified header value
     *  cannot be converted to a date
     *
     *  @since 2.1.2
     */
    public long getDateHeader(Header header) {

        String value = getHeader(header);
        if (value == null)
            return (-1L);

        final SimpleDateFormats formats = SimpleDateFormats.create();

        try {
            // Attempt to convert the date header in a variety of formats
            long result = FastHttpDateFormat.parseDate(value, formats.getFormats());
            if (result != (-1L)) {
                return result;
            }
            throw new IllegalArgumentException(value);
        } finally {
            formats.recycle();
        }
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
     * Return the first value of the specified header, if any; otherwise,
     * return <code>null</code>
     *
     * @param header the requested {@link Header}
      *
      * @since 2.1.2
     */
    public String getHeader(final Header header) {
        return request.getHeader(header);
    }


    /**
     * Return all of the values of the specified header, if any; otherwise,
     * return an empty enumeration.
     *
     * @param name Name of the requested header
     */
    public Iterable<String> getHeaders(String name) {
        return request.getHeaders().values(name);
    }

    /**
     * Return all of the values of the specified header, if any; otherwise,
     * return an empty enumeration.
     *
     * @param header the requested {@link Header}
     *
     * @since 2.1.2
     */
    public Iterable<String> getHeaders(final Header header) {
        return request.getHeaders().values(header);
    }


    /**
     * Return the names of all headers received with this request.
     */
    public Iterable<String> getHeaderNames() {
        return request.getHeaders().names();
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
            return -1;
        } else {
            return Integer.parseInt(value);
        }

    }

    /**
     * Return the value of the specified header as an integer, or -1 if there
     * is no such header for this request.
     *
     * @param header the requested {@link Header}
     *
     * @exception IllegalArgumentException if the specified header value
     *  cannot be converted to an integer
     *
     *  @since 2.1.2
     */
    public int getIntHeader(final Header header) {

        String value = getHeader(header);
        if (value == null) {
            return -1;
        } else {
            return Integer.parseInt(value);
        }

    }


    /**
     * Return the HTTP request method used in this Request.
     */
    public Method getMethod() {
        return request.getMethod();
    }

    /**
     * Sets the HTTP request method used in this Request.
     * @param method the HTTP request method used in this Request.
     */
    public void setMethod(String method) {
        request.setMethod(method);
    }


    /**
     * @return the query string associated with this request.
     */
    public String getQueryString() {
        final String queryString = request.getQueryStringDC().toString(
                parameters.getQueryStringEncoding());
        
        return queryString == null || queryString.isEmpty()
                ? null
                : queryString;
    }

    /**
     * Sets the query string associated with this request.
     * @param queryString the query string associated with this request.
     */
    public void setQueryString(String queryString) {
        request.setQueryString(queryString);
    }

    /**
     * Return the name of the remote user that has been authenticated
     * for this Request.
     */
    public String getRemoteUser() {

        if (userPrincipal != null) {
            return userPrincipal.getName();
        } else {
            return null;
        }

    }


    /**
     * Return the session identifier included in this request, if any.
     */
    public String getRequestedSessionId() {
        return requestedSessionId;
    }


    /**
     * Return the request URI for this request.
     */
    public String getRequestURI() {
        return request.getRequestURI();
    }

    /**
     * Sets the request URI for this request.
     * @param uri the request URI for this request.
     */
    public void setRequestURI(String uri) {
        request.setRequestURI(uri);
    }

    /**
     * Reconstructs the URL the client used to make the request.
     * The returned URL contains a protocol, server name, port
     * number, and server path, but it does not include query
     * string parameters.
     * <p>
     * Because this method returns a <code>StringBuilder</code>,
     * not a <code>String</code>, you can modify the URL easily,
     * for example, to append query parameters.
     * <p>
     * This method is useful for creating redirect messages and
     * for reporting errors.
     *
     * @return A <code>StringBuffer</code> object containing the
     *  reconstructed URL
     */
    public StringBuilder getRequestURL() {

        final StringBuilder url = new StringBuilder();
        return appendRequestURL(this, url);

    }

    /**
     * Appends the reconstructed URL the client used to make the request.
     * The appended URL contains a protocol, server name, port
     * number, and server path, but it does not include query
     * string parameters.
     * <p>
     * Because this method returns a <code>StringBuilder</code>,
     * not a <code>String</code>, you can modify the URL easily,
     * for example, to append query parameters.
     * <p>
     * This method is useful for creating redirect messages and
     * for reporting errors.
     *
     * @return A <code>StringBuilder</code> object containing the appended
     *  reconstructed URL
     */
    public static StringBuilder appendRequestURL(final Request request,
            final StringBuilder buffer) {

        final String scheme = request.getScheme();
        int port = request.getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        buffer.append(scheme);
        buffer.append("://");
        buffer.append(request.getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            buffer.append(':');
            buffer.append(port);
        }
        buffer.append(request.getRequestURI());
        return buffer;

    }

    /**
     * Appends the reconstructed URL the client used to make the request.
     * The appended URL contains a protocol, server name, port
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
     * @return A <code>StringBuffer</code> object containing the appended
     *  reconstructed URL
     */
    public static StringBuffer appendRequestURL(final Request request,
            final StringBuffer buffer) {

        final String scheme = request.getScheme();
        int port = request.getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        buffer.append(scheme);
        buffer.append("://");
        buffer.append(request.getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            buffer.append(':');
            buffer.append(port);
        }
        buffer.append(request.getRequestURI());
        return buffer;

    }

    /**
     * Return the principal that has been authenticated for this Request.
     */
    public Principal getUserPrincipal() {
        if (userPrincipal == null) {
            if (getRequest().isSecure()) {
                X509Certificate certs[] = (X509Certificate[]) getAttribute(
                        Globals.CERTIFICATES_ATTR);
                if (FORCE_CLIENT_AUTH_ON_GET_USER_PRINCIPAL &&
                        ((certs == null) || (certs.length < 1))) {
                    // Force SSL re-handshake and request client auth
                    certs = (X509Certificate[]) getAttribute(
                            Globals.SSL_CERTIFICATE_ATTR);
                }
                
                if (certs != null && certs.length > 0) {
                    userPrincipal = certs[0].getSubjectX500Principal();
                }
            }
        }

        return userPrincipal;
    }

    public FilterChainContext getContext() {
        return ctx;
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

        final Cookies serverCookies = getRawCookies();
        cookies = serverCookies.get();

    }


    /**
     * @return the {@link InputBuffer} associated with this request, which is the
     * source for {@link #getInputStream()}, {@link #getReader()},
     * {@link #getNIOInputStream()}, and {@link #getNIOReader()}
     */
    public InputBuffer getInputBuffer() {

        return inputBuffer;

    }


    /**
     * This method may be used if some other entity processed request parameters
     * and wishes to expose them via the request.  When this method is called,
     * it will mark the internal request parameter state as having been processed.
     *
     * @param parameters the parameters to expose via this request.
     *
     * @since 2.2
     */
    public void setRequestParameters(final Parameters parameters) {

        this.requestParametersParsed = true;
        for (final String name : parameters.getParameterNames()) {
            this.parameters.addParameterValues(name,
                                               parameters.getParameterValues(name));
        }

    }


    /**
     * TODO DOCS
     */
    protected Cookies getRawCookies() {
        if (rawCookies == null) {
            rawCookies = new Cookies();
        }
        if (!rawCookies.initialized()) {
            rawCookies.setHeaders(request.getHeaders());
        }

        return rawCookies;
    }


    /**
     * Parse request parameters.
     */
    protected void parseRequestParameters() {

        // Delay updating requestParametersParsed to TRUE until
        // after getCharacterEncoding() has been called, because
        // getCharacterEncoding() may cause setCharacterEncoding() to be
        // called, and the latter will ignore the specified encoding if
        // requestParametersParsed is TRUE
        requestParametersParsed = true;

        Charset charset = null;

        if (parameters.getEncoding() == null) {
            // getCharacterEncoding() may have been overridden to search for
            // hidden form field containing request encoding
            charset = lookupCharset(getCharacterEncoding());
            
            parameters.setEncoding(charset);
        }
        
        if (parameters.getQueryStringEncoding() == null) {
            if (charset == null) {
                // getCharacterEncoding() may have been overridden to search for
                // hidden form field containing request encoding
                charset = lookupCharset(getCharacterEncoding());
            }
            
            parameters.setQueryStringEncoding(charset);
        }

        parameters.handleQueryParameters();

        if (usingInputStream || usingReader) {
            return;
        }

        if (!Method.POST.equals(getMethod())) {
            return;
        }

        if (!checkPostContentType(getContentType())) return;

        final int maxFormPostSize =
                httpServerFilter.getConfiguration().getMaxFormPostSize();

        int len = getContentLength();
        if (len < 0) {
            if (!request.isChunked()) {
                return;
            }

            len = maxFormPostSize;
        }

        if ((maxFormPostSize > 0) && (len > maxFormPostSize)) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning(LogMessages.WARNING_GRIZZLY_HTTP_SERVER_REQUEST_POST_TOO_LARGE());
            }

            throw new IllegalStateException(LogMessages.WARNING_GRIZZLY_HTTP_SERVER_REQUEST_POST_TOO_LARGE());
        }

        int read = 0;
        try {
            final Buffer formData = getPostBody(len);
            read = formData.remaining();
            parameters.processParameters(formData, formData.position(), read);
        } catch (Exception ignored) {
        } finally {
            try {
                skipPostBody(read);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_HTTP_SERVER_REQUEST_BODY_SKIP(), e);
            }
        }

    }

    private Charset lookupCharset(final String enc) {
        Charset charset;
        if (enc != null) {
            try {
                charset = Charsets.lookupCharset(enc);
            } catch (Exception e) {
                charset = org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET;
            }
        } else {
            charset = org.glassfish.grizzly.http.util.Constants.DEFAULT_HTTP_CHARSET;
        }
        
        return charset;
    }
    
    private boolean checkPostContentType(final String contentType) {
        return ((contentType != null)
                  && contentType.trim().startsWith(FORM_POST_CONTENT_TYPE));

    }

    /**
     * Gets the POST body of this request.
     *
     * @return The POST body of this request
     */
    public Buffer getPostBody(final int len) throws IOException {
        inputBuffer.fillFully(len);
        return inputBuffer.getBuffer();
    }

    /**
     * Skips the POST body of this request.
     *
     * @param len how much of the POST body to skip.
     */
    protected void skipPostBody(final int len) throws IOException {
        inputBuffer.skip(len);
    }

    /**
     * Parse request locales.
     */
    protected void parseLocales() {

        localesParsed = true;

        final Iterable<String> values = getHeaders("accept-language");

        for (String value : values) {
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
        TreeMap<Double,List<Locale>> localLocalesMap = new TreeMap<Double,List<Locale>>();

        // Preprocess the value to remove all whitespace
        int white = value.indexOf(' ');
        if (white < 0)
            white = value.indexOf('\t');
        if (white >= 0) {
            int len = value.length();
            StringBuilder sb = new StringBuilder(len - 1);
            for (int i = 0; i < len; i++) {
                char ch = value.charAt(i);
                if ((ch != ' ') && (ch != '\t'))
                    sb.append(ch);
            }
            value = sb.toString();
        }

        if (parser == null) {
            parser = new StringParser();
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

            Locale locale = localeParser.parseLocale(entry);
            if (locale == null) {
                continue;
            }

            // Add a new Locale to the list of Locales for this quality level
            Double key = -quality;  // Reverse the order
            List<Locale> values = localLocalesMap.get(key);
            if (values == null) {
                values = new ArrayList<Locale>();
                localLocalesMap.put(key, values);
            }
            values.add(locale);

        }

        // Process the quality values in highest->lowest order (due to
        // negating the Double value when creating the key)
        for (List<Locale> localLocales: localLocalesMap.values()) {
            for (Locale locale : localLocales) {
                addLocale(locale);
            }
        }

    }

    /**
     * Parses the value of the JROUTE cookie, if present.
     */
    void parseJrouteCookie() {
        if (!cookiesParsed) {
            parseCookies();
        }

        final Cookie cookie = getRawCookies().findByName(Constants.JROUTE_COOKIE);
        if (cookie != null) {
            setJrouteId(cookie.getValue());
        }
    }

    /*
     * @return <code>true</code> if the given string is composed of
     *  upper- or lowercase letters only, <code>false</code> otherwise.
     */
    static boolean isAlpha(String value) {

        if (value == null) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) {
                return false;
            }
        }

        return true;
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

    // ------------------------------------------------------ Session support --/


    /**
     * Return the session associated with this Request, creating one
     * if necessary.
     */
    public Session getSession() {
        return doGetSession(true);
    }


    /**
     * Return the session associated with this Request, creating one
     * if necessary and requested.
     *
     * @param create Create a new session if one does not exist
     */
    public Session getSession(final boolean create) {
        return doGetSession(create);
    }

    /**
     * Change the session id of the current session associated with this
     * request and return the new session id. 
     *
     * @return the original session id
     *
     * @throws IllegalStateException if there is no session associated
     * with the request
     *
     * @since 2.3
     */
    public String changeSessionId() {
        Session sessionLocal = doGetSession(false);
        if (sessionLocal == null) {
            throw new IllegalStateException("changeSessionId has been called without a session");
        }

        String oldSessionId = getSessionManager().changeSessionId(this, sessionLocal);
        final String newSessionId = sessionLocal.getIdInternal();
        requestedSessionId = newSessionId;

        if (isRequestedSessionIdFromURL())
            return oldSessionId;

        if (response != null) {
            final Cookie cookie = new Cookie(obtainSessionCookieName(),
                                             newSessionId);
            configureSessionCookie(cookie);
            response.addSessionCookieInternal(cookie);
        }

        return oldSessionId;
    }

    protected Session doGetSession(final boolean create) {
        // Return the current session if it exists and is valid
        if (session != null && session.isValid()) {
            return session;
        }

        session = null;

        if (requestedSessionId == null) {
            final Cookie[] cookiesLocale = getCookies();
            assert cookiesLocale != null;
            
            final String sessionCookieNameLocal = obtainSessionCookieName();
            for (int i = 0; i < cookiesLocale.length; i++) {
                final Cookie c = cookiesLocale[i];
                if (sessionCookieNameLocal.equals(c.getName())) {
                    setRequestedSessionId(c.getValue());
                    setRequestedSessionCookie(true);
                    break;
                }
            }
        }

        session = getSessionManager().getSession(this, requestedSessionId);
        if (session != null && !session.isValid()) {
            session = null;
        }
        
        if (session != null) {
            session.access();
            return session;
        }
        
        if (!create) {
            return null;
        }
        
        session = getSessionManager().createSession(this);
        session.setSessionTimeout(
                httpServerFilter.getConfiguration().getSessionTimeoutSeconds() * 1000);
        requestedSessionId = session.getIdInternal();

        // Creating a new session cookie based on the newly created session
        final Cookie cookie = new Cookie(obtainSessionCookieName(),
                                         session.getIdInternal());
        configureSessionCookie(cookie);
        
        response.addCookie(cookie);
        
        return session;
    }

    /**
     * @return <code>true</code> if the session identifier included in this
     * request came from a cookie.
     */
    public boolean isRequestedSessionIdFromCookie() {

        return requestedSessionId != null && requestedSessionCookie;

    }

    /**
     * Return <code>true</code> if the session identifier included in this
     * request came from the request URI.
     */
    public boolean isRequestedSessionIdFromURL() {

        return requestedSessionId != null && requestedSessionURL;

    }

    /**
     * @return <tt>true</tt> if the session identifier included in this
     * request identifies a valid session.
     */
    public boolean isRequestedSessionIdValid() {
        if (requestedSessionId == null) {
            return false;
        }

        if (session != null
                && requestedSessionId.equals(session.getIdInternal())) {
            return session.isValid();
        }

        final Session localSession =
                getSessionManager().getSession(this, requestedSessionId);
        return localSession != null && localSession.isValid();

    }

    /**
     * Configures the given session cookie.
     *
     * @param cookie The session cookie to be configured
     */
    protected void configureSessionCookie(final Cookie cookie) {
        cookie.setMaxAge(-1);
        cookie.setPath("/");

        if (isSecure()) {
            cookie.setSecure(true);
        }
        
        getSessionManager().configureSessionCookie(this, cookie);
    }

    /**
     * Parse session id in URL.
     */
    protected void parseSessionId() {
        if (sessionParsed) return;

        sessionParsed = true;
        final DataChunk uriDC = request.getRequestURIRef().getRequestURIBC();
        
        final boolean isUpdated;
        
        switch (uriDC.getType()) {
            case Bytes:
                isUpdated = parseSessionId(uriDC.getByteChunk());
                break;
            case Buffer:
                isUpdated = parseSessionId(uriDC.getBufferChunk());
                break;
            case Chars:
                isUpdated = parseSessionId(uriDC.getCharChunk());
                break;
            case String:
                isUpdated = parseSessionId(uriDC);
                break;
            default:
                throw new IllegalStateException("Unexpected DataChunk type: " + uriDC.getType());
        }
        
        if (isUpdated) {
            uriDC.notifyDirectUpdate();
        }
    }


    private boolean parseSessionId(final Chunk uriChunk) {
        final String sessionParamNameMatch = sessionCookieName != null
                ? ';' + sessionCookieName + '='
                : match;
        
        boolean isUpdated = false;
        final int semicolon = uriChunk.indexOf(sessionParamNameMatch, 0);

        if (semicolon > 0) {
            // Parse session ID, and extract it from the decoded request URI
//            final int start = uriChunk.getStart();

            final int sessionIdStart = semicolon + sessionParamNameMatch.length();
            final int semicolon2 = uriChunk.indexOf(';', sessionIdStart);
            
            isUpdated = semicolon2 >= 0;
            final int end = isUpdated ? semicolon2 : uriChunk.getLength();

            final String sessionId = uriChunk.toString(sessionIdStart, end);

            final int jrouteIndex = sessionId.lastIndexOf(':');
            if (jrouteIndex > 0) {
                setRequestedSessionId(sessionId.substring(0, jrouteIndex));
                if (jrouteIndex < (sessionId.length() - 1)) {
                    setJrouteId(sessionId.substring(jrouteIndex + 1));
                }
            } else {
                setRequestedSessionId(sessionId);
            }

            setRequestedSessionURL(true);

            uriChunk.delete(semicolon, end);
        } else {
            setRequestedSessionId(null);
            setRequestedSessionURL(false);
        }
        
        return isUpdated;
    }

    private boolean parseSessionId(DataChunk dataChunkStr) {
        assert dataChunkStr.getType() == DataChunk.Type.String;
        
        final String uri = dataChunkStr.toString();
        final String sessionParamNameMatch = sessionCookieName != null
                ? ';' + sessionCookieName + '='
                : match;
        
        boolean isUpdated = false;
        final int semicolon = uri.indexOf(sessionParamNameMatch);

        if (semicolon > 0) {
            // Parse session ID, and extract it from the decoded request URI
//            final int start = uriChunk.getStart();

            final int sessionIdStart = semicolon + sessionParamNameMatch.length();
            final int semicolon2 = uri.indexOf(';', sessionIdStart);
            
            isUpdated = semicolon2 >= 0;
            final int end = isUpdated ? semicolon2 : uri.length();

            final String sessionId = uri.substring(sessionIdStart, end);

            final int jrouteIndex = sessionId.lastIndexOf(':');
            if (jrouteIndex > 0) {
                setRequestedSessionId(sessionId.substring(0, jrouteIndex));
                if (jrouteIndex < (sessionId.length() - 1)) {
                    setJrouteId(sessionId.substring(jrouteIndex + 1));
                }
            } else {
                setRequestedSessionId(sessionId);
            }

            setRequestedSessionURL(true);

            dataChunkStr.setString(uri.substring(0, semicolon));
        } else {
            setRequestedSessionId(null);
            setRequestedSessionURL(false);
        }
        
        return isUpdated;
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
    
    private static class PathData {
        private final Request request;
        private String path;
        private PathResolver resolver;

        public PathData(Request request) {
            this.request = request;
        }

        public PathData(final Request request, final String path,
                final PathResolver resolver) {
            this.request = request;
            this.path = path;
            this.resolver = resolver;
        }
        
        public void setPath(final String path) {
            this.path = path;
            resolver = null;
        }

        public void setResolver(final PathResolver resolver) {
            this.resolver = resolver;
            path = null;
        }

        public String get() {
            return path != null ? path :
                    (resolver != null ? (path = resolver.resolve(request)) : null);
        }
        
        public void reset() {
            path = null;
            resolver = null;
        }
    }
    
    protected interface PathResolver {
        String resolve(Request request);
    }
}
