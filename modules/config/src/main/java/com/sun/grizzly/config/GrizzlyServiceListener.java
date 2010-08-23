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

package com.sun.grizzly.config;

import com.sun.grizzly.Controller;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import org.jvnet.hk2.component.Habitat;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * <p>The GrizzlyServiceListener is responsible of mapping incoming requests to the proper Container or Grizzly
 * extensions. Registered Containers can be notified by Grizzly using three mode:</p> <ul><li>At the transport level:
 * Containers can be notified when TCP, TLS or UDP requests are mapped to them.</li> <li>At the protocol level:
 * Containers can be notified when protocols (ex: SIP, HTTP) requests are mapped to them.</li> </li>At the requests
 * level: Containers can be notified when specific patterns requests are mapped to them.</li><ul>
 *
 * @author Jeanfrancois Arcand
 */
public class GrizzlyServiceListener {
    /**
     * The logger to use for logging messages.
     */
    protected static final Logger logger = Logger.getLogger(GrizzlyServiceListener.class.getName());

    private final Controller controller;
    private boolean isEmbeddedHttpSecured;
    private GrizzlyEmbeddedHttp embeddedHttp;
    private String name;

    public GrizzlyServiceListener(Controller cont) {
        controller = cont;
    }

    /*
    * Configures the given grizzlyListener.
    *
    * @param grizzlyListener The grizzlyListener to configure
    * @param httpProtocol The Protocol that corresponds to the given grizzlyListener
    * @param isSecure true if the grizzlyListener is security-enabled, false otherwise
    * @param httpServiceProps The httpProtocol-service properties
    * @param isWebProfile if true - just HTTP protocol is supported on port,
    *        false - port unification will be activated
    */
    // TODO: Must get the information from domain.xml Config objects.
    // TODO: Pending Grizzly issue 54
    public void configure(NetworkListener networkListener, Habitat habitat) {
        initializeListener(networkListener, habitat);
        setName(networkListener.getName());
        GrizzlyEmbeddedHttp.setLogger(logger);
    }

    private void initializeListener(NetworkListener networkListener, Habitat habitat) {
        final Protocol httpProtocol = networkListener.findHttpProtocol();
        if (httpProtocol != null) {
            isEmbeddedHttpSecured = Boolean.parseBoolean(
                    httpProtocol.getSecurityEnabled());
        }
        
        embeddedHttp = createEmbeddedHttp(isEmbeddedHttpSecured);

        embeddedHttp.setController(controller);
        embeddedHttp.configure(networkListener, habitat);
    }

    protected GrizzlyEmbeddedHttp createEmbeddedHttp(boolean isSecured) {
        if (isSecured) {
            return new GrizzlyEmbeddedHttps();
        } else {
            return new GrizzlyEmbeddedHttp();
        }
    }

    public void start() throws IOException, InstantiationException {
        embeddedHttp.initEndpoint();
        embeddedHttp.startEndpoint();
    }

    public void stop() {
        embeddedHttp.stopEndpoint();
    }

    public void setAddress(InetAddress address) {
        if (embeddedHttp != null) {
            embeddedHttp.setAddress(address);
        }
    }

    public Controller getController() {
        return controller;
    }

    public String getDefaultVirtualServer() {
        return embeddedHttp.getDefaultVirtualServer();
    }

    public GrizzlyEmbeddedHttp getEmbeddedHttp() {
        return embeddedHttp;
    }

    public boolean isEmbeddedHttpSecured() {
        return isEmbeddedHttpSecured;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPort() {
        return embeddedHttp.getPort();
    }
}
