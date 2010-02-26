/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.http.core;

import com.sun.grizzly.http.util.BufferChunk;
import com.sun.grizzly.http.util.UDecoder;

/**
 * This is a low-level, efficient representation of a server request. Most 
 * fields are GC-free, expensive operations are delayed until the  user code 
 * needs the information.
 *
 * Processing is delegated to modules, using a hook mechanism.
 * 
 * This class is not intended for user code - it is used internally by tomcat
 * for processing the request in the most efficient way. Users ( servlets ) can
 * access the information using a facade, which provides the high-level view
 * of the request.
 *
 * For lazy evaluation, the request uses the getInfo() hook. The following ids
 * are defined:
 * <ul>
 *  <li>req.encoding - returns the request encoding
 *  <li>req.attribute - returns a module-specific attribute ( like SSL keys, etc ).
 * </ul>
 *
 * Tomcat defines a number of attributes:
 * <ul>
 *   <li>"org.apache.tomcat.request" - allows access to the low-level
 *       request object in trusted applications 
 * </ul>
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author Harish Prabandham
 * @author Alex Cruikshank [alex@epitonic.com]
 * @author Hans Bergsten [hans@gefionsoftware.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class HttpRequest extends HttpHeaderPacket {

    // ----------------------------------------------------- Instance Variables
    private BufferChunk methodBC = BufferChunk.newInstance();
    private BufferChunk requestURIBC = BufferChunk.newInstance();
    private BufferChunk queryBC = BufferChunk.newInstance();
    /**
     * URL decoder.
     */
    private UDecoder urlDecoder = new UDecoder();

    public static Builder builder() {
        return new Builder();
    }
    
    // ----------------------------------------------------------- Constructors
    protected HttpRequest() {
        methodBC.setString("GET");
        /* SJSWS 6376484
        uriBC.setString("/");
         */
        queryBC.setString("");
    }

    public UDecoder getURLDecoder() {
        return urlDecoder;
    }

    // -------------------- Request data --------------------
    public BufferChunk getMethodBC() {
        return methodBC;
    }

    public String getMethod() {
        return methodBC.toString();
    }

    public void setMethod(String method) {
        this.methodBC.setString(method);
    }

    public BufferChunk getRequestURIBC() {
        return requestURIBC;
    }

    public String getRequestURI() {
        return requestURIBC.toString();
    }

    public void setRequestURI(String requestURI) {
        this.requestURIBC.setString(requestURI);
    }

    public BufferChunk getQueryStringBC() {
        return queryBC;
    }

    public String getQueryString() {
        return queryBC.toString();
    }

    // -------------------- debug --------------------
    @Override
    public String toString() {
        return "R( " + getRequestURI() + ")";
    }

    // -------------------- Recycling -------------------- 
    @Override
    public void recycle() {
        super.recycle();

        requestURIBC.recycle();
        queryBC.recycle();
        methodBC.recycle();

        // XXX Do we need such defaults ?
        methodBC.setString("GET");

        queryBC.setString("");
        protocolBC.setString("HTTP/1.0");
    }

    @Override
    public final boolean isRequest() {
        return true;
    }

    public static class Builder extends HttpHeaderPacket.Builder<Builder> {
        protected Builder() {
            packet = new HttpRequest();
        }

        public Builder method(String method) {
            ((HttpRequest) packet).setMethod(method);
            return this;
        }

        public Builder uri(String uri) {
            ((HttpRequest) packet).setRequestURI(uri);
            return this;
        }

        public final HttpRequest build() {
            return (HttpRequest) packet;
        }
    }
}
