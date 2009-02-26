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

import com.sun.grizzly.tcp.ActionCode;
import com.sun.grizzly.tcp.OutputBuffer;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.C2BConverter;
import com.sun.grizzly.util.buf.CharChunk;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;

/**
 * The buffer used by Tomcat response. This is a derivative of the Tomcat 3.3
 * OutputBuffer, with the removal of some of the state handling (which in 
 * Coyote is mostly the Processor's responsability).
 *
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public class GrizzlyOutputBuffer extends Writer
    implements ByteChunk.ByteOutputChannel, CharChunk.CharOutputChannel {

    // -------------------------------------------------------------- Constants


    public static final String DEFAULT_ENCODING = 
        com.sun.grizzly.tcp.Constants.DEFAULT_CHARACTER_ENCODING;
    public static final int DEFAULT_BUFFER_SIZE = 8*1024;
    static final int debug = 0;


    // The buffer can be used for byte[] and char[] writing
    // ( this is needed to support ServletOutputStream and for
    // efficient implementations of templating systems )
    public final int INITIAL_STATE = 0;
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;


    // ----------------------------------------------------- Instance Variables


    /**
     * The byte buffer.
     */
    private ByteChunk bb;


    /**
     * The chunk buffer.
     */
    private CharChunk cb;


    /**
     * State of the output buffer.
     */
    private int state = 0;


    /**
     * Number of bytes written.
     */
    private int bytesWritten = 0;


    /**
     * Number of chars written.
     */
    private int charsWritten = 0;


    /**
     * Flag which indicates if the output buffer is closed.
     */
    private boolean closed = false;


    /**
     * Do a flush on the next operation.
     */
    private boolean doFlush = false;


    /**
     * Byte chunk used to output bytes.
     */
    private ByteChunk outputChunk = new ByteChunk();


    /**
     * Encoding to use.
     */
    private String enc;


    /**
     * Encoder is set.
     */
    private boolean gotEnc = false;


    /**
     * List of encoders.
     */
    protected HashMap encoders = new HashMap();


    /**
     * Current char to byte converter.
     */
    protected C2BConverter conv;


    /**
     * Associated Coyote response.
     */
    private Response coyoteResponse;


    /**
     * Suspended flag. All output bytes will be swallowed if this is true.
     */
    private boolean suspended = false;


    /**
     * The <code>ByteBuffer</code> used to cache the request.
     */
    private ByteBuffer byteBuffer;

    /**
     * Is the File Cache enabled
     */
    private boolean enableCache = false;
    // ----------------------------------------------------------- Constructors


    /**
     * Default constructor. Allocate the buffer with the default buffer size.
     */
    public GrizzlyOutputBuffer() {

        this(DEFAULT_BUFFER_SIZE);

    }


    // START S1AS8 4861933
    public GrizzlyOutputBuffer(boolean chunkingDisabled) {
        this(DEFAULT_BUFFER_SIZE, chunkingDisabled);
    }
    // END S1AS8 4861933


    /**
     * Alternate constructor which allows specifying the initial buffer size.
     * 
     * @param size Buffer size to use
     */
    public GrizzlyOutputBuffer(int size) {
        // START S1AS8 4861933
        /*
        bb = new ByteChunk(size);
        bb.setLimit(size);
        bb.setByteOutputChannel(this);
        cb = new CharChunk(size);
        cb.setCharOutputChannel(this);
        cb.setLimit(size);
        */
        this(size, false);
        // END S1AS8 4861933
    }


    // START S1AS8 4861933
    public GrizzlyOutputBuffer(int size, boolean chunkingDisabled) {
        bb = new ByteChunk(size);
        if (!chunkingDisabled) {
            bb.setLimit(size);
        }
        bb.setByteOutputChannel(this);
        cb = new CharChunk(size);
        cb.setCharOutputChannel(this);
        if (!chunkingDisabled) {
            cb.setLimit(size);
        }
    }
    // END S1AS8 4861933


    // ------------------------------------------------------------- Properties


    /**
     * Associated Coyote response.
     * 
     * @param coyoteResponse Associated Coyote response
     */
    public void setResponse(Response coyoteResponse) {
	this.coyoteResponse = coyoteResponse;
    }


    /**
     * Get associated Coyote response.
     * 
     * @return the associated Coyote response
     */
    public Response getResponse() {
        return this.coyoteResponse;
    }


    /**
     * Is the response output suspended ?
     * 
     * @return suspended flag value
     */
    public boolean isSuspended() {
        return this.suspended;
    }


    /**
     * Set the suspended flag.
     * 
     * @param suspended New suspended flag value
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Recycle the output buffer.
     */
    public void recycle() {
        state = INITIAL_STATE;
        bytesWritten = 0;
        charsWritten = 0;

        cb.recycle();
        bb.recycle(); 
        closed = false;
        suspended = false;

        if (conv!= null) {
            conv.recycle();
        }

        gotEnc = false;
        enc = null;

        if ( enableCache)
            byteBuffer.clear();

        enableCache = false;
    }


    /**
     * Close the output buffer. This tries to calculate the response size if 
     * the response has not been committed yet.
     * 
     * @throws IOException An underlying IOException occurred
     */
    public void close()
        throws IOException {

        if (closed)
            return;
        if (suspended)
            return;

        if ((!coyoteResponse.isCommitted()) 
            && (coyoteResponse.getContentLength() == -1)) {
            // Flushing the char buffer
            if (state == CHAR_STATE) {
                cb.flushBuffer();
                state = BYTE_STATE;
            }
            // If this didn't cause a commit of the response, the final content
            // length can be calculated
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.setContentLength(bb.getLength());
            }
        }

        doFlush(false);
        closed = true;

        coyoteResponse.finish();

    }


    /**
     * Flush bytes or chars contained in the buffer.
     * 
     * @throws IOException An underlying IOException occurred
     */
    public void flush()
        throws IOException {
        doFlush(true);
    }


    /**
     * Flush bytes or chars contained in the buffer.
     * 
     * @throws IOException An underlying IOException occurred
     */
    protected void doFlush(boolean realFlush)
        throws IOException {

        if (suspended)
            return;

        doFlush = true;
        if (state == CHAR_STATE) {
            cb.flushBuffer();
            bb.flushBuffer();
            state = BYTE_STATE;
        } else if (state == BYTE_STATE) {
            bb.flushBuffer();
        } else if (state == INITIAL_STATE) {
            // If the buffers are empty, commit the response header
            coyoteResponse.sendHeaders();
        }
        doFlush = false;

        if (realFlush) {
            coyoteResponse.action(ActionCode.ACTION_CLIENT_FLUSH, 
                                  coyoteResponse);
            // If some exception occurred earlier, or if some IOE occurred
            // here, notify the servlet with an IOE
            if (coyoteResponse.isExceptionPresent()) {
                throw new ClientAbortException
                    (coyoteResponse.getErrorException());
            }
        }

    }


    // ------------------------------------------------- Bytes Handling Methods


    /** 
     * Sends the buffer data to the client output, checking the
     * state of Response and calling the right interceptors.
     * 
     * @param buf Byte buffer to be written to the response
     * @param off Offset
     * @param cnt Length
     * 
     * @throws IOException An underlying IOException occurred
     */
    public void realWriteBytes(byte buf[], int off, int cnt)
	throws IOException {
        if (closed)
            return;
        if (coyoteResponse == null)
            return;

        // If we really have something to write
        if (cnt > 0) {
            // real write to the adapter
            outputChunk.setBytes(buf, off, cnt);
            try {
                coyoteResponse.doWrite(outputChunk);
            } catch (IOException e) {
                // An IOException on a write is almost always due to
                // the remote client aborting the request.  Wrap this
                // so that it can be handled better by the error dispatcher.
                throw new ClientAbortException(e);
            }
        }

    }


    public void write(byte b[], int off, int len) throws IOException {

        if (suspended)
            return;

        if (state == CHAR_STATE)
            cb.flushBuffer();
        state = BYTE_STATE;
        writeBytes(b, off, len);

    }


    private void writeBytes(byte b[], int off, int len) 
        throws IOException {

        if (closed)
            return;

        if ( enableCache){
            makeSpace(len);
            byteBuffer.put(b, off, len);
        }
        bb.append(b, off, len);
        bytesWritten += len;

        // if called from within flush(), then immediately flush
        // remaining bytes
        if (doFlush) {
            bb.flushBuffer();
        }

    }


    // XXX Char or byte ?
    public void writeByte(int b)
        throws IOException {

        if (suspended)
            return;

        if (state == CHAR_STATE)
            cb.flushBuffer();
        state = BYTE_STATE;

        if ( enableCache){
            makeSpace(1);
            byteBuffer.put( (byte)b );
        }
        
        bb.append( (byte)b );
        bytesWritten++;

    }


    // ------------------------------------------------- Chars Handling Methods


    public void write(int c)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        if ( enableCache){
            makeSpace(1);
            byteBuffer.putChar((char)c);
        }
        cb.append((char) c);
        charsWritten++;

    }


    public void write(char c[])
        throws IOException {

        if (suspended)
            return;

        write(c, 0, c.length);

    }


    public void write(char c[], int off, int len)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        if ( enableCache){
            makeSpace(len);
            byteBuffer.put(new String(c).getBytes(),off,len);
        }
        cb.append(c, off, len);
        charsWritten += len;

    }


    public void write(StringBuffer sb)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        int len = sb.length();
        charsWritten += len;
        if ( enableCache){
            byte[] bytes = sb.toString().getBytes();
            makeSpace(bytes.length);
            byteBuffer.put(bytes);   
        }
        cb.append(sb);

    }


    /**
     * Append a string to the buffer
     */
    public void write(String s, int off, int len)
        throws IOException {

        if (suspended)
            return;

        state=CHAR_STATE;

        charsWritten += len;
        if (s==null)
            s="null";
        if ( enableCache){
            makeSpace(len);
            byteBuffer.put(s.getBytes(),off,len);  
        }
        cb.append( s, off, len );

    }


    public void write(String s)
        throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;
        if (s==null)
            s="null";
        write(s, 0, s.length());

    } 


    public void flushChars()
        throws IOException {
        cb.flushBuffer();
        state = BYTE_STATE;

    }


    public boolean flushCharsNeeded() {
        return state == CHAR_STATE;
    }


    public void setEncoding(String s) {
        enc = s;
    }


    public void realWriteChars(char c[], int off, int len) 
        throws IOException {
        if (!gotEnc)
            setConverter();
        conv.convert(c, off, len);
        conv.flushBuffer();	// ???

    }


    public void checkConverter() 
        throws IOException {

        if (!gotEnc)
            setConverter();

    }


    protected void setConverter() 
        throws IOException {

        if (coyoteResponse != null)
            enc = coyoteResponse.getCharacterEncoding();

        gotEnc = true;
        if (enc == null)
            enc = DEFAULT_ENCODING;
        conv = (C2BConverter) encoders.get(enc);
        if (conv == null) {
            if (System.getSecurityManager() != null){
                try{
                    conv = (C2BConverter)AccessController.doPrivileged(
                            new PrivilegedExceptionAction(){

                                public Object run() throws IOException{
                                    return new C2BConverter(bb, enc);
                                }

                            }
                    );              
                }catch(PrivilegedActionException ex){
                    Exception e = ex.getException();
                    if (e instanceof IOException)
                        throw (IOException)e; 

                }
            } else {
                conv = new C2BConverter(bb, enc);
            }
            encoders.put(enc, conv);

        }
    }

    
    // --------------------  BufferedOutputStream compatibility


    /**
     * Real write - this buffer will be sent to the client
     */
    public void flushBytes()
        throws IOException {

        bb.flushBuffer();

    }


    public int getBytesWritten() {
        return bytesWritten;
    }


    public int getCharsWritten() {
        return charsWritten;
    }


    public int getContentWritten() {
        return bytesWritten + charsWritten;
    }


    /** 
     * True if this buffer hasn't been used ( since recycle() ) -
     * i.e. no chars or bytes have been added to the buffer.  
     */
    public boolean isNew() {
        return (bytesWritten == 0) && (charsWritten == 0);
    }


    public void setBufferSize(int size) {
        if (size > bb.getLimit()) {// ??????
	    bb.setLimit(size);
	}
    }


    public void reset() {

        //count=0;
        bb.recycle();
        bytesWritten = 0;
        cb.recycle();
        charsWritten = 0;
        gotEnc = false;
        enc = null;

        if ( enableCache)
            byteBuffer.clear();

        enableCache = false;
    }


    public int getBufferSize() {
        return bb.getLimit();
    }

    
    /**
     * Enable resource caching.
     */
    public void enableCache(boolean enableCache){
        this.enableCache = enableCache;
        if ( enableCache && byteBuffer == null){
            byteBuffer = ByteBuffer.allocate(bb.getLimit());
        } 
    }
    
    
    /**
     * Return a copy of the cached resources.
     */
    public ByteBuffer getCachedByteBuffer(){
        ByteBuffer cache = ByteBuffer.allocate(byteBuffer.position());
        byteBuffer.flip();
        cache.put(byteBuffer);
        return cache;
    }
    
    
    /**
     * Meke sure we have enough space to store bytes.
     */
    private void makeSpace(int size){
        int capacity = byteBuffer.capacity(); 
        if ( byteBuffer.position() + size > capacity) {
            ByteBuffer tmp = ByteBuffer.allocate(capacity * 2);
            byteBuffer.flip();
            tmp.put(byteBuffer);
            byteBuffer = tmp;
        }
    }    
}
