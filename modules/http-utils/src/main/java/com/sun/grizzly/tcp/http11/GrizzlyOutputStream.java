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


package com.sun.grizzly.tcp.http11;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Coyote implementation of the servlet output stream.
 * 
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class GrizzlyOutputStream extends OutputStream{


    // ----------------------------------------------------- Instance Variables


    protected GrizzlyOutputBuffer ob;


    // ----------------------------------------------------------- Constructors


    public GrizzlyOutputStream(GrizzlyOutputBuffer ob) {
        this.ob = ob;
    }
    
    
    // --------------------------------------------------------- Public Methods


    /**
    * Prevent cloning the facade.
    */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

  
    // -------------------------------------------------------- Package Methods


    /**
     * Clear facade.
     */
    void clear() {
        ob = null;
    }


    // --------------------------------------------------- OutputStream Methods


    public void write(int i)
        throws IOException {
        ob.writeByte(i);
    }


    @Override
    public void write(byte[] b)
        throws IOException {
        write(b, 0, b.length);
    }


    @Override
    public void write(byte[] b, int off, int len)
        throws IOException {
        ob.write(b, off, len);
    }


    /**
     * Will send the buffer to the client.
     */
    @Override
    public void flush()
        throws IOException {
        ob.flush();
    }


    @Override
    public void close()
        throws IOException {
        ob.close();
    }


    public void print(String s)
        throws IOException {
        ob.write(s);
    }


}

