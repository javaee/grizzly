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

package com.sun.grizzly.osgi.httpservice;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.osgi.httpservice.util.Logger;
import com.sun.grizzly.arp.AsyncHandler;
import com.sun.grizzly.arp.DefaultAsyncHandler;
import com.sun.grizzly.comet.CometAsyncFilter;
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
    private static final String ORG_OSGI_SERVICE_HTTPS_PORT = "org.osgi.service.http.port.secure";
    private HttpServiceFactory serviceFactory;
    private static final String COM_SUN_GRIZZLY_COMET_SUPPORT = "com.sun.grizzly.cometSupport";

    /**
     * {@inheritDoc}
     */
    public void start(final BundleContext bundleContext) throws Exception {
        logTracker = new ServiceTracker(bundleContext, LogService.class.getName(), null);
        logTracker.open();
        logger = new Logger(logTracker);
        logger.info("Starting Grizzly OSGi HttpService");

        int port = readProperty(bundleContext, ORG_OSGI_SERVICE_HTTP_PORT, 80);
        if (bundleContext.getProperty(ORG_OSGI_SERVICE_HTTPS_PORT) != null) {
            logger.warn("HTTPS not supported yet.");
        }
        boolean cometEnabled = readProperty(bundleContext, COM_SUN_GRIZZLY_COMET_SUPPORT, false);
        startGrizzly(port, cometEnabled);
        serviceFactory = new HttpServiceFactory(ws, logger, bundleContext.getBundle());
        registration = bundleContext.registerService(
                HttpService.class.getName(), serviceFactory,
                new Properties());
    }

    /**
     * Reads property from {@link BundleContext}.
     * If property not present or invalid value for type <code>T</code> return <code>defValue</code>.
     *
     * @param ctx      Bundle context to query.
     * @param name     Property name to query for.
     * @param defValue Default value if property not present or invalid value for type <code>T</code>.
     * @param <T>      Property type.
     * @return Property value or default as described above.
     */
    private <T> T readProperty(BundleContext ctx, String name, T defValue) {
        String value = ctx.getProperty(name);
        if (value != null) {
            if (defValue instanceof Integer) {
                try {
                    //noinspection unchecked,RedundantCast
                    return (T) (Integer) Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    logger.info(new StringBuilder().append("Couldn't parse '").append(name)
                            .append("' property, going to use default (").append(defValue).append("). ")
                            .append(e.getMessage()).toString());
                }
            } else if (defValue instanceof Boolean) {
                //noinspection unchecked,RedundantCast
                return (T) (Boolean) Boolean.parseBoolean(value);
            }
            //noinspection unchecked
            return (T) value;
        }
        return defValue;
    }

    /**
     * Starts {@link GrizzlyWebServer}.
     *
     * @param port Port to listen on.
     * @param cometEnabled If comet should be enabled.
     * @throws IOException Couldn't start {@link GrizzlyWebServer}.
     */
    private void startGrizzly(int port, boolean cometEnabled) throws IOException {
        ws = new GrizzlyWebServer(port);
        if (cometEnabled) {
            logger.info("Enabling Comet.");
            SelectorThread st = ws.getSelectorThread();
            st.setEnableAsyncExecution(true);
            AsyncHandler asyncHandler = new DefaultAsyncHandler();
            asyncHandler.addAsyncFilter(new CometAsyncFilter());
            st.setAsyncHandler(asyncHandler);
        }
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
