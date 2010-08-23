/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2008 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.http;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.http.util.BufferChunk;


/**
 * Lazy cookie implementation, which is based on preparsed Grizzly {@link Buffer}s.
 * The {@link String} representation will be created on demand.
 * 
 *  Allows recycling and uses Buffer as low-level
 *  representation ( and thus the byte-> char conversion can be delayed
 *  until we know the charset ).
 *
 *  Tomcat.core uses this recyclable object to represent cookies,
 *  and the facade will convert it to the external representation.
 */
public class LazyCookieState {
    // Version 0 (Netscape) attributes
    private BufferChunk name = BufferChunk.newInstance();
    private BufferChunk value = BufferChunk.newInstance();
    // Expires - Not stored explicitly. Generated from Max-Age (see V1)
    private BufferChunk path = BufferChunk.newInstance();
    private BufferChunk domain = BufferChunk.newInstance();
    private boolean secure;
    // Version 1 (RFC2109) attributes
    private BufferChunk comment = BufferChunk.newInstance();


    // Note: Servlet Spec =< 2.5 only refers to Netscape and RFC2109,
    // not RFC2965
    // Version 1 (RFC2965) attributes
    // TODO Add support for CommentURL
    // Discard - implied by maxAge <0
    // TODO Add support for Port
    public LazyCookieState() {
    }

    public void recycle() {
        path.recycle();
        name.recycle();
        value.recycle();
        comment.recycle();
        path.recycle();
        domain.recycle();
        secure = false;
    }

    public BufferChunk getComment() {
        return comment;
    }

    public BufferChunk getDomain() {
        return domain;
    }

    public BufferChunk getPath() {
        return path;
    }

    public void setSecure(boolean flag) {
        secure = flag;
    }

    public boolean getSecure() {
        return secure;
    }

    public BufferChunk getName() {
        return name;
    }

    public BufferChunk getValue() {
        return value;
    }

    // -------------------- utils --------------------
    @Override
    public String toString() {
        return "LazyCookieState " + getName() + "=" + getValue() + " ; "
                + " " + getPath() + " " + getDomain();
    }
}
