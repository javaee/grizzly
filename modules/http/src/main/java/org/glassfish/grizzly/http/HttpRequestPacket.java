/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpCodecUtils;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http.util.RequestURIRef;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.DefaultAttributeBuilder;

/**
 * The {@link HttpHeader} object, which represents HTTP request message.
 *
 * @see HttpHeader
 * @see HttpResponsePacket
 * 
 * @author Alexey Stashok
 */
public abstract class HttpRequestPacket extends HttpHeader {
    /**
     * Prefix for all service/read-only attributes, that once added could not
     * be removed. The attributes with this prefix will not be listed in the
     * {@link #getAttributeNames()} result.
     * The prefix was introduced with the intention to avoid collisions with
     * normal user attributes and fail fast when we compare read-only and
     * normal user attributes.
     */
    public static final String READ_ONLY_ATTR_PREFIX = "@RoA.";
    
    private static final AttributeBuilder ATTR_BUILDER =
            new DefaultAttributeBuilder();

    // ----------------------------------------------------- Instance Variables

    private Connection connection;

    private HttpResponsePacket response;

    private int serverPort = -1;
    protected int remotePort = -1;
    protected int localPort = -1;

    private final RequestURIRef requestURIRef = new RequestURIRef();

    private String localHost;

    private final DataChunk methodC = DataChunk.newInstance();
    protected Method parsedMethod;

    private final DataChunk queryC = DataChunk.newInstance();
    protected final DataChunk remoteAddressC = DataChunk.newInstance();
    protected final DataChunk remoteHostC = DataChunk.newInstance();
    protected final DataChunk localNameC = DataChunk.newInstance();
    protected final DataChunk localAddressC = DataChunk.newInstance();
    private final DataChunk serverNameC = DataChunk.newInstance();

    /**
     * Authentication type.
     */
    private final DataChunk authTypeC = DataChunk.newInstance();
    private final DataChunk remoteUserC = DataChunk.newInstance();

    private boolean requiresAcknowledgement;

    protected DataChunk unparsedHostC;
    private boolean hostHeaderParsed;

    /**
     * Internal notes associated with this request by Catalina components
     * and event listeners.
     */
    private final transient AttributeHolder notesHolder =
            ATTR_BUILDER.createUnsafeAttributeHolder();

    /**
     * The attributes associated with this Request, keyed by attribute name.
     */
    protected final Map<String, Object> attributes = new HashMap<String, Object>();

    /**
     * Returns {@link HttpRequestPacket} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // ----------------------------------------------------------- Constructors


    protected HttpRequestPacket() {
        setMethod(Method.GET);
    }


    // ---------------------------------------------------------- Public Methods


    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public Connection getConnection() {
        return connection;
    }

    public HttpResponsePacket getResponse() {
        return response;
    }

    // -------------------- Request data --------------------


    /**
     * Get the HTTP request method as {@link DataChunk}
     * (avoiding creation of a String object). The result format is "GET|POST...".
     *
     * @return the HTTP request method as {@link DataChunk}
     * (avoiding creation of a String object). The result format is "GET|POST...".
     */
    public DataChunk getMethodDC() {
        // potentially the value might be changed, so we need to parse it again
        parsedMethod = null;
        return methodC;
    }

    /**
     * Get the HTTP request method.
     *
     * @return the HTTP request method.
     */
    public Method getMethod() {
        if (parsedMethod != null) {
            return parsedMethod;
        }

        parsedMethod = Method.valueOf(methodC);

        return parsedMethod;
    }

    /**
     * Set the HTTP request method.
     * @param method the HTTP request method. Format is "GET|POST...".
     */
    public void setMethod(final String method) {
        this.methodC.setString(method);
        parsedMethod = null;
    }

    /**
     * Set the HTTP request method.
     * @param method the HTTP request method. Format is "GET|POST...".
     */
    public void setMethod(final Method method) {
        this.methodC.setString(method.getMethodString());
        parsedMethod = method;
    }

    /**
     * Returns the request URL of the HTTP request as {@link RequestURIRef}
     * (avoiding creation of a String object).
     * 
     * @return the request URL of the HTTP request as {@link RequestURIRef}
     * (avoiding creation of a String object).
     */
    public RequestURIRef getRequestURIRef() {
        return requestURIRef;
    }

    /**
     * Returns the request URL.
     *
     * @return the request URL.
     */
    public String getRequestURI() {
        return requestURIRef.getURI();
    }

    /**
     * Set the request URL.
     *
     * @param requestURI the request URL.
     */
    public void setRequestURI(String requestURI) {
        this.requestURIRef.setURI(requestURI);
    }

