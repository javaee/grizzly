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

package com.sun.grizzly.osgi.httpservice.its.impl;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.service.log.LogService;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;

import javax.servlet.GenericServlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Bundle activator.
 */
public class Activator implements BundleActivator {
    private ServiceTracker logTracker;
    private ServiceTracker httpServiceTracker;
    private Logger logger;

    /**
     * {@inheritDoc}
     */
    public void start(final BundleContext bundleContext) throws Exception {
        logTracker = new ServiceTracker(
                bundleContext, LogService.class.getName(), null);

        logTracker.open();
        logger = new Logger(logTracker);
        logger.info("staring sample bundle");

        final GenericServlet genericServlet = new GenericServlet() {
            public void service(
                    ServletRequest servletRequest, ServletResponse servletResponse)
                    throws ServletException, IOException {
                HttpServletResponse response = (HttpServletResponse) servletResponse;
                response.setStatus(200);
                response.getWriter().write("Grizzly");
            }
        };
        httpServiceTracker = new ServiceTracker(bundleContext, HttpService.class.getName(), null);
        httpServiceTracker.open();

        if (httpServiceTracker.getService() == null) {
            final CountDownLatch latch = new CountDownLatch(1);
            new Thread(new Runnable() {
                public void run() {
                    try {
                        latch.await();
                        httpServiceTracker.waitForService(1000);
                        register(genericServlet);
                    } catch (ServletException e) {
                        e.printStackTrace();
                    } catch (NamespaceException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

            bundleContext.addServiceListener(new ServiceListener() {
                public void serviceChanged(ServiceEvent serviceEvent) {
                    if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
                        latch.countDown();
                    }
                }
            }, "(objectclass=" + HttpService.class.getName() + ")");
        } else {
            register(genericServlet);
        }
        logger.info("stared sample bundle");
    }

    private void register(GenericServlet genericServlet) throws ServletException, NamespaceException {
        HttpService service = (HttpService) httpServiceTracker.getService();
        if (service == null) {
            logger.warn("No HttpService");
            return;
        }
        service.registerServlet("/1", genericServlet, new Properties(), null);
        service.registerResources("/2", "", null);
//        service.registerResources("/10", "", null);
//        service.registerResources("/10/2", "", null);
    }

    /**
     * {@inheritDoc}
     */
    public void stop(BundleContext bundleContext) throws Exception {
        logger.info("stoping sample bundle");
        HttpService service = (HttpService) httpServiceTracker.getService();
        if (service != null) {
            service.unregister("/1");
            service.unregister("/2");
        }
        httpServiceTracker.close();
        logger.info("stoped sample bundle");
        logTracker.close();
    }
}
