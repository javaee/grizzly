/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.config;

import java.lang.reflect.Method;

import com.sun.grizzly.standalone.StaticHandler;
import com.sun.grizzly.util.http.mapper.MappingData;
import com.sun.grizzly.util.Interceptor;
import com.sun.grizzly.http.FileCache;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.tcp.ActionCode;
import java.io.IOException;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.ByteChunk;
import java.io.File;

/**
 * Extends Grizzly StaticHandler.
 *
 * @author Shing Wai Chan
 * @author Jeanfrancois Arcand
 */
public class ContainerStaticHandler extends StaticHandler {

    private static Method getServletClassMethod = null;
    protected final static int MAPPING_DATA = 12;

    public ContainerStaticHandler() {
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
                    mappingData.wrapper.getClass().getName().equals(
                    "org.apache.catalina.core.StandardWrapper");

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
                        String docroot = System.getProperty("com.sun.aas.instanceRoot")
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
                String docroot = SelectorThread.getSelector(req.getLocalPort()).getWebAppRootPath();
                String uri = req.requestURI().toString();
                fileCache.add(FileCache.DEFAULT_SERVLET_NAME, docroot, uri,
                        req.getResponse().getMimeHeaders(), false);
            }
        }

        if (handlerCode == Interceptor.REQUEST_LINE_PARSED) {
            ByteChunk requestURI = req.requestURI().getByteChunk();
            if (fileCache.sendCache(req)) {
                return Interceptor.BREAK;
            }
        }
        return Interceptor.CONTINUE;
    }

    private static synchronized Method getServletClassMethod(Object wrapper)
            throws NoSuchMethodException {

        if (getServletClassMethod == null) {
            Class clazz = wrapper.getClass();
            getServletClassMethod = clazz.getMethod("getServletClassName");
        }

        return getServletClassMethod;
    }
}
