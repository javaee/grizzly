/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.servlet;

import com.sun.grizzly.tcp.http11.Constants;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.res.StringManager;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Locale;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;



/**
 * Facade class that wraps a {@link GrizzlyResponse} object. 
 * All methods are delegated to the wrapped response.
 *
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 * @version $Revision: 1.9 $ $Date: 2007/05/05 05:32:43 $
 */


public class HttpServletResponseImpl implements HttpServletResponse {

    private ServletOutputStreamImpl outputStream = null;
    
    // ----------------------------------------------------------- DoPrivileged
    
    private final class SetContentTypePrivilegedAction
            implements PrivilegedAction {

        private String contentType;

        public SetContentTypePrivilegedAction(String contentType){
            this.contentType = contentType;
        }
        
        public Object run() {
            response.setContentType(contentType);
            return null;
        }            
    }
     
    
    // ----------------------------------------------------------- Constructors


    /**
     * Construct a wrapper for the specified response.
     *
     * @param response The response to be wrapped
     */
    public HttpServletResponseImpl(GrizzlyResponse response) throws IOException {
        this.response = response;
        this.outputStream = new ServletOutputStreamImpl(response.createOutputStream());
    }


    // ----------------------------------------------- Class/Instance Variables


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package,
                                 Constants.class.getClassLoader());


    /**
     * The wrapped response.
     */
    protected GrizzlyResponse response = null;


    // --------------------------------------------------------- Public Methods

    
    /**
    * Prevent cloning the facade.
    */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
      
    
    /**
     * Clear facade.
     */
    public void clear() {
        response = null;
    }


    public void finish() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        response.setSuspended(true);

    }


    public boolean isFinished() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return response.isBufferSuspended();

    }


    // ------------------------------------------------ ServletResponse Methods

    
    /**
     * {@inheritDoc}
     */
    public String getCharacterEncoding() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return response.getCharacterEncoding();
    }

    
    /**
     * {@inheritDoc}
     */
    public ServletOutputStream getOutputStream()
        throws IOException {

        if (outputStream == null){
            outputStream = new ServletOutputStreamImpl(response.createOutputStream());
        }

        if (isFinished())
            response.setSuspended(true);
        
        return outputStream;

    }

    /**
     * Underlying GrizzlyResponse could be recycled. For example OutputStream is
     * not longer available. We need to update the ServletOutputStream accordingly.
     */
    void update() throws IOException {
        if (outputStream != null) {
            outputStream.update(response.createOutputStream());
        }
    }

    void recycle() {
        if(System.getSecurityManager() != null){
            outputStream = null;
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public PrintWriter getWriter()
        throws IOException {

        //        if (isFinished())
        //            throw new IllegalStateException
        //                (/*sm.getString("HttpServletResponseImpl.finished")*/);

        PrintWriter writer = response.getWriter();
        if (isFinished())
            response.setSuspended(true);
        return (writer);

    }

    
    /**
     * {@inheritDoc}
     */
    public void setContentLength(int len) {

        if (isCommitted())
            return;

        response.setContentLength(len);

    }

    
    /**
     * {@inheritDoc}
     */
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
    public void setBufferSize(int size) {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setBufferSize(size);

    }

    
    /**
     * {@inheritDoc}
     */
    public int getBufferSize() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return response.getBufferSize();
    }

    
    /**
     * {@inheritDoc}
     */
    public void flushBuffer()
        throws IOException {

        if (isFinished())
            //            throw new IllegalStateException
            //                (/*sm.getString("HttpServletResponseImpl.finished")*/);
            return;
        
        if (System.getSecurityManager() != null){
            try{
                AccessController.doPrivileged(new PrivilegedExceptionAction(){

                    public Object run() throws IOException{
                        response.setAppCommitted(true);

                        response.flushBuffer();
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
            response.setAppCommitted(true);

            response.flushBuffer();            
        }

    }

    
    /**
     * {@inheritDoc}
     */
    public void resetBuffer() {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.resetBuffer();

    }

    
    /**
     * {@inheritDoc}
     */
    public boolean isCommitted() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return (response.isAppCommitted());
    }

    
    /**
     * {@inheritDoc}
     */
    public void reset() {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.reset();

    }

    
    /**
     * {@inheritDoc}
     */
    public void setLocale(Locale loc) {

        if (isCommitted())
            return;

        response.setLocale(loc);
    }

    
    /**
     * {@inheritDoc}
     */
    public Locale getLocale() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return response.getLocale();
    }

    
    /**
     * {@inheritDoc}
     */
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
    public boolean containsHeader(String name) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return response.containsHeader(name);
    }

    
    /**
     * {@inheritDoc}
     */
    public String encodeURL(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        return response.encodeURL(url);
    }

    
    /**
     * {@inheritDoc}
     */
    public String encodeRedirectURL(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        throw new UnsupportedOperationException("Not supported yet.");
    }

    
    /**
     * {@inheritDoc}
     */
    public String encodeUrl(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        throw new UnsupportedOperationException("Not supported yet.");
    }

    
    /**
     * {@inheritDoc}
     */
    public String encodeRedirectUrl(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("HttpServletResponseImpl.nullResponse"));
        }

        throw new UnsupportedOperationException("Not supported yet.");
    }

    
    /**
     * {@inheritDoc}
     */
    public void sendError(int sc, String msg)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setAppCommitted(true);

        response.sendError(sc, msg);

    }

    
    /**
     * {@inheritDoc}
     */
    public void sendError(int sc)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setAppCommitted(true);

        response.sendError(sc);

    }

    
    /**
     * {@inheritDoc}
     */
    public void sendRedirect(String location)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setAppCommitted(true);

        response.sendRedirect(location);

    }

    
    /**
     * {@inheritDoc}
     */
    public void setDateHeader(String name, long date) {

        if (isCommitted())
            return;

        response.setDateHeader(name, date);

    }

    
    /**
     * {@inheritDoc}
     */
    public void addDateHeader(String name, long date) {

        if (isCommitted())
            return;

        response.addDateHeader(name, date);

    }

    
    /**
     * {@inheritDoc}
     */
    public void setHeader(String name, String value) {

        if (isCommitted())
            return;

        response.setHeader(name, value);

    }

    
    /**
     * {@inheritDoc}
     */
    public void addHeader(String name, String value) {

        if (isCommitted())
            return;

        response.addHeader(name, value);

    }

   
    /**
     * {@inheritDoc}
     */
    public void setIntHeader(String name, int value) {

        if (isCommitted())
            return;

        response.setIntHeader(name, value);

    }

   
    /**
     * {@inheritDoc}
     */
    public void addIntHeader(String name, int value) {

        if (isCommitted())
            return;

        response.addIntHeader(name, value);

    }

   
    /**
     * {@inheritDoc}
     */
    public void setStatus(int sc) {

        if (isCommitted())
            return;

        response.setStatus(sc);

    }

   
    /**
     * {@inheritDoc}
     */
    public void setStatus(int sc, String sm) {

        if (isCommitted())
            return;

        response.setStatus(sc, sm);

    }


    public int getStatus() {
        return response.getStatus();
    }
    
    
    public String getMessage() {
        return response.getMessage();
    }

    
    public void setSuspended(boolean suspended) {
        response.setSuspended(suspended);
    }

    
    public void setAppCommitted(boolean appCommitted) {
        response.setAppCommitted(appCommitted);
    }

    
    public int getContentCount() {
        return response.getContentCount();
    }

    
    public boolean isError() {
        return response.isError();
    }

   
    /**
     * {@inheritDoc}
     */
    public String getContentType() {
        return response.getContentType();
    }

   
    /**
     * {@inheritDoc}
     */    
    public void setCharacterEncoding(String charEnc) {
        response.setCharacterEncoding(charEnc);
    }
}
