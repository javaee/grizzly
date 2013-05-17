/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.osgi.httpservice;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.osgi.httpservice.util.Logger;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.http.HttpService;

/**
 * Grizzly OSGi {@link HttpService} {@link ServiceFactory}.
 *
 * @author Hubert Iwaniuk
 * @since Jan 20, 2009
 */
public class HttpServiceFactory implements ServiceFactory {
    private final Logger logger;
    private OSGiMainHandler mainHttpHandler;

    public HttpServiceFactory(HttpServer httpServer, Logger logger, Bundle bundle) {
        this.logger = logger;
        mainHttpHandler = new OSGiMainHandler(logger, bundle);
        httpServer.getServerConfiguration().addHttpHandler(mainHttpHandler, "/");
    }

    @Override
    public HttpService getService(
            Bundle bundle, ServiceRegistration serviceRegistration) {
        logger.info("Bundle: " + bundle + ", is getting HttpService with serviceRegistration: " + serviceRegistration);
        return new HttpServiceImpl(bundle, logger);
    }

    @Override
    public void ungetService(
            Bundle bundle, ServiceRegistration serviceRegistration,
            Object httpServiceObj) {
        logger.info("Bundle: " + bundle + ", is ungetting HttpService with serviceRegistration: " + serviceRegistration);
        mainHttpHandler.uregisterAllLocal();
    }

    /**
     * Clean up.
     */
    public void stop() {
        logger.info("Stoping main handler");
        mainHttpHandler.unregisterAll();
    }
}