    /**
     * Returns the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     * The result is represented as {@link DataChunk} (avoiding creation of a
     * String object).
     * 
     * @return the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     * The result is represented as {@link DataChunk} (avoiding creation of a
     * String object).
     */
    public DataChunk getQueryStringDC() {
        return queryC;
    }

    /**
     * Returns the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     *
     * @return the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     */
    public String getQueryString() {
        return ((queryC.isNull()) ? null : queryC.toString());
    }

    /**
     * Set the <code>query</code> portion of the request URI.
     *
     * @param query the query String
     */
    public void setQueryString(String query) {
        queryC.setString(query);
    }

    
    /**
     * Return the buffer holding the server name, if
     * any. Use isNull() to check if there is no value
     * set.
     * This is the "virtual host", derived from the
     * Host: header.
     * 
     * @return the buffer holding the server name
     */
    protected DataChunk serverNameRaw() {
        return serverNameC;
    }
    
    /**
     * Return the buffer holding the server name, if
     * any. Use isNull() to check if there is no value
     * set.
     * This is the "virtual host", derived from the
     * Host: header.
     * @return the buffer holding the server name, if
     * any
     */
    public DataChunk serverName() {
        parseHostHeader();
        return serverNameC;
    }




    /**
     * @return Returns the integer value of the Internet Protocol (IP) port as
     *  specified in the <code>Host</code> request header.
     */
    public int getServerPort() {
        parseHostHeader();
        return serverPort;
    }

    /**
     * Sets the Internet Protocol (IP) port specified in the
     *  <code>Host</code> request header.
     *
     * @param serverPort the port as specified in the <code>Host</code>
     *  request header
     */
    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * @return the {@link DataChunk} representing the Internet Protocol (IP)
     *  address of the client or last proxy that sent the request.
     */
    public DataChunk remoteAddr() {
        if (remoteAddressC.isNull()) {
            remoteAddressC
                  .setString(((InetSocketAddress) connection.getPeerAddress())
                        .getAddress().getHostAddress());
        }
        return remoteAddressC;

    }


    /**
      * @return the Internet Protocol (IP) address of the client or last proxy
     *   that sent the request.
     */
    public String getRemoteAddress() {
        return remoteAddr().toString();
    }


    /**
     * @return a {@link DataChunk} representing the fully qualified
     *  name of the client or the last proxy that sent the request. If the
     *  engine cannot or chooses not to resolve the host name (to improve
     *  performance), this method returns the the IP address.
     */
    public DataChunk remoteHost() {
         if ((remoteHostC.isNull())) {
            String remoteHost = null;
            InetAddress inetAddr = ((InetSocketAddress) connection
                  .getPeerAddress()).getAddress();
            if (inetAddr != null) {
                remoteHost = inetAddr.getHostName();
            }

            if (remoteHost == null) {
                if (!remoteAddressC.isNull()) {
                    remoteHost = remoteAddressC.toString();
                } else { // all we can do is punt
                    remoteHostC.recycle();
                }
            }
            remoteHostC.setString(remoteHost);
        }
        return remoteHostC;
    }


    /**
     * @return a <code>String</code> representing the fully qualified name of
     *  the client or the last proxy that sent the request. If the engine cannot
     *  or chooses not to resolve the hostname (to improve performance), this
     *  method returns the the IP address.
     */
    public String getRemoteHost() {

        return remoteHost().toString();
        
    }


    /**
     * Allows consumers of this request to be notified if the user-agent
     * requires acknowledgment of an expectation (i.e., the Expect header).
     *
     * @param requiresAcknowledgement <code>true</code> if expectation
     *  processing is required.
     */
    protected void requiresAcknowledgement(boolean requiresAcknowledgement) {
        this.requiresAcknowledgement = requiresAcknowledgement;
    }


    /**
     * @return <code>true</code> if this request requires acknowledgement.
     */
    public boolean requiresAcknowledgement() {
        return requiresAcknowledgement;
    }


    /**
     * @return a {@link DataChunk} representing the host name of the
     *  Internet Protocol (IP) interface on which the request was received.
     */
    public DataChunk localName() {

        if (localNameC.isNull()) {
            InetAddress inetAddr = ((InetSocketAddress) connection
                  .getLocalAddress()).getAddress();
            localNameC.setString(inetAddr.getHostName());
        }
        return localNameC;
        
    }


    /**
     * @return a <code>String</code> representing the host name of the 
     *  Internet Protocol (IP) interface on which the request was received.
     */
    public String getLocalName() {

        return localName().toString();

    }


