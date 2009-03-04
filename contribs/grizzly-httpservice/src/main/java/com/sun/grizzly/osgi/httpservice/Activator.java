/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */
package com.sun.grizzly.osgi.httpservice;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.osgi.httpservice.util.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;
import org.osgi.service.log.LogService;
import org.osgi.util.tracker.ServiceTracker;

import java.io.IOException;
import java.util.Properties;

/**
 * OSGi HttpService Activator.
 *
 * @author Hubert Iwaniuk
 * @since Jan 20, 2009
 */
public class Activator implements BundleActivator {
    private ServiceTracker logTracker;
    private ServiceRegistration registration;
    private Logger logger;
    private GrizzlyWebServer ws;
    private static final String ORG_OSGI_SERVICE_HTTP_PORT = "org.osgi.service.http.port";
    private HttpServiceFactory serviceFactory;

    /**
     * {@inheritDoc}
     */
    public void start(final BundleContext bundleContext) throws Exception {
        logTracker = new ServiceTracker(bundleContext, LogService.class.getName(), null);
        logTracker.open();
        logger = new Logger(logTracker);
        logger.info("Starting Grizzly OSGi HttpService");

        String portProp = bundleContext.getProperty(ORG_OSGI_SERVICE_HTTP_PORT);
        int port = 80;
        if (portProp != null) {
            try {
                port = Integer.parseInt(portProp);
            } catch (NumberFormatException nfe) {
                logger.warn(new StringBuilder().append("Couldn't parse '").append(ORG_OSGI_SERVICE_HTTP_PORT)
                        .append("' property, going to use default (").append(port).append("). ")
                        .append(nfe.getMessage()).toString());
            }
        }
        startGrizzly(port);
        serviceFactory = new HttpServiceFactory(ws, logger, bundleContext.getBundle());
        registration = bundleContext.registerService(
                HttpService.class.getName(), serviceFactory,
                new Properties());
    }

    private void startGrizzly(int port) throws IOException {
        ws = new GrizzlyWebServer(port);
        ws.setMaxThreads(5);
        ws.useAsynchronousWrite(true);
        ws.start();
    }

    /**
     * {@inheritDoc}
     */
    public void stop(final BundleContext bundleContext) throws Exception {
        logger.info("Stopping Grizzly OSGi HttpService");
        serviceFactory.stop();
        if (registration != null) {
            registration.unregister();
        }
        ws.stop();
        logTracker.close();
    }
}
