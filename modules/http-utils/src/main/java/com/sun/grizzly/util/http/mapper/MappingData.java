

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 
package com.sun.grizzly.util.http.mapper;

import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;

/**
 * Mapping data.
 *
 * @author Remy Maucherat
 */
public class MappingData {

    public Object host = null;
    public Object context = null;
    public Object wrapper = null;
    public boolean jspWildCard = false;
    // START GlassFish 1024
    public boolean isDefaultContext = false;
    // END GlassFish 1024

    public MessageBytes contextPath = MessageBytes.newInstance();
    public MessageBytes requestPath = MessageBytes.newInstance();
    public MessageBytes wrapperPath = MessageBytes.newInstance();
    public MessageBytes pathInfo = MessageBytes.newInstance();

    public MessageBytes redirectPath = MessageBytes.newInstance();

    public void recycle() {
        host = null;
        context = null;
        wrapper = null;
        pathInfo.recycle();
        requestPath.recycle();
        wrapperPath.recycle();
        contextPath.recycle();
        redirectPath.recycle();
        jspWildCard = false;
        // START GlassFish 1024
        isDefaultContext = false;
        // END GlassFish 1024
    }

}
