/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.jmx;

import java.util.concurrent.ConcurrentMap;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.gmbal.Description;
import org.glassfish.gmbal.GmbalMBean;
import org.glassfish.gmbal.ManagedAttribute;
import org.glassfish.gmbal.ManagedObject;

import org.glassfish.grizzly.jmxbase.GrizzlyJmxManager;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * JMX management object for {@link org.glassfish.grizzly.http.server.HttpServer}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("The HttpServer.")
public class HttpServer extends JmxObject {


    private final org.glassfish.grizzly.http.server.HttpServer gws;

    private GrizzlyJmxManager mom;
    private final ConcurrentMap<String, NetworkListener> currentListeners =
            DataStructures.<String, NetworkListener>getConcurrentMap(4);
    private final ConcurrentMap<String, Object> listenersJmx =
            DataStructures.<String, Object>getConcurrentMap(4);
    


    // ------------------------------------------------------------ Constructors


    public HttpServer(org.glassfish.grizzly.http.server.HttpServer gws) {
        this.gws = gws;
    }


    // -------------------------------------------------- Methods from JmxObject


    /**
     * {@inheritDoc}
     */
    @Override
    public String getJmxName() {
        return "HttpServer";
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
    protected synchronized void onDeregister(GrizzlyJmxManager mom) {
        this.mom = null;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * @see org.glassfish.grizzly.http.server.HttpServer#isStarted()
     */
    @ManagedAttribute(id="started")
    @Description("Indicates whether or not this server instance has been started.")
    public boolean isStarted() {
        return gws.isStarted();
    }


//    @ManagedAttribute(id="document-root")
//    @Description("The document root of this server instance.")
//    public Collection<String> getDocumentRoots() {
//        return gws.getServerConfiguration().getDocRoots();
//    }


    // ------------------------------------------------------- Protected Methods


    protected void rebuildSubTree() {

        for (final NetworkListener l : gws.getListeners()) {
            final NetworkListener currentListener = currentListeners.get(l.getName());
            if (currentListener != l) {
                if (currentListener != null) {
                    final Object listenerJmx = listenersJmx.get(l.getName());
                    if (listenerJmx != null) {
                        mom.deregister(listenerJmx);
                    }

                    currentListeners.remove(l.getName());
                    listenersJmx.remove(l.getName());
                }

                final Object mmJmx = l.createManagementObject();
                mom.register(this, mmJmx, "NetworkListener[" + l.getName() + ']');
                currentListeners.put(l.getName(), l);
                listenersJmx.put(l.getName(), mmJmx);
            }
        }
        
    }
}
