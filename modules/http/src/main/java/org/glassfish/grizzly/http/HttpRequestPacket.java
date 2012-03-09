/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http.util.RequestURIRef;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.DefaultAttributeBuilder;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;

/**
 * The {@link HttpHeader} object, which represents HTTP request message.
 *
 * @see HttpHeader
 * @see HttpResponsePacket
 * 
 * @author Alexey Stashok
 */
public abstract class HttpRequestPacket extends HttpHeader {
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

    /**
     * Internal notes associated with this request by Catalina components
     * and event listeners.
     */
    private final transient AttributeHolder notesHolder =
            new IndexedAttributeHolder(ATTR_BUILDER);

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

        parsedMethod = Method.parseDataChunk(methodC);

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
     */
    public DataChunk serverName() {
        return serverNameC;
    }


    /**
     * @return Returns the integer value of the Internet Protocol (IP) port as
     *  specified in the <code>Host</code> request header.
     */
    public int getServerPort() {
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
     * Return the authentication type used for this Request.
     */
     public DataChunk authType() {
         return authTypeC;
     }

    /**
     * Return the name of the remote user that has been authenticated
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
     * @param note {@link Note} value to be returned
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
     * @param note {@link Note} value to be removed
     */
    public <E> E removeNote(final Note<E> note) {
        return note.attribute.remove(notesHolder);
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
        note.attribute.set(notesHolder, value);
    }

    /**
     * Return the specified request attribute if it exists; otherwise, return
     * <code>null</code>.
     *
     * @param name Name of the request attribute to return
     */
    public Object getAttribute(final String name) {
        return attributes.get(name);
    }


    /**
     * Return the names of all request attributes for this Request, or an
     * empty {@link Set} if there are none.
     */
    public Set<String> getAttributeNames() {
        return attributes.keySet();
    }

    /**
     * Set the specified request attribute to the specified value.
     *
     * @param name Name of the request attribute to set
     * @param value The associated value
     */
    public void setAttribute(final String name, final Object value) {
        attributes.put(name, value);
    }

    /**
     * Remove the specified request attribute if it exists.
     *
     * @param name Name of the request attribute to remove
     */
    public void removeAttribute(final String name) {
        attributes.remove(name);
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


    // ------------------------------------------------- Package Private Methods


    protected void setResponse(HttpResponsePacket response) {
        this.response = response;
    }

    
    // ---------------------------------------------------------- Nested Classes


    /**
     * <tt>HttpRequestPacket</tt> message builder.
     */
    public static class Builder extends HttpHeader.Builder<Builder> {
        protected Builder() {
            packet = HttpRequestPacketImpl.create();
        }

        /**
         * Set the HTTP request method.
         * @param method the HTTP request method..
         */
        public Builder method(final Method method) {
            ((HttpRequestPacket) packet).setMethod(method);
            return this;
        }

        /**
         * Set the HTTP request method.
         * @param method the HTTP request method. Format is "GET|POST...".
         */
        public Builder method(final String method) {
            ((HttpRequestPacket) packet).setMethod(method);
            return this;
        }

        /**
         * Set the request URI.
         *
         * @param uri the request URI.
         */
        public Builder uri(final String uri) {
            ((HttpRequestPacket) packet).setRequestURI(uri);
            return this;
        }

        /**
         * Set the <code>query</code> portion of the request URI.
         *
         * @param query the query String
         *
         * @return the current <code>Builder</code>
         */
        public Builder query(final String query) {
            ((HttpRequestPacket) packet).setQueryString(query);
            return this;
        }

        /**
         * Build the <tt>HttpRequestPacket</tt> message.
         *
         * @return <tt>HttpRequestPacket</tt>
         */
        public final HttpRequestPacket build() {
            return (HttpRequestPacket) packet;
        }
    }
}
