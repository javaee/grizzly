/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.config.dom;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.Element;
import org.jvnet.hk2.config.types.PropertyBag;
import org.glassfish.grizzly.http.Constants;

/**
 * Created Jan 8, 2009
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
@Configured
public interface Http extends ConfigBeanProxy, Injectable, PropertyBag {
    @Attribute(defaultValue = "org.glassfish.grizzly.tcp.StaticResourcesAdapter")
    String getAdapter();

    void setAdapter(String adapter);

    /**
     * Enable pass through of authentication from any front-end server
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getAuthPassThroughEnabled();

    void setAuthPassThroughEnabled(String bool);

    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getChunkingEnabled();

    void setChunkingEnabled(String enabled);

    /**
     * Enable comet support for this http instance.  The default for this is false until enabling comet support but not
     * using it can be verified as harmless.  Currently it is unclear what the performance impact of enabling this
     * feature is.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getCometSupportEnabled();

    void setCometSupportEnabled(String enable);

    @Attribute(defaultValue = "text/html,text/xml,text/plain")
    String getCompressableMimeType();

    void setCompressableMimeType(String type);

    @Attribute(defaultValue = "off", dataType = String.class)
    @Pattern(regexp = "on|off|force|\\d+")
    String getCompression();

    void setCompression(String compression);

    @Attribute(defaultValue = "2048", dataType = Integer.class)
    String getCompressionMinSizeBytes();

    void setCompressionMinSizeBytes(String size);

    @Attribute(defaultValue = "300000", dataType = Integer.class)
    String getConnectionUploadTimeoutMillis();

    void setConnectionUploadTimeoutMillis(String timeout);

    /**
     * Setting the default response-type. Specified as a semi-colon delimited string consisting of content-type,
     * encoding, language, charset
     */
    @Deprecated
    @Attribute
    String getDefaultResponseType();

    @Deprecated
    void setDefaultResponseType(String defaultResponseType);

    /**
     * The id attribute of the default virtual server for this particular connection group.
     */
    @Attribute(required = true)
    String getDefaultVirtualServer();

    void setDefaultVirtualServer(String defaultVirtualServer);

    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getDnsLookupEnabled();

    void setDnsLookupEnabled(String enable);

    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getEncodedSlashEnabled();
    
    void setEncodedSlashEnabled(String enabled);

    /**
     * Gets the value of the fileCache property.
     */
    @Element
    @NotNull
    FileCache getFileCache();

    void setFileCache(FileCache value);

    /**
     * The response type to be forced if the content served cannot be matched by any of the MIME mappings for
     * extensions. Specified as a semi-colon delimited string consisting of content-type, encoding, language, charset
     */
    @Deprecated
    @Attribute()
    String getForcedResponseType();

    @Deprecated
    void setForcedResponseType(String forcedResponseType);

    /**
     * The size of the buffer used by the request processing threads for reading the request data
     */
    @Attribute(defaultValue = "8192", dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getHeaderBufferLengthBytes();

    void setHeaderBufferLengthBytes(String length);

    /**
     * Max number of connection in the Keep Alive mode
     */
    @Attribute(defaultValue = "" + Constants.DEFAULT_MAX_KEEP_ALIVE, dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getMaxConnections();

    void setMaxConnections(String max);

    @Attribute(defaultValue = "2097152", dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getMaxPostSizeBytes();

    void setMaxPostSizeBytes(String max);

    @Attribute(dataType = Integer.class)
    String getNoCompressionUserAgents();

    void setNoCompressionUserAgents(String agents);

    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getRcmSupportEnabled();

    void setRcmSupportEnabled(String enable);

    /**
     * if the connector is supporting non-SSL requests and a request is received for which a matching
     * security-constraint requires SSL transport catalina will automatically redirect the request to the port number
     * specified here
     */
    @Attribute(dataType = Integer.class)
    @Max(65535)
    String getRedirectPort();

    void setRedirectPort(String redirectPort);

    /**
     * Time after which the request times out in seconds
     */
    @Attribute(defaultValue = "900", dataType = Integer.class)
    @Min(-1)
    @Max(Integer.MAX_VALUE)
    String getRequestTimeoutSeconds();

    void setRequestTimeoutSeconds(String timeout);

    @Attribute
    String getRestrictedUserAgents();

    void setRestrictedUserAgents(String agents);

    /**
     * Size of the buffer for response bodies in bytes
     */
    @Attribute(defaultValue = "8192", dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getSendBufferSizeBytes();

    void setSendBufferSizeBytes(String size);

    /**
     * Tells the server what to put in the host name section of any URLs it sends to the client. This affects URLs the
     * server automatically generates; it doesn't affect the URLs for directories and files stored in the server. This
     * name should be the alias name if your server uses an alias. If you append a colon and port number, that port will
     * be used in URLs the server sends to the client.
     */
    @Attribute
    String getServerName();

    void setServerName(String serverName);

    /**
     * Keep Alive timeout, max time a connection can be deemed as idle and kept in the keep-alive state
     */
    @Attribute(defaultValue = "30", dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getTimeoutSeconds();

    void setTimeoutSeconds(String timeout);

    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getTraceEnabled();

    void setTraceEnabled(String enabled);

    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getUploadTimeoutEnabled();

    void setUploadTimeoutEnabled(String disable);

    @Attribute(defaultValue = "UTF-8")
    String getUriEncoding();

    void setUriEncoding(String encoding);

    /**
     * The version of the HTTP protocol used by the HTTP Service
     */
    @Attribute(defaultValue = "HTTP/1.1")
    String getVersion();

    void setVersion(String version);

    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getWebsocketsSupportEnabled();

    void setWebsocketsSupportEnabled(String enabled);

    /**
     * The Servlet 2.4 spec defines a special X-Powered-By: Servlet/2.4 header, which containers may add to
     * servlet-generated responses. This is complemented by the JSP 2.0 spec, which defines a X-Powered-By: JSP/2.0
     * header to be added (on an optional basis) to responses utilizing JSP technology. The goal of these headers is to
     * aid in gathering statistical data about the use of Servlet and JSP technology. If true, these headers will be
     * added.
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getXpoweredBy();

    void setXpoweredBy(String xpoweredBy);
}
