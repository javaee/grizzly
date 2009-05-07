/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */
package com.sun.grizzly.config.dom;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.DuckTyped;
import org.jvnet.hk2.config.Element;

/**
 * Created Jan 8, 2009
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
@Configured
public interface Http extends ConfigBeanProxy, Injectable {
    @Attribute(defaultValue="com.sun.grizzly.tcp.StaticResourcesAdapter")
    String getAdapter();

    void setAdapter(String adapter);

    @Attribute(defaultValue = "true")
    String getChunkingDisabled();

    void setChunkingDisabled(String disabled);

    @Attribute(defaultValue = "false")
    String getCompression();

    void getCompression(String compression);

    @Attribute(defaultValue = "text/html,text/xml,text/plain")
    String getCompressableMimeType();

    void setCompressableMimeType(String type);

    @Attribute(defaultValue = "2048")
    String getCompressionMinSizeBytes();

    void setCompressionMinSizeBytes(String size);

    @Attribute(defaultValue = "300000")
    String getConnectionUploadTimeoutMillis();

    void setConnectionUploadTimeoutMillis(String timeout);

    /**
     * Setting the default response-type. Specified as a semi-colon delimited string consisting of content-type,
     * encoding, language, charset
     */
    @Attribute(defaultValue = "text/plain; charset=iso-8859-1")
    String getDefaultResponseType();

    void setDefaultResponseType(final String defaultResponseType);

    /**
     * The id attribute of the default virtual server for this particular connection group.
     */
    @Attribute
    String getDefaultVirtualServer();

    void setDefaultVirtualServer(final String defaultVirtualServer);

    @Attribute
    String getDisableUploadTimeout();

    void setDisableUploadTimeout(String disable);

    /**
     * Enable pass through of authentication from any front-end server
     */
    @Attribute
    String getEnableAuthPassThrough();

    void setEnableAuthPassThrough(String bool);

    /**
     * Enable comet support for this http instance.  The default for this is false until enabling comet support but not
     * using it can be verified as harmless.  Currently it is unclear what the performance impact of enabling this
     * feature is.
     */
    @Attribute(defaultValue = "false")
    String getEnableCometSupport();

    void setEnableCometSupport(String enable);

    @Attribute(defaultValue = "false")
    String getEnableRcmSupport();

    void setEnableRcmSupport(String enable);

    /**
     * Gets the value of the fileCache property.
     */
    @Element
    FileCache getFileCache();

    void setFileCache(FileCache value);

    /**
     * The response type to be forced if the content served cannot be matched by any of the MIME mappings for
     * extensions. Specified as a semi-colon delimited string consisting of content-type, encoding, language, charset
     */
    @Attribute(defaultValue = "text/plain; charset=iso-8859-1")
    String getForcedResponseType();

    void setForcedResponseType(final String forcedResponseType);

    /**
     * The size of the buffer used by the request processing threads for reading the request data
     */
    @Attribute(defaultValue = "8192")
    String getHeaderBufferLengthBytes();

    void setHeaderBufferLengthBytes(String length);

    /**
     * Max number of connection in the Keep Alive mode
     */
    @Attribute(defaultValue = "256")
    String getMaxConnections();

    void setMaxConnections(String max);

    @Attribute(defaultValue = "2097152")
    String getMaxPostSizeBytes();

    void setMaxPostSizeBytes(String max);

    @Attribute
    String getNoCompressionUserAgents();

    void setNoCompressionUserAgents(String agents);

    /**
     * if the connector is supporting non-SSL requests and a request is received for which a matching
     * security-constraint requires SSL transport catalina will automatically redirect the request to the port number
     * specified here
     */
    @Attribute
    String getRedirectPort();

    void setRedirectPort(final String redirectPort);

    /**
     * Size of the buffer for request bodies in bytes
     */
    @Attribute(defaultValue = "8192")
    String getRequestBodyBufferSizeBytes();

    void setRequestBodyBufferSizeBytes(String size);

    /**
     * Time after which the request times out in seconds
     */
    @Attribute(defaultValue = "30")
    String getRequestTimeoutSeconds();

    void setRequestTimeoutSeconds(String timeout);

    @Attribute
    String getRestrictedUserAgents();

    void setRestrictedUserAgents(String agents);

    /**
     * Size of the buffer for request bodies in bytes
     */
    @Attribute(defaultValue = "8192")
    String getSendBufferSizeBytes();

    void setSendBufferSizeBytes(String size);

    /**
     * Tells the server what to put in the host name section of any URLs it sends to the client. This affects URLs the
     * server automatically generates; it doesnt affect the URLs for directories and files stored in the server. This
     * name should be the alias name if your server uses an alias. If you append a colon and port number, that port will
     * be used in URLs the server sends to the client.
     */
    @Attribute(required = true)
    String getServerName();

    void setServerName(final String serverName);

    /**
     * Keep Alive timeout, max time a connection can be deemed as idle and kept in the keep-alive state
     */
    @Attribute(defaultValue = "30")
    String getTimeoutSeconds();

    void setTimeoutSeconds(String timeout);

    @Attribute
    String getTraceEnabled();

    void setTraceEnabled(String enabled);

    @Attribute
    String getUriEncoding();

    void setUriEncoding(String encoding);

    void setVersion(final String version);

    /**
     * The version of the HTTP protocol used by the HTTP Service
     */
    @Attribute(defaultValue = "HTTP/1.1")
    String getVersion();

    /**
     * The Servlet 2.4 spec defines a special X-Powered-By: Servlet/2.4 header, which containers may add to
     * servlet-generated responses. This is complemented by the JSP 2.0 spec, which defines a X-Powered-By: JSP/2.0
     * header to be added (on an optional basis) to responses utilizing JSP technology. The goal of these headers is to
     * aid in gathering statistical data about the use of Servlet and JSP technology. If true, these headers will be
     * added.
     */
    @Attribute(defaultValue = "true")
    String getXpoweredBy();

    void setXpoweredBy(final String xpoweredBy);
}