    /**
     * @return a {@link DataChunk} representing the Internet Protocol (IP)
     *  address of the interface on which the request was received.
     */
    public DataChunk localAddr() {
        if (localAddressC.isNull()) {
            InetAddress inetAddr = ((InetSocketAddress) connection
                  .getLocalAddress()).getAddress();
            localAddressC.setString(inetAddr.getHostAddress());
        }
        return localAddressC;
    }


    /**
     * @return a <code>String</code> representing the Internet Protocol (IP)
     *  address of the interface on which the request was received.
     */
    public String getLocalAddress() {

        return localAddr().toString();

    }


    /**
     * @return the Internet Protocol (IP) source port of the client or last
     *  proxy that sent the request.
     */
    public int getRemotePort() {
        if (remotePort == -1) {
            remotePort = ((InetSocketAddress) connection.getPeerAddress()).getPort();
        }
        return remotePort;
    }


    /**
     * Sets the Internet Protocol (IP) source port of the client or last
     * proxy that sent the request.
     *
     * @param port the source port of the client
     */
    public void setRemotePort(int port) {
        this.remotePort = port;
    }


    /**
     * @return the Internet Protocol (IP) port number of the interface on which
     *  the request was received.
     */
    public int getLocalPort() {
        if (localPort == -1) {
            localPort = ((InetSocketAddress) connection.getLocalAddress()).getPort();
        }
        return localPort;
    }


    /**
     * Sets the Internet Protocol (IP) port number of the interface on which
     * the request was received.
     *
     * @param port the port on which the request was received
     */
    public void setLocalPort(int port) {
        this.localPort = port;
    }


    /**
     * @return the host name of the server servicing this request.
     */
    public String getLocalHost() {
        return localHost;
    }


    /**
     * Set the host name of the server servicing this request.
     * @param host the host name of the server servicing this request.
     */
    public void setLocalHost(String host) {
        this.localHost = host;
    }

    /**
     * @return the authentication type used for this Request.
     */
     public DataChunk authType() {
         return authTypeC;
     }

    /**
     * @return the name of the remote user that has been authenticated
     * for this Request.
     */
     public DataChunk remoteUser() {
         return remoteUserC;
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
        return new Note(ATTR_BUILDER.createAttribute(name));
    }

    /**
     * Return the {@link Note} value associated with this <tt>Request</tt>,
     * or <code>null</code> if no such binding exists.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @param <E> the {@link Note} type.
     * @param note {@link Note} value to be returned
     * @return the {@link Note} value associated with this <tt>Request</tt>,
     * or <code>null</code> if no such binding exists
     */
    public <E> E getNote(final Note<E> note) {
        return note.attribute.get(notesHolder);
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
        return notesHolder.getAttributeNames();
    }


    /**
     * Remove the {@link Note} value associated with this request.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @param <E> the {@link Note} type.
     * @param note {@link Note} value to be removed
     * @return the old value associated with the {@link Node}, that was removed
     */
    public <E> E removeNote(final Note<E> note) {
        return note.attribute.remove(notesHolder);
    }


    /**
     * Bind the {@link Note} value to this Request,
     * replacing any existing binding for this name.
     * Use {@link #createNote(java.lang.String)} to create a new {@link Note}.
     *
     * @param <E> the {@link Note} type.
     * @param note {@link Note} to which the object should be bound
     * @param value the {@link Note} value be bound to the specified {@link Note}.
     */
    public <E> void setNote(final Note<E> note, final E value) {
        note.attribute.set(notesHolder, value);
    }

