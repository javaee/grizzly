/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import org.glassfish.grizzly.ThreadCache;
import org.glassfish.grizzly.http.server.Response;



/**
 * Facade class that wraps a {@link Response} object.
 * All methods are delegated to the wrapped response.
 *
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 * @version $Revision: 1.9 $ $Date: 2007/05/05 05:32:43 $
 */
@SuppressWarnings("deprecation")
public class HttpServletResponseImpl implements HttpServletResponse, Holders.ResponseHolder {

    private final ServletOutputStreamImpl outputStream;
    private ServletWriterImpl writer;
    
    /**
     * Using output stream flag.
     */
    protected boolean usingOutputStream = false;


    /**
     * Using writer flag.
     */
    protected boolean usingWriter = false;

    // ----------------------------------------------------------- DoPrivileged
    
    private final class SetContentTypePrivilegedAction
            implements PrivilegedAction {

        private final String contentType;

        public SetContentTypePrivilegedAction(String contentType){
            this.contentType = contentType;
        }
        
        @Override
        public Object run() {
            response.setContentType(contentType);
            return null;
        }            
    }
     
    private static final ThreadCache.CachedTypeIndex<HttpServletResponseImpl> CACHE_IDX =
            ThreadCache.obtainIndex(HttpServletResponseImpl.class, 2);

    // ------------- Factory ----------------
    public static HttpServletResponseImpl create() {
        final HttpServletResponseImpl response =
                ThreadCache.takeFromCache(CACHE_IDX);
        if (response != null) {
            return response;
        }

        return new HttpServletResponseImpl();
    }
    
    // ----------------------------------------------------------- Constructors


    /**
     * Construct a wrapper for the specified response.
     */
    protected HttpServletResponseImpl() {
        outputStream = new ServletOutputStreamImpl(this);
    }


    // ----------------------------------------------- Class/Instance Variables

    /**
     * The wrapped response.
     */
    protected Response response = null;

    /**
     * {@link HttpServletRequestImpl}.
     */
    protected HttpServletRequestImpl servletRequest;

    // --------------------------------------------------------- Public Methods

    public void initialize(final Response response,
            final HttpServletRequestImpl servletRequest) throws IOException {
        this.response = response;
        this.servletRequest = servletRequest;
        
        outputStream.initialize();

    }
    /**
    * Prevent cloning the facade.
    */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
      
    
    public void finish() throws IOException {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

//        response.setSuspended(true);
        response.finish();

    }


//    public boolean isFinished() {
//
//        if (response == null) {
//            throw new IllegalStateException(
//                            sm.getString("HttpServletResponseImpl.nullResponse"));
//        }
//
//        return response.isBufferSuspended();
//
//    }


