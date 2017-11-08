/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

/**
 *
 * @author oleksiys
 */
public class ServletWriterImpl extends PrintWriter {

    // -------------------------------------------------------------- Constants
    // No need for a do privileged block - every web app has permission to read
    // this by default
    private static final char[] LINE_SEP =
            System.getProperty("line.separator").toCharArray();
    // ----------------------------------------------------- Instance Variables
    protected Writer ob;
    protected boolean error = false;

    // ----------------------------------------------------------- Constructors
    public ServletWriterImpl(Writer ob) {
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

        if (error) {
            return;
        }

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
        } catch (IOException ignored) {
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

        if (error) {
            return;
        }

        try {
            ob.write(c);
        } catch (IOException e) {
            error = true;
        }

    }

    @Override
    public void write(char buf[], int off, int len) {

        if (error) {
            return;
        }

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

        if (error) {
            return;
        }

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

    @Override
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

//    public NIOWriter getOutputBuffer() {
//        return (NIOWriter) ob;
//    }
}
