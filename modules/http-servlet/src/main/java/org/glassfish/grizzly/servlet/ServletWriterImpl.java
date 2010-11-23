/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import org.glassfish.grizzly.http.server.io.NIOWriter;

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
    protected NIOWriter ob;
    protected boolean error = false;

    // ----------------------------------------------------------- Constructors
    public ServletWriterImpl(NIOWriter ob) {
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
        } catch (IOException ex) {
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

    public NIOWriter getOutputBuffer() {
        return ob;
    }
}
