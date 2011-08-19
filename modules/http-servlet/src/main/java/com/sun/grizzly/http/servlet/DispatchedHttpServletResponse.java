/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Locale;

/**
 * Wrapper around a <code>javax.servlet.http.HttpServletResponse</code>
 * that transforms an application response object (which might be the original
 * one passed to a servlet.
 *
 * @author Bongjae Chang
 */
public class DispatchedHttpServletResponse extends HttpServletResponseWrapper {

    /**
     * Is this wrapped response the subject of an <code>include()</code>
     * call?
     */
    private boolean included = false;

    public DispatchedHttpServletResponse( HttpServletResponse response, boolean included ) {
        super( response );
        this.included = included;
        setResponse( response );
    }

    /**
     * Set the response that we are wrapping.
     *
     * @param response The new wrapped response
     */
    private void setResponse( HttpServletResponse response ) {
        super.setResponse( response );
    }

    @Override
    public void setContentLength( int len ) {
        if( included )
            return;
        super.setContentLength( len );
    }

    @Override
    public void setContentType( String type ) {
        if( included )
            return;
        super.setContentType( type );
    }

    @Override
    public void setBufferSize( int size ) {
        if( included )
            return;
        super.setBufferSize( size );
    }

    @Override
    public void reset() {
        if( included )
            return;
        super.reset();
    }

    @Override
    public void setLocale( Locale loc ) {
        if( included )
            return;
        super.setLocale( loc );
    }

    @Override
    public void addCookie( Cookie cookie ) {
        if( included )
            return;
        super.addCookie( cookie );
    }

    @Override
    public void sendError( int sc, String msg )
            throws IOException {
        if( included )
            return;
        super.sendError( sc, msg );
    }

    @Override
    public void sendError( int sc )
            throws IOException {
        if( included )
            return;
        super.sendError( sc );
    }

    public void sendRedirect( String location )
            throws IOException {
        if( included )
            return;
        super.sendRedirect( location );
    }

    @Override
    public void setDateHeader( String name, long date ) {
        if( included )
            return;
        super.setDateHeader( name, date );
    }

    @Override
    public void addDateHeader( String name, long date ) {
        if( included )
            return;
        super.addDateHeader( name, date );
    }

    @Override
    public void setHeader( String name, String value ) {
        if( included )
            return;
        super.setHeader( name, value );
    }

    @Override
    public void addHeader( String name, String value ) {
        if( included )
            return;
        super.addHeader( name, value );
    }

    @Override
    public void setIntHeader( String name, int value ) {
        if( included )
            return;
        super.setIntHeader( name, value );
    }

    @Override
    public void addIntHeader( String name, int value ) {
        if( included )
            return;
        super.addIntHeader( name, value );
    }

    @Override
    public void setStatus( int sc ) {
        if( included )
            return;
        super.setStatus( sc );
    }

    @Override
    public void setStatus( int sc, String sm ) {
        if( included )
            return;
        super.setStatus( sc, sm );
    }

    @Override
    public void setCharacterEncoding( String charEnc ) {
        if( included )
            return;
        super.setCharacterEncoding( charEnc );
    }
}
