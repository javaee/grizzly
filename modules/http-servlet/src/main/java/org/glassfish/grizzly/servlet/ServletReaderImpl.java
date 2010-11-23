/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.grizzly.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import org.glassfish.grizzly.http.server.io.NIOReader;

/**
 *
 * @author oleksiys
 */
public class ServletReaderImpl extends BufferedReader {



    // -------------------------------------------------------------- Constants


    private static final char[] LINE_SEP = { '\r', '\n' };
    private static final int MAX_LINE_LENGTH = 4096;


    // ----------------------------------------------------- Instance Variables


    protected NIOReader ib;

    protected char[] lineBuffer = null;


    // ----------------------------------------------------------- Constructors


    public ServletReaderImpl(NIOReader ib) {
        super(ib, 1);
        this.ib = ib;
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
        ib = null;
    }


    // --------------------------------------------------------- Reader Methods


    @Override
    public void close()
        throws IOException {
        ib.close();
    }


    @Override
    public int read()
        throws IOException {
        return ib.read();
    }


    @Override
    public int read(char[] cbuf)
        throws IOException {
        return ib.read(cbuf, 0, cbuf.length);
    }


    @Override
    public int read(char[] cbuf, int off, int len)
        throws IOException {
        return ib.read(cbuf, off, len);
    }


    @Override
    public long skip(long n)
        throws IOException {
        return ib.skip(n);
    }


    @Override
    public boolean ready()
        throws IOException {
        return ib.ready();
    }


    @Override
    public boolean markSupported() {
        return true;
    }


    @Override
    public void mark(int readAheadLimit)
        throws IOException {
        ib.mark(readAheadLimit);
    }


    @Override
    public void reset()
        throws IOException {
        ib.reset();
    }


    @Override
    public String readLine()
        throws IOException {

        if (lineBuffer == null) {
            lineBuffer = new char[MAX_LINE_LENGTH];
        }

        String result = null;

        int pos = 0;
        int end = -1;
        int skip = -1;
        StringBuilder aggregator = null;
        while (end < 0) {
            mark(MAX_LINE_LENGTH);
            while ((pos < MAX_LINE_LENGTH) && (end < 0)) {
                int nRead = read(lineBuffer, pos, MAX_LINE_LENGTH - pos);
                if (nRead < 0) {
                    if (pos == 0 && aggregator == null) {
                        return null;
                    }
                    end = pos;
                    skip = pos;
                }
                for (int i = pos; (i < (pos + nRead)) && (end < 0); i++) {
                    if (lineBuffer[i] == LINE_SEP[0]) {
                        end = i;
                        skip = i + 1;
                        char nextchar;
                        if (i == (pos + nRead - 1)) {
                            nextchar = (char) read();
                        } else {
                            nextchar = lineBuffer[i+1];
                        }
                        if (nextchar == LINE_SEP[1]) {
                            skip++;
                        }
                    } else if (lineBuffer[i] == LINE_SEP[1]) {
                        end = i;
                        skip = i + 1;
                    }
                }
                if (nRead > 0) {
                    pos += nRead;
                }
            }
            if (end < 0) {
                if (aggregator == null) {
                    aggregator = new StringBuilder();
                }
                aggregator.append(lineBuffer);
                pos = 0;
            } else {
                reset();
                skip(skip);
            }
        }

        if (aggregator == null) {
            result = new String(lineBuffer, 0, end);
        } else {
            aggregator.append(lineBuffer, 0, end);
            result = aggregator.toString();
        }

        return result;

    }

    public NIOReader getNIOReader(){
        return ib;
    }

}
