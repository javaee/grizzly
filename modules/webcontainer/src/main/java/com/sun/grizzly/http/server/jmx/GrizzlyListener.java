/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.http.server.jmx;

import com.sun.grizzly.Transport;
import com.sun.grizzly.http.HttpCodecFilter;
import com.sun.grizzly.http.server.filecache.FileCache;
import com.sun.grizzly.http.server.WebServerFilter;
import com.sun.grizzly.monitoring.jmx.GrizzlyJmxManager;
import com.sun.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

/**
 * JMX management object for {@link com.sun.grizzly.http.server.GrizzlyListener}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("The GrizzlyListener is an abstraction around the Transport (exposed as a child of this entity).")
public class GrizzlyListener extends JmxObject {

    private final com.sun.grizzly.http.server.GrizzlyListener listener;

    private FileCache currentFileCache;
    private Transport currentTransport;

    private JmxObject fileCacheJmx;
    private JmxObject transportJmx;

    private WebServerFilter currentWebServerFilter;
    private JmxObject webServerFilterJmx;
    
    private HttpCodecFilter currentHttpCodecFilter;
    private JmxObject httpCodecFilterJmx;

    private GrizzlyJmxManager mom;


    // ------------------------------------------------------------ Constructors


    public GrizzlyListener(com.sun.grizzly.http.server.GrizzlyListener listener) {

        this.listener = listener;

    }


    // -------------------------------------------------- Methods from JmxObject


    /**
     * {@inerhitDoc
     */
    @Override
    public String getJmxName() {
        return "GrizzlyListener";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        this.mom = mom;
        rebuildSubTree();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected synchronized void onUnregister(GrizzlyJmxManager mom) {
        this.mom = null;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#getName()}
     */
    @ManagedAttribute(id="name")
    @Description("The logical name of the listener.")
    public String getName() {
        return listener.getName();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#getHost()}
     */
    @ManagedAttribute(id="host")
    @Description("The network host to which this listener is bound.")
    public String getHost() {
        return listener.getHost();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#getPort()}
     */
    @ManagedAttribute(id="port")
    @Description("The network port to which this listener is bound.")
    public int getPort() {
        return listener.getPort();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#getKeepAliveTimeoutInSeconds()}
     */
    @ManagedAttribute(id="keep-alive-timeout-in-seconds")
    @Description("The time, in seconds, to keep an inactive request alive.")
    public int getKeepAliveTimeoutInSeconds() {
        return listener.getKeepAliveTimeoutInSeconds();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#isSecure()}
     */
    @ManagedAttribute(id="secure")
    @Description("Indicates whether or not this listener is secured via SSL.")
    public boolean isSecure() {
        return listener.isSecure();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#getMaxHttpHeaderSize()} ()}
     */
    @ManagedAttribute(id="max-http-header-size")
    @Description("The maximum size, in bytes, an HTTP request may be.")
    public int getMaxHttpHeaderSize() {
        return listener.getMaxHttpHeaderSize();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#getName()}
     */
    @ManagedAttribute(id="max-pending-bytes")
    @Description("The maximum size, in bytes, a connection may have waiting to be sent to the client.")
    public int getMaxPendingBytes() {
        return listener.getMaxPendingBytes();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#isStarted()}
     */
    @ManagedAttribute(id="started")
    @Description("Indicates whether or not this listener is started.")
    public boolean isStarted() {
        return listener.isStarted();
    }


    /**
     * @see {@link com.sun.grizzly.http.server.GrizzlyListener#isPaused()}
     */
    @Description("Indicates whether or not a started listener is actively processing requests.")
    @ManagedAttribute(id="paused")
    public boolean isPaused() {
        return listener.isPaused();
    }


    // ------------------------------------------------------- Protected Methods


    protected void rebuildSubTree() {

        final FileCache fileCache = listener.getFileCache();
        if (currentFileCache != fileCache) {
            if (currentFileCache != null) {
                mom.unregister(fileCacheJmx);

                currentFileCache = null;
                fileCacheJmx = null;
            }

            if (fileCache != null) {
                final JmxObject jmx = fileCache.getMonitoringConfig().createManagementObject();
                mom.register(this, jmx, jmx.getJmxName());
                currentFileCache = fileCache;
                fileCacheJmx = jmx;
            }
        }

        final Transport transport = listener.getTransport();
        if (currentTransport != transport) {
            if (currentTransport != null) {
                mom.unregister(transportJmx);

                currentTransport = null;
                transportJmx = null;
            }

            if (transport != null) {
                final JmxObject jmx = transport.getMonitoringConfig().createManagementObject();
                mom.register(this, jmx, jmx.getJmxName());
                currentTransport = transport;
                transportJmx = jmx;
            }
        }

        final WebServerFilter filter = listener.getWebServerFilter();
        if (currentWebServerFilter != filter) {
            if (currentWebServerFilter != null) {
                mom.unregister(webServerFilterJmx);

                currentWebServerFilter = null;
                webServerFilterJmx = null;
            }

            if (filter != null) {
                final JmxObject jmx = filter.getMonitoringConfig().createManagementObject();
                mom.register(this, jmx, jmx.getJmxName());
                currentWebServerFilter = filter;
                webServerFilterJmx = jmx;
            }
        }

        final HttpCodecFilter codecFilter = listener.getHttpCodecFilter();
        if (currentHttpCodecFilter != codecFilter) {
            if (currentHttpCodecFilter != null) {
                mom.unregister(httpCodecFilterJmx);

                currentHttpCodecFilter = null;
                httpCodecFilterJmx = null;
            }

            if (codecFilter != null) {
                final JmxObject jmx = codecFilter.getMonitoringConfig().createManagementObject();
                mom.register(this, jmx, jmx.getJmxName());
                currentHttpCodecFilter = codecFilter;
                httpCodecFilterJmx = jmx;
            }
        }
        
    }

}