    /**
     * @return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     *
     * @param name Name of the request attribute to return
     */
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }


    /**
     * @return read-only {@link Set} of the names of all request attributes,
     * or an empty {@link Set} if there are none.
     */
    public Set<String> getAttributeNames() {
        final Set<String> attrNames = new HashSet<String>(attributes.size());
        for (String name : attributes.keySet()) {
            if (name == null || !name.startsWith(READ_ONLY_ATTR_PREFIX)) {
                attrNames.add(name);
            }
        }
        
        return Collections.unmodifiableSet(attrNames);
    }

    /**
     * Set the specified request attribute to the specified value.
     *
     * @param name Name of the request attribute to set
     * @param value The associated value
     */
    public void setAttribute(final String name, final Object value) {
        final Object oldValue = attributes.put(name, value);
        // make sure we don't overwrite read-only attribute
        if (oldValue != null &&
                name != null && name.startsWith(READ_ONLY_ATTR_PREFIX)) {
            // restore the original value for read-only attribute
            attributes.put(name, oldValue);
        }
    }

    /**
     * Remove the specified request attribute if it exists.
     *
     * @param name Name of the request attribute to remove
     */
    public void removeAttribute(final String name) {
        if (name == null || !name.startsWith(READ_ONLY_ATTR_PREFIX)) {
            attributes.remove(name);
        }
    }

    /**
     * Returns <code>true</code> if this request is a <code>HEAD</code>
     *  request, otherwise returns <code>false</code>.
     *
     * @return <code>true</code> if this request is a <code>HEAD</code>
     *  request, otherwise returns <code>false</code>.
     */
    public boolean isHeadRequest() {
        return (Method.HEAD.equals(getMethod()));
    }

    // -------------------- Recycling --------------------

    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        requestURIRef.recycle();
        queryC.recycle();

        methodC.recycle();
        parsedMethod = null;

        hostHeaderParsed = false;
        unparsedHostC = null;
        
        remoteAddressC.recycle();
        remoteHostC.recycle();
        localAddressC.recycle();
        localNameC.recycle();
        serverNameC.recycle();

        authTypeC.recycle();
        remoteUserC.recycle();
        
        attributes.clear();

        requiresAcknowledgement = false;

        remotePort = -1;
        localPort = -1;
        serverPort = -1;

        connection = null;
        localHost = null;
        response = null;

        super.reset();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isRequest() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("HttpRequestPacket (\n   method=").append(getMethod())
                .append("\n   url=").append(getRequestURI())
                .append("\n   query=").append(getQueryString())
                .append("\n   protocol=").append(getProtocol().getProtocolString())
                .append("\n   content-length=").append(getContentLength())
                .append("\n   headers=[");
        final MimeHeaders headersLocal = getHeaders();
        for (final String name : headersLocal.names()) {
            sb.append("\n      ").append(name).append('=')
                    .append(headersLocal.getHeader(name));
        }
        sb.append("]\n)");

        return sb.toString();
    }


    // ------------------------------------------------------- Protected Methods


    protected void setResponse(HttpResponsePacket response) {
        this.response = response;
    }


    // --------------------------------------------------------- Private Methods


    private void parseHostHeader() {
        if (!hostHeaderParsed) {
            doParseHostHeader();
            hostHeaderParsed = true;
        }
    }

    protected void doParseHostHeader() {
        HttpCodecUtils.parseHost(unparsedHostC, serverNameC, this);
    }
    
    // ---------------------------------------------------------- Nested Classes


    /**
     * <tt>HttpRequestPacket</tt> message builder.
     */
    public static class Builder extends HttpHeader.Builder<Builder> {

        protected Method method;
        protected String methodString;
        protected String uri;
        protected String queryString;
        protected String host;

        /**
         * Set the HTTP request method.
         * @param method the HTTP request method..
         */
        public Builder method(final Method method) {
            this.method = method;
            methodString = null;
            return this;
        }

        /**
         * Set the HTTP request method.
         * @param method the HTTP request method. Format is "GET|POST...".
         */
        public Builder method(final String method) {
            this.methodString = method;
            this.method = null;
            return this;
        }

        /**
         * Set the request URI.
         *
         * @param uri the request URI.
         */
        public Builder uri(final String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Set the value for the Host header.
         * @param host the value for the Host header.
         *
         * @return this.
         */
        public Builder host(final String host) {
            this.host = host;
            return this;
        }

        /**
         * Set the <code>query</code> portion of the request URI.
         *
         * @param queryString the query String
         *
         * @return the current <code>Builder</code>
         */
        public Builder query(final String queryString) {
            this.queryString = queryString;
            return this;
        }

        /**
         * Build the <tt>HttpRequestPacket</tt> message.
         *
         * @return <tt>HttpRequestPacket</tt>
         */
        public final HttpRequestPacket build() {
            HttpRequestPacket packet = (HttpRequestPacket) super.build();
            if (method != null) {
                packet.setMethod(method);
            }
            if (methodString != null) {
                packet.setMethod(methodString);
            }
            if (uri != null) {
                packet.setRequestURI(uri);
            }
            if (queryString != null) {
                packet.setQueryString(queryString);
            }
            if (host != null) {
                packet.addHeader(Header.Host, host);
            }
            return packet;
        }

        public void reset() {
            super.reset();
            method = null;
            uri = null;
            queryString = null;
        }

        @Override
        protected HttpHeader create() {
            return HttpRequestPacketImpl.create();
        }
    }
}
