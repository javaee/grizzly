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
            if (fileCache.sendCache(requestURI.getBytes(), requestURI.getStart(),
                    requestURI.getLength(), socketChannel,
                    keepAlive(req))) {
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
