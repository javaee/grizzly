

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

package com.sun.grizzly.tcp;

import java.io.IOException;

import com.sun.grizzly.util.buf.ByteChunk;


/**
 * Input buffer.
 *
 * This class is used only in the protocol implementation. All reading from
 * tomcat ( or adapter ) should be done using Request.doRead().
 * 
 * @author Remy Maucherat
 */
public interface InputBuffer {


    /** Return from the input stream.
        IMPORTANT: the current model assumes that the protocol will 'own' the
        buffer and return a pointer to it in ByteChunk ( i.e. the param will
        have chunk.getBytes()==null before call, and the result after the call ).
    */
    public int doRead(ByteChunk chunk, Request request) 
        throws IOException;


}
