/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import com.sun.grizzly.Controller;
import com.sun.grizzly.config.dom.NetworkListener;
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

    private Controller controller;
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
        if (System.getProperty("product.name") == null) {
            System.setProperty("product.name", "GlassFish/v3");
        }
        //TODO: Configure via domain.xml
        //grizzlyListener.setController(controller);
        // TODO: This is not the right way to do.

        initializeListener(networkListener, habitat);
        setName(networkListener.getName());
        GrizzlyEmbeddedHttp.setLogger(logger);
    }

    private void initializeListener(NetworkListener networkListener, Habitat habitat) {
        isEmbeddedHttpSecured = Boolean.parseBoolean(
                networkListener.findHttpProtocol().getSecurityEnabled());
        embeddedHttp = createEmbeddedHttp(isEmbeddedHttpSecured);

        embeddedHttp.setController(controller);
        embeddedHttp.configure(networkListener, habitat);
    }

    protected GrizzlyEmbeddedHttp createEmbeddedHttp(boolean isSecured) {
        if (isSecured) {
            return new GrizzlyEmbeddedHttps(this);
        } else {
            return new GrizzlyEmbeddedHttp(this);
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