    // ------------------------------------------------ ServletResponse Methods

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getCharacterEncoding() {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.getCharacterEncoding();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public ServletOutputStream getOutputStream()
        throws IOException {

        if (usingWriter)
            throw new IllegalStateException("Illegal attempt to call getOutputStream() after getWriter() has already been called.");

        usingOutputStream = true;
        return outputStream;

    }

    void recycle() {
        response = null;
        servletRequest = null;
        
        writer = null;
        
        outputStream.recycle();
        
        usingOutputStream = false;
        usingWriter = false;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public PrintWriter getWriter()
        throws IOException {

        if (usingOutputStream)
            throw new IllegalStateException("Illegal attempt to call getWriter() after getOutputStream has already been called.");

        usingWriter = true;
        if (writer == null) {
            writer = new ServletWriterImpl(response.getWriter());
        }
        
        return writer;
        
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setContentLength(int len) {

        if (isCommitted())
            return;

        response.setContentLength(len);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setContentLengthLong(long len) {
        if (isCommitted())
            return;

        response.setContentLengthLong(len);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void setContentType(String type) {

        if (isCommitted())
            return;
        
        if (System.getSecurityManager() != null){
            AccessController.doPrivileged(new SetContentTypePrivilegedAction(type));
        } else {
            response.setContentType(type);            
        }
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setBufferSize(int size) {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to adjust the buffer size after the response has already been committed.");

        response.setBufferSize(size);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public int getBufferSize() {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.getBufferSize();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void flushBuffer()
        throws IOException {

//        if (isFinished())
//            //            throw new IllegalStateException
//            //                (/*sm.getString("HttpServletResponseImpl.finished")*/);
//            return;
        
        if (System.getSecurityManager() != null){
            try{
                AccessController.doPrivileged(new PrivilegedExceptionAction(){

                    @Override
                    public Object run() throws IOException{
//                        response.setAppCommitted(true);

                        response.flush();
                        return null;
                    }
                });
            } catch(PrivilegedActionException e){
                Exception ex = e.getException();
                if (ex instanceof IOException){
                    throw (IOException)ex;
                }
            }
        } else {
//            response.setAppCommitted(true);

            response.flush();            
        }

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void resetBuffer() {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to reset the buffer after the response has already been committed.");

        response.resetBuffer();

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCommitted() {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.isCommitted();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to reset the response after it has already been committed.");

        response.reset();

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocale(Locale loc) {

        if (isCommitted())
            return;

        response.setLocale(loc);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public Locale getLocale() {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.getLocale();
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addCookie(Cookie cookie) {

        if (isCommitted())
            return;
        CookieWrapper wrapper = new CookieWrapper(cookie.getName(),cookie.getValue());
        wrapper.setWrappedCookie(cookie);
        response.addCookie(wrapper);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsHeader(String name) {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.containsHeader(name);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeURL(String url) {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.encodeURL(url);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeRedirectURL(String url) {

        if (response == null) {
            throw new IllegalStateException("Null response object");
        }

        return response.encodeRedirectURL(url);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeUrl(String url) {
        return encodeURL(url);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public String encodeRedirectUrl(String url) {
        return encodeRedirectURL(url);
    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void sendError(int sc, String msg)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to call sendError() after the response has been committed.");

//        response.setAppCommitted(true);

        response.sendError(sc, msg);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void sendError(int sc)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to call sendError() after the response has already been committed.");

//        response.setAppCommitted(true);

        response.sendError(sc);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void sendRedirect(String location)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException("Illegal attempt to redirect the response after it has been committed.");

//        response.setAppCommitted(true);

        response.sendRedirect(location);

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String string) {
        return response.getHeader(string);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getHeaderNames() {
        return new ArrayList<String>(Arrays.asList(response.getHeaderNames()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<String> getHeaders(String string) {
        return new ArrayList<String>(Arrays.asList(response.getHeaderValues(string)));
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setDateHeader(String name, long date) {

        if (isCommitted())
            return;

        response.setDateHeader(name, date);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addDateHeader(String name, long date) {

        if (isCommitted())
            return;

        response.addDateHeader(name, date);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void setHeader(String name, String value) {

        if (isCommitted())
            return;

        response.setHeader(name, value);

    }

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addHeader(String name, String value) {

        if (isCommitted())
            return;

        response.addHeader(name, value);

    }

   
    /**
     * {@inheritDoc}
     */
    @Override
    public void setIntHeader(String name, int value) {

        if (isCommitted())
            return;

        response.setIntHeader(name, value);

    }

   
    /**
     * {@inheritDoc}
     */
    @Override
    public void addIntHeader(String name, int value) {

        if (isCommitted())
            return;

        response.addIntHeader(name, value);

    }

   
    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatus(int sc) {

        if (isCommitted())
            return;

        response.setStatus(sc);

    }

   
    /**
     * {@inheritDoc}
     */
    @Override
    public void setStatus(int sc, String sm) {

        if (isCommitted())
            return;

        response.setStatus(sc, sm);

    }


    @Override
    public int getStatus() {
        return response.getStatus();
    }
    
    
    public String getMessage() {
        return response.getMessage();
    }

    
//    public void setSuspended(boolean suspended) {
//        response.setSuspended(suspended);
//    }

    
//    public void setAppCommitted(boolean appCommitted) {
//        response.setAppCommitted(appCommitted);
//    }
//
//
//    public int getContentCount() {
//        return response.getContentCount();
//    }

    
    public boolean isError() {
        return response.isError();
    }

   
    /**
     * {@inheritDoc}
     */
    @Override
    public String getContentType() {
        return response.getContentType();
    }

   
    /**
     * {@inheritDoc}
     */    
    @Override
    public void setCharacterEncoding(String charEnc) {
        response.setCharacterEncoding(charEnc);
    }


    public Response getResponse() {
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response getInternalResponse() {
        return response;
    }    
}
