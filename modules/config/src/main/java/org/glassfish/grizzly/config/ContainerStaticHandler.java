/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2006-2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.config;

import java.lang.reflect.Method;

import org.glassfish.grizzly.standalone.StaticHandler;
import org.glassfish.grizzly.util.http.mapper.MappingData;
import org.glassfish.grizzly.util.Interceptor;
import org.glassfish.grizzly.http.FileCache;
import org.glassfish.grizzly.http.WebFilter;
import org.glassfish.grizzly.tcp.ActionCode;
import java.io.IOException;

import org.glassfish.grizzly.tcp.Request;
import org.glassfish.grizzly.util.buf.ByteChunk;
import java.io.File;

/**
 * Extends Grizzly StaticHandler.
 *
 * @author Shing Wai Chan
 * @author Jeanfrancois Arcand
 */
@SuppressWarnings({"StaticNonFinalField"})
public class ContainerStaticHandler extends StaticHandler {

    private static Method getServletClassMethod = null;
    protected final static int MAPPING_DATA = 12;

    public ContainerStaticHandler(WebFilter webFilter) {
        super(webFilter);
    }

    /**
     * Intercept the request and decide if we cache the static resource. If the
     * static resource is already cached, return it.
     */
    @Override
    public int handle(Request req, int handlerCode) throws IOException {
        if (fileCache == null || !fileCache.isEnabled()) {
            return Interceptor.CONTINUE;
        }

        MappingData mappingData = (MappingData) req.getNote(MAPPING_DATA);
        if (handlerCode == Interceptor.RESPONSE_PROCEEDED && mappingData != null) {
            boolean isWebContainer = mappingData.wrapper != null &&
                "org.apache.catalina.core.StandardWrapper".equals(mappingData.wrapper.getClass().getName());

            ContextRootInfo cri = null;
            if (mappingData.context != null && mappingData.context instanceof ContextRootInfo) {
                cri = (ContextRootInfo) mappingData.context;
            }

            if (isWebContainer) {
                try {
                    Object wrapper = mappingData.wrapper;
                    String servletClass =
                            (String) (getServletClassMethod(wrapper).invoke(wrapper));

                    if ("org.apache.catalina.servlets.DefaultServlet".equals(servletClass)) {
                        String docroot = System.getProperty("com.glassfish.aas.instanceRoot")
                                + File.separatorChar + "applications";
                        String uri = req.requestURI().toString();
                        fileCache.add(FileCache.DEFAULT_SERVLET_NAME, docroot, uri,
                                req.getResponse().getMimeHeaders(), false);
                    } else {
                        return Interceptor.CONTINUE;
                    }
                } catch (Exception ex) {
                    IOException ioex = new IOException();
                    ioex.initCause(ex);
                    throw ioex;
                }
            } else if (cri != null && cri.getAdapter() instanceof FileCacheAware) {
                //Force caching for nucleus installation.

                req.action(ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE, req);
                String docroot = webFilter.getConfig().getWebAppRootPath();
                String uri = req.requestURI().toString();
                fileCache.add(FileCache.DEFAULT_SERVLET_NAME, docroot, uri,
                        req.getResponse().getMimeHeaders(), false);
            }
        }

        if (handlerCode == Interceptor.REQUEST_LINE_PARSED) {
            ByteChunk requestURI = req.requestURI().getByteChunk();
            if (fileCache.sendCache(requestURI.getBytes(), requestURI.getStart(),
                    requestURI.getLength(), writer,
                    keepAlive(req))) {
                return Interceptor.BREAK;
            }
        }
        return Interceptor.CONTINUE;
    }

    private static Method getServletClassMethod(Object wrapper)
        throws NoSuchMethodException {
        synchronized (ContainerStaticHandler.class) {
            if (getServletClassMethod == null) {
                Class clazz = wrapper.getClass();
                getServletClassMethod = clazz.getMethod("getServletClassName");
            }
            return getServletClassMethod;
        }
    }
}
