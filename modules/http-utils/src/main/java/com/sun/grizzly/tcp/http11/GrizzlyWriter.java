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
import java.io.PrintWriter;


/**
 * Coyote implementation of the servlet writer.
 * 
 * @author Remy Maucherat
 */
public class GrizzlyWriter
    extends PrintWriter {


    // -------------------------------------------------------------- Constants


    private static final char[] LINE_SEP = { '\r', '\n' };


    // ----------------------------------------------------- Instance Variables


    protected GrizzlyOutputBuffer ob;
    protected boolean error = false;


    // ----------------------------------------------------------- Constructors


    public GrizzlyWriter(GrizzlyOutputBuffer ob) {
        super(ob);
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

    /**
     * Recycle.
     */
    void recycle() {
        error = false;
    }


    // --------------------------------------------------------- Writer Methods


    @Override
    public void flush() {

        if (error)
            return;

        try {
            ob.flush();
        } catch (IOException e) {
            error = true;
        }

    }

    @Override
    public void close() {

        // We don't close the PrintWriter - super() is not called,
        // so the stream can be reused. We close ob.
        try {
            ob.close();
        } catch (IOException ex ) {
            ;
        }
        error = false;

    }


    @Override
    public boolean checkError() {
        flush();
        return error;
    }


    @Override
    public void write(int c) {

        if (error)
            return;

        try {
            ob.write(c);
        } catch (IOException e) {
            error = true;
        }

    }


    @Override
    public void write(char buf[], int off, int len) {

        if (error)
            return;

        try {
            ob.write(buf, off, len);
        } catch (IOException e) {
            error = true;
        }

    }


    @Override
    public void write(char buf[]) {
	write(buf, 0, buf.length);
    }


    @Override
    public void write(String s, int off, int len) {

        if (error)
            return;

        try {
            ob.write(s, off, len);
        } catch (IOException e) {
            error = true;
        }

    }


    @Override
    public void write(String s) {
        write(s, 0, s.length());
    }


    // ---------------------------------------------------- PrintWriter Methods


    @Override
    public void print(boolean b) {
        if (b) {
            write("true");
        } else {
            write("false");
        }
    }


    @Override
    public void print(char c) {
        write(c);
    }


    @Override
    public void print(int i) {
        write(String.valueOf(i));
    }


    public void print(long l) {
        write(String.valueOf(l));
    }


    @Override
    public void print(float f) {
        write(String.valueOf(f));
    }


    @Override
    public void print(double d) {
        write(String.valueOf(d));
    }


    @Override
    public void print(char s[]) {
        write(s);
    }


    @Override
    public void print(String s) {
        if (s == null) {
            s = "null";
        }
        write(s);
    }


    @Override
    public void print(Object obj) {
        write(String.valueOf(obj));
    }


    @Override
    public void println() {
        write(LINE_SEP);
    }


    @Override
    public void println(boolean b) {
        print(b);
        println();
    }


    @Override
    public void println(char c) {
        print(c);
        println();
    }


    @Override
    public void println(int i) {
        print(i);
        println();
    }


    @Override
    public void println(long l) {
        print(l);
        println();
    }


    @Override
    public void println(float f) {
        print(f);
        println();
    }


    @Override
    public void println(double d) {
        print(d);
        println();
    }


    @Override
    public void println(char c[]) {
        print(c);
        println();
    }


    @Override
    public void println(String s) {
        print(s);
        println();
    }


    @Override
    public void println(Object o) {
        print(o);
        println();
    }

    public GrizzlyOutputBuffer getOutputBuffer(){
        return ob;
    }
}
