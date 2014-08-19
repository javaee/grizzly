/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2014 Oracle and/or its affiliates. All rights reserved.
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
    public enum PayloadExpectation {ALLOWED, NOT_ALLOWED, UNDEFINED};
    
    public static final Method OPTIONS =
            new Method("OPTIONS", PayloadExpectation.ALLOWED);
    public static final Method GET =
            new Method("GET", PayloadExpectation.UNDEFINED);
    public static final Method HEAD =
            new Method("HEAD", PayloadExpectation.UNDEFINED);
    public static final Method POST
            = new Method("POST", PayloadExpectation.ALLOWED);
    public static final Method PUT
            = new Method("PUT", PayloadExpectation.ALLOWED);
    public static final Method DELETE
            = new Method("DELETE", PayloadExpectation.UNDEFINED);
    public static final Method TRACE
            = new Method("TRACE", PayloadExpectation.NOT_ALLOWED);
    public static final Method CONNECT
            = new Method("CONNECT", PayloadExpectation.NOT_ALLOWED);
    public static final Method PATCH
            = new Method("PATCH", PayloadExpectation.ALLOWED);
    public static final Method PRI
            = new Method("PRI", PayloadExpectation.NOT_ALLOWED);

    public static Method CUSTOM(final String methodName) {
        return CUSTOM(methodName, PayloadExpectation.ALLOWED);
    }

    public static Method CUSTOM(final String methodName,
            final PayloadExpectation payloadExpectation) {
        return new Method(methodName, payloadExpectation);
    }    

    public static Method valueOf(final DataChunk methodC) {
        if (methodC.equals(Method.GET.getMethodString())) {
            return Method.GET;
        } else if (methodC.equals(Method.POST.getMethodBytes())) {
            return Method.POST;
        } else if (methodC.equals(Method.HEAD.getMethodBytes())) {
            return Method.HEAD;
        } else if (methodC.equals(Method.PUT.getMethodBytes())) {
            return Method.PUT;
        } else if (methodC.equals(Method.DELETE.getMethodBytes())) {
            return Method.DELETE;
        } else if (methodC.equals(Method.TRACE.getMethodBytes())) {
            return Method.TRACE;
        } else if (methodC.equals(Method.CONNECT.getMethodBytes())) {
            return Method.CONNECT;
        } else if (methodC.equals(Method.OPTIONS.getMethodBytes())) {
            return Method.OPTIONS;
        } else if (methodC.equals(Method.PATCH.getMethodBytes())) {
            return Method.PATCH;
        } else if (methodC.equals(Method.PRI.getMethodBytes())) {
            return Method.PRI;
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
        } else if (method.equals(Method.PRI.getMethodString())) {
            return Method.PRI;
        } else {
            return CUSTOM(method.toString());
        }
    }

    private final String methodString;
    private final byte[] methodBytes;

    private final PayloadExpectation payloadExpectation;
    
    private Method(final String methodString,
            final PayloadExpectation payloadExpectation) {
        this.methodString = methodString;
        try {
            this.methodBytes = methodString.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            // Should never get here
            throw new IllegalStateException(e);
        }
        
        this.payloadExpectation = payloadExpectation;
    }

    public String getMethodString() {
        return methodString;
    }

    public byte[] getMethodBytes() {
        return methodBytes;
    }

    public PayloadExpectation getPayloadExpectation() {
        return payloadExpectation;
    }

    @Override
    public String toString() {
        return methodString;
    }

    public boolean matchesMethod(final String method) {
        return (this.methodString.equals(method));
    }
}
