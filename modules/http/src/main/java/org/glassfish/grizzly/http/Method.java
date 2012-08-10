/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import java.io.UnsupportedEncodingException;
import org.glassfish.grizzly.http.util.DataChunk;

/**
 * Predefined HTTP methods
 * 
 * @author Alexey Stashok
 */
public final class Method {
    public static final Method OPTIONS = new Method("OPTIONS");
    public static final Method GET = new Method("GET");
    public static final Method HEAD = new Method("HEAD");
    public static final Method POST = new Method("POST");
    public static final Method PUT = new Method("PUT");
    public static final Method DELETE = new Method("DELETE");
    public static final Method TRACE = new Method("TRACE");
    public static final Method CONNECT = new Method("CONNECT");
    public static final Method PATCH = new Method("PATCH");

    public static Method CUSTOM(final String methodName) {
        return new Method(methodName);
    }

    /**
     * @deprecated pls. use {@link #valueOf(org.glassfish.grizzly.http.util.DataChunk)}.
     */
    public static Method parseDataChunk(final DataChunk methodC) {
        return valueOf(methodC);
    }
    
    public static Method valueOf(final DataChunk methodC) {
        if (methodC.equals(Method.GET.getMethodString())) {
            return Method.GET;
        } else if (methodC.equals(Method.POST.getMethodString())) {
            return Method.POST;
        } else if (methodC.equals(Method.HEAD.getMethodString())) {
            return Method.HEAD;
        } else if (methodC.equals(Method.PUT.getMethodString())) {
            return Method.PUT;
        } else if (methodC.equals(Method.DELETE.getMethodString())) {
            return Method.DELETE;
        } else if (methodC.equals(Method.TRACE.getMethodString())) {
            return Method.TRACE;
        } else if (methodC.equals(Method.CONNECT.getMethodString())) {
            return Method.CONNECT;
        } else if (methodC.equals(Method.OPTIONS.getMethodString())) {
            return Method.OPTIONS;
        } else if (methodC.equals(Method.PATCH.getMethodString())) {
            return Method.PATCH;
        } else {
            return CUSTOM(methodC.toString());
        }
    }

    public static Method valueOf(final String method) {
        if (method.equals(Method.GET.getMethodString())) {
            return Method.GET;
        } else if (method.equals(Method.POST.getMethodString())) {
            return Method.POST;
        } else if (method.equals(Method.HEAD.getMethodString())) {
            return Method.HEAD;
        } else if (method.equals(Method.PUT.getMethodString())) {
            return Method.PUT;
        } else if (method.equals(Method.DELETE.getMethodString())) {
            return Method.DELETE;
        } else if (method.equals(Method.TRACE.getMethodString())) {
            return Method.TRACE;
        } else if (method.equals(Method.CONNECT.getMethodString())) {
            return Method.CONNECT;
        } else if (method.equals(Method.OPTIONS.getMethodString())) {
            return Method.OPTIONS;
        } else if (method.equals(Method.PATCH.getMethodString())) {
            return Method.PATCH;
        } else {
            return CUSTOM(method.toString());
        }
    }

    private final String methodString;
    private byte[] methodBytes;

    private Method(final String methodString) {
        this.methodString = methodString;
        try {
            this.methodBytes = methodString.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException ignored) {
            this.methodBytes = methodString.getBytes();
        }
    }

    public String getMethodString() {
        return methodString;
    }

    public byte[] getMethodBytes() {
        return methodBytes;
    }

    @Override
    public String toString() {
        return methodString;
    }

    public boolean matchesMethod(final String method) {
        return (this.methodString.equals(method));
    }
}
