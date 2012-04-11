/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp;

import com.sun.grizzly.tcp.http11.InternalOutputBuffer;
import com.sun.grizzly.tcp.http11.filters.VoidOutputFilter;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.http.MimeHeaders;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Locale;
import java.nio.channels.SocketChannel;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;


/**
 * Response object.
 * 
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Harish Prabandham
 * @author Hans Bergsten <hans@gefionsoftware.com>
 * @author Remy Maucherat
 */
public class Response<A> {


    // ----------------------------------------------------------- Constructors


    public Response() {
    }


    // ----------------------------------------------------- Class Variables

    /**
     * Default locale as mandated by the spec.
     */
    private static Locale DEFAULT_LOCALE = Locale.getDefault();
    
    
    // --------------------------------------------------- Suspend/Resume ---- /
    
    private final ReentrantLock lock = new ReentrantLock();

    // ----------------------------------------------------- Instance Variables

    /**
     * Status code.
     */
    protected int status = 200;


    /**
     * Status message.
     */
    protected String message = null;


    /**
     * Response headers.
     */
    protected MimeHeaders headers = new MimeHeaders();


    /**
     * Associated output buffer.
     */
    protected OutputBuffer outputBuffer;


    /**
     * Notes.
     */
    private NotesManagerImpl notesManager = new NotesManagerImpl();


    /**
     * Committed flag.
     */
    protected boolean commited = false;


    /**
     * Action hook.
     */
    public ActionHook hook;


    /**
     * HTTP specific fields.
     */
    protected String contentType = null;
    protected String contentLanguage = null;
    protected String characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
    // START SJSAS 6316254
    private String quotedCharsetValue = characterEncoding;
    // END SJSAS 6316254
    protected long contentLength = -1;
    private Locale locale = DEFAULT_LOCALE;

    // General informations
    private long bytesWritten=0;

    /**
     * Holds request error exception.
     */
    protected Exception errorException = null;

    /**
     * Has the charset been explicitly set.
     */
    protected boolean charsetSet = false;

    /**
     * Request error URI.
     */
    protected String errorURI = null;

    protected Request req;

    // The underlying {@link SelectionKey}
    private SelectionKey selectionKey;

    // Is the request suspended.
    private volatile boolean isSuspended = false;
    
    // The ResponseAttachment associated with this response.    
    private ResponseAttachment ra;

    // Disable Grizzly's auto-detection of remote closed connection.
    public final static boolean discardDisconnectEvent =
            Boolean.getBoolean("com.sun.grizzly.discardDisconnect");

    private boolean allowCustomReasonPhrase = true;
    
    
    // ------------------------------------------------------------- Properties

    public Request getRequest() {
        return req;
    }

    public void setRequest( Request req ) {
        this.req=req;
    }

    public OutputBuffer getOutputBuffer() {
        return outputBuffer;
    }


    public void setOutputBuffer(OutputBuffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }


    public MimeHeaders getMimeHeaders() {
        return headers;
    }


    public ActionHook getHook() {
        return hook;
    }


    public void setHook(ActionHook hook) {
        this.hook = hook;
    }


    // -------------------- Per-Response "notes" --------------------



    public final void setNote(int pos, Object value) {
	notesManager.setNote(pos,value);
    }


    public final Object getNote(int pos) {
	return notesManager.getNote(pos);
    }


    public NotesManagerImpl getNotesManager() {
        return notesManager;
    }

    
    public void setNotesManager(NotesManagerImpl notesManager) {
        this.notesManager = notesManager;
    }
    
    
    // -------------------- Actions --------------------


    public void action(ActionCode actionCode, Object param) {
        if (hook != null) {
            if( param==null ) 
                hook.action(actionCode, this);
            else
                hook.action(actionCode, param);
        }
    }


    // -------------------- State --------------------


    public int getStatus() {
        return status;
    }

    
    /** 
     * Set the response status 
     */ 
    public void setStatus( int status ) {
        this.status = status;
    }


    /**
     * Get the status message.
     */
    public String getMessage() {
        return message;
    }


    /**
     * Set the status message.
     */
    public void setMessage(String message) {
        this.message = message;
    }


    public boolean isCommitted() {
        return commited;
    }


    public void setCommitted(boolean v) {
        commited = v;
    }


    // -----------------Error State --------------------


    /** 
     * Set the error Exception that occurred during
     * request processing.
     */
    public void setErrorException(Exception ex) {
        errorException = ex;
    }


    /** 
     * Get the Exception that occurred during request
     * processing.
     */
    public Exception getErrorException() {
        return errorException;
    }


    public boolean isExceptionPresent() {
        return errorException != null;
    }


    /** 
     * Set request URI that caused an error during
     * request processing.
     */
    public void setErrorURI(String uri) {
        errorURI = uri;
    }


    /** Get the request URI that caused the original error.
     */
    public String getErrorURI() {
        return errorURI;
    }

    public boolean isAllowCustomReasonPhrase() {
        return allowCustomReasonPhrase;
    }

    public void setAllowCustomReasonPhrase(boolean allowCustomReasonPhrase) {
        this.allowCustomReasonPhrase = allowCustomReasonPhrase;
    }

    // -------------------- Methods --------------------
    
    
    public void reset() throws IllegalStateException {
        
        // Reset the headers only if this is the main request,
        // not for included
        contentType = null;
        locale = DEFAULT_LOCALE;
        contentLanguage = null;
        characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
        // START SJSAS 6316254
        quotedCharsetValue = characterEncoding;
        // END SJSAS 6316254
        contentLength = -1;
        charsetSet = false;

        status = 200;
        message = null;
        headers.clear();
        isSuspended = false;
        
        // Force the PrintWriter to flush its data to the output
        // stream before resetting the output stream
        //
        // Reset the stream
        if (commited) {
            //String msg = sm.getString("servletOutputStreamImpl.reset.ise"); 
            throw new IllegalStateException();
        }
        
        action(ActionCode.ACTION_RESET, this);
    }
    
    
    /**
     * Discard any write operations on the {@link Response}. Invoking that method
     * prevent any write operation to be fluxhed on the network.
     */
    public void discardUpstreamWrites() throws IllegalStateException {
        action(ActionCode.ACTION_DISCARD_UPSTREAM_WRITE, this);       
    }
    
    public void flush() throws IOException {
        action(ActionCode.ACTION_CLIENT_FLUSH, this);
    }

    public void finish() throws IOException {
        action(ActionCode.ACTION_CLOSE, this);
    }


    public void acknowledge() throws IOException {
        action(ActionCode.ACTION_ACK, this);
    }


    // -------------------- Headers --------------------
    public boolean containsHeader(String name) {
        return headers.getHeader(name) != null;
    }


    public void setHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        headers.setValue(name).setString( value);
    }


    public void addHeader(String name, String value) {
        char cc=name.charAt(0);
        if( cc=='C' || cc=='c' ) {
            if( checkSpecialHeader(name, value) )
            return;
        }
        headers.addValue(name).setString( value );
    }

    
    /** 
     * Set internal fields for special header names. 
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader( String name, String value) {
        // XXX Eliminate redundant fields !!!
        // ( both header and in special fields )
        if("Content-Type".equalsIgnoreCase(name)) {
            setContentType( value );
            return true;
        }
        if("Content-Length".equalsIgnoreCase(name)) {
            try {
                int cL=Integer.parseInt( value );
                setContentLength( cL );
                return true;
            } catch( NumberFormatException ex ) {
                // Do nothing - the spec doesn't have any "throws" 
                // and the user might know what he's doing
                return false;
            }
        }
        if("Content-Language".equalsIgnoreCase(name)) {
            // XXX XXX Need to construct Locale or something else
        }
        return false;
    }


    /** 
     *  Write all headers into our internal buffer but don't flush them to the 
     * client yet. You must invoke {@link #flush} in order to make that operation.
     */
    public void sendHeaders() throws IOException {
        action(ActionCode.ACTION_COMMIT, this);
        commited = true;
    }
    
    /** 
     *  Write all headers into our internal buffer and flush the to the client.
     */
    public void flushHeaders() throws IOException {
        action(ActionCode.ACTION_CLOSE, this);
    }

    // -------------------- I18N --------------------


    public Locale getLocale() {
        return locale;
    }

    /**
     * Called explicitely by user to set the Content-Language and
     * the default encoding
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.getLanguage();
        if (contentLanguage != null && contentLanguage.length() > 0) {
            String country = locale.getCountry();
            StringBuilder value = new StringBuilder(contentLanguage);
            if (country != null && country.length() > 0) {
                value.append('-');
                value.append(country);
            }
            contentLanguage = value.toString();
        }

    }

    /**
     * Return the content language.
     */
    public String getContentLanguage() {
        return contentLanguage;
    }

    /*
     * Overrides the name of the character encoding used in the body
     * of the response. This method must be called prior to writing output
     * using getWriter().
     *
     * @param charset String containing the name of the chararacter encoding.
     */
    public void setCharacterEncoding(String charset) {

        if (isCommitted())
            return;
        if (charset == null)
            return;

        characterEncoding = charset;
        // START SJSAS 6316254
        quotedCharsetValue = charset;
        // END SJSAS 6316254
        charsetSet=true;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    /**
     * Sets the content type.
     *
     * This method must preserve any response charset that may already have 
     * been set via a call to response.setContentType(), response.setLocale(),
     * or response.setCharacterEncoding().
     *
     * @param type the content type
     */
    public void setContentType(String type) {

        int semicolonIndex = -1;

        if (type == null) {
            contentType = null;
            return;
        }

        /*
         * Remove the charset param (if any) from the Content-Type, and use it
         * to set the response encoding.
         * The most recent response encoding setting will be appended to the
         * response's Content-Type (as its charset param) by getContentType();
         */
        boolean hasCharset = false;
        int len = type.length();
        int index = type.indexOf(';');
        while (index != -1) {
            semicolonIndex = index;
            index++;
            while (index < len && Character.isSpace(type.charAt(index))) {
                index++;
            }
            if (index+8 < len
                    && type.charAt(index) == 'c'
                    && type.charAt(index+1) == 'h'
                    && type.charAt(index+2) == 'a'
                    && type.charAt(index+3) == 'r'
                    && type.charAt(index+4) == 's'
                    && type.charAt(index+5) == 'e'
                    && type.charAt(index+6) == 't'
                    && type.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = type.indexOf(';', index);
        }

        if (!hasCharset) {
            contentType = type;
            return;
        }

        contentType = type.substring(0, semicolonIndex);
        String tail = type.substring(index+8);
        int nextParam = tail.indexOf(';');
        String charsetValue;
        if (nextParam != -1) {
            contentType += tail.substring(nextParam);
            charsetValue = tail.substring(0, nextParam);
        } else {
            charsetValue = tail;
        }

        // The charset value may be quoted, but must not contain any quotes.
        if (charsetValue != null && charsetValue.length() > 0) {
            charsetSet=true;
            // START SJSAS 6316254
            quotedCharsetValue = charsetValue;
            // END SJSAS 6316254
            characterEncoding = charsetValue.replace('"', ' ').trim();
        }
    }

    public String getContentType() {

        String ret = contentType;

        if (ret != null 
                /* SJSAS 6316254
                && characterEncoding != null
                */
                // START SJSAS 6316254
                && quotedCharsetValue != null
                // END SJSAS 6316254
                && charsetSet) {
            /* SJSAS 6316254
            ret = ret + ";charset=" + characterEncoding;   
            */
            // START SJSAS 6316254
            ret = ret + ";charset=" + quotedCharsetValue;
            // END SJSAS 6316254
        }

        return ret;
    }
    
    public void setContentLength(int contentLength) {
        this.contentLength = contentLength;
    }

    
    public int getContentLength() {
        return (int)contentLength;
    }
    
    
    public void setContentLengthLong(long contentLength) {
        this.contentLength = contentLength;
    }
    
    
    public long getContentLengthLong() {
        return contentLength;
    }

    /**
     * Write a chunk of bytes.
     */
    public void doWrite(ByteChunk chunk/*byte buffer[], int pos, int count*/)
        throws IOException{
        
        if (isSuspended){
            action(ActionCode.RESET_SUSPEND_TIMEOUT,this);
        }
        
        outputBuffer.doWrite(chunk, this);
        bytesWritten+=chunk.getLength();
    }

    // --------------------
    
    public void recycle() {
        
//        selectionKey = null;
        contentType = null;
        contentLanguage = null;
        locale = DEFAULT_LOCALE;
        characterEncoding = Constants.DEFAULT_CHARACTER_ENCODING;
        // START SJSAS 6316254
        quotedCharsetValue = characterEncoding;
        // END SJSAS 6316254
        charsetSet = false;
        contentLength = -1;
        status = 200;
        message = null;
        commited = false;
        errorException = null;
        errorURI = null;
        headers.clear();

        isSuspended = false;
        ra = null;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public void setBytesWritten(long bytesWritten) {
        this.bytesWritten = bytesWritten;
    }


    /**
     * Removes any Set-Cookie response headers whose value contains the
     * string "JSESSIONID=" or "JSESSIONIDSSO="
     */
    public void removeSessionCookies() {
        headers.removeHeader("Set-Cookie", Constants.SESSION_COOKIE_PATTERN);
    }

    /**
     * Set the underlying {@link SelectionKey}
     */
    public void setSelectionKey(SelectionKey selectionKey) {
	this.selectionKey = selectionKey;
    }

    /**
     * Return the underlying {@link SelectionKey}
     */
    public SelectionKey getSelectionKey() {
	return selectionKey;
    }
    
    /**
     * Return the underlying {@link SocketChannel} 
     * <strong>WARNING</strong>. If you directly use the {@link SocketChannel}, 
     * you must make sure {@link Response#sendHeaders} followed by a {@link Response#flush()}
     * if  you just want to manipulate the response body, but not the header. 
     * If you don't want to let Grizzly write the headers for you,
     * Invoke {@link Response#setCommitted(boolean)} with true before starting writing bytes
     * to the {@link SocketChannel} 
     */
    public SocketChannel getChannel(){
	return (SocketChannel) selectionKey.channel();
    }

    /**
     * Complete the {@link Response} and finish/commit it. If a 
     * {@link CompletionHandler} has been defined, its {@link CompletionHandler#resumed}
     * will first be invoked, then the {@link Response#setCommitted(boolean)} followed
     * by {@link Response#finish()}. Those operations commit the response.
     */
    public void resume(){
        lock.lock();
        try {
            if (!isSuspended){
                throw new IllegalStateException("Not Suspended");
            }
            
            isSuspended = false;

            req.action(ActionCode.CANCEL_SUSPENDED_RESPONSE, null);

            if (SuspendResponseUtils.removeSuspendedInCurrentThread()) {
                // It's possible that the response was resumed within the
                // same thread that invoked suspend().  In this case, invoke
                // the completion handler but do not finish the response as
                // this response is going to exit normally.
                ra.invokeCompletionHandler();
                return;
            }

            ra.resume();
        } finally {
            ra = null;
            lock.unlock();
        }
        
        req.action(ActionCode.ACTION_FINISH_RESPONSE, null);
    }
    
    /**
     * Cancel the {@link Response} and finish/commit it. If a 
     * {@link CompletionHandler} has been defined, its {@link CompletionHandler#cancelled}
     * will first be invoked, then the {@link Response#setCommitted(boolean)} followed
     * by {@link Response#finish()}. Those operations commit the response.
     */   
    public void cancel(){
        lock.lock();
        try {
            if (!isSuspended){
                throw new IllegalStateException("Not Suspended");
            }

            isSuspended = false;
            
            req.action(ActionCode.CANCEL_SUSPENDED_RESPONSE, null);  
            if (SuspendResponseUtils.removeSuspendedInCurrentThread()) {
                // It's possible that the response was cancelled within the
                // same thread that invoked suspend().  In this case, invoke
                // cancel, but do not finish the response as this response is
                // going to exit normally.
                ra.cancel();
                return;
            }

            ra.cancel();
        } finally {
            ra = null;
            lock.unlock();
        }
        
        req.action(ActionCode.ACTION_FINISH_RESPONSE, null);        
    }
        
    /**
     * Return <tt>true<//tt> if that {@link Response#suspend()} has been 
     * invoked and set to <tt>true</tt>
     * @return <tt>true<//tt> if that {@link Response#suspend()} has been 
     * invoked and set to <tt>true</tt>
     */
    public boolean isSuspended(){
        return isSuspended;
    }
       
    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response.
     */
    public void suspend(){
        suspend(Long.MAX_VALUE);
    }

    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response.
     * 
     * @param timeout The maximum amount of time, in milliseconds, 
     * a {@link Response} can be suspended. When the timeout expires (because 
     * nothing has been written or because the {@link Response#resume()} 
     * or {@link Response#cancel()}), the {@link Response} will be automatically
     * resumed and commited. Usage of any methods of a {@link Response} that
     * times out will throw an {@link IllegalStateException}.
     *        
     */   
    public void suspend(long timeout){
        suspend(timeout,null,null);
    }
    
    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response. When the 
     * {@link Response#resume()} is invoked, the container will make sure {@link CompletionHandler#resumed}
     * is invoked with the original <tt>attachement</tt>. When the 
     * {@link Response#cancel()} is invoked, the container will make sure {@link CompletionHandler#cancelled}
     * is invoked with the original <tt>attachement</tt>. If the timeout expires, the 
     * {@link CompletionHandler#cancelled} is invoked with the original <tt>attachement</tt> and 
     * the {@link Response} commited.
     * 
     * @param timeout The maximum amount of time, in milliseconds, 
     * a {@link Response} can be suspended. When the timeout expires (because 
     * nothing has been written or because the {@link Response#resume()} 
     * or {@link Response#cancel()}), the {@link Response} will be automatically
     * resumed and commited. Usage of any methods of a {@link Response} that
     * times out will throw an {@link IllegalStateException}.
     * @param attachment Any Object that will be passed back to the {@link CompletionHandler}        
     * @param competionHandler a {@link CompletionHandler}

     */     
    public void suspend(long timeout,A attachment, 
            CompletionHandler<? super A> competionHandler){          
        suspend(timeout, attachment, competionHandler, null);
    }
    
    
    /**
     * Suspend the {@link Response}. Suspending a {@link Response} will
     * tell the underlying container to avoid recycling objects associated with
     * the current instance, and also to avoid commiting response. When the 
     * {@link Response#resume()} is invoked, the container will make sure {@link CompletionHandler#resumed}
     * is invoked with the original <tt>attachement</tt>. When the 
     * {@link Response#cancel()} is invoked, the container will make sure {@link CompletionHandler#cancelled}
     * is invoked with the original <tt>attachement</tt>. If the timeout expires, the 
     * {@link CompletionHandler#cancelled} is invoked with the original <tt>attachement</tt> and 
     * the {@link Response} commited.
     * 
     * @param timeout The maximum amount of time, in milliseconds, 
     * a {@link Response} can be suspended. When the timeout expires (because 
     * nothing has been written or because the {@link Response#resume()} 
     * or {@link Response#cancel()}), the {@link Response} will be automatically
     * resumed and commited. Usage of any methods of a {@link Response} that
     * times out will throw an {@link IllegalStateException}.
     * @param attachment Any Object that will be passed back to the {@link CompletionHandler}        
     * @param competionHandler a {@link CompletionHandler}
     * @param ra {@link ResponseAttachment} used to times out idle connection.
     */     
    public void suspend(long timeout, A attachment,
            CompletionHandler<? super A> competionHandler, ResponseAttachment<A> ra) {
        lock.lock();

        try {
            if (isSuspended) {
                throw new IllegalStateException("Already Suspended");
            }
            isSuspended = true;

            if (ra == null) {
                ra = new ResponseAttachment(timeout, attachment, competionHandler, this);
            }

            ra.lock = lock;
            if (competionHandler == null) {
                competionHandler = new CompletionHandler() {

                    public void resumed(Object attachment) {
                        if (LoggerUtils.getLogger().isLoggable(Level.FINE)) {
                            LoggerUtils.getLogger().fine(Response.this
                                    + " resumed" + attachment);
                        }
                    }

                    public void cancelled(Object attachment) {
                        if (LoggerUtils.getLogger().isLoggable(Level.FINE)) {
                            LoggerUtils.getLogger().fine(Response.this
                                    + " cancelled" + attachment);
                        }
                    }
                };
                ra.completionHandler = competionHandler;
            }
            this.ra = ra;

            SuspendResponseUtils.attach(selectionKey, ra);
            SuspendResponseUtils.setSuspendedInCurrentThread();
        } finally {
            lock.unlock();
        }
    }
    
    
    /**
     * Return the {@link ResponseAttachment} associated with this instance, or
     * null if the {@link Response#isSuspended()} return false.
     * @return
     */
    public ResponseAttachment getResponseAttachment(){
        return ra;
    }
    
    
    public static class ResponseAttachment<A> implements SelectionKeyAttachment.KeySelectionListener,
                       SelectionKeyAttachment.TimeOutListener {

        private volatile CompletionHandler<? super A> completionHandler;
        private final A attachment;
        private final Response response;
        protected volatile ReentrantLock lock;
        private volatile long idleTimeoutDelay;

        protected volatile ThreadAttachment threadAttachment;
        
        protected ResponseAttachment(long idleTimeoutDelay, A attachment,
                CompletionHandler<? super A> completionHandler, Response response) {
            this.attachment = attachment;
            this.completionHandler = completionHandler;
            this.response = response;
            this.idleTimeoutDelay = validateIdleTimeoutDelay(idleTimeoutDelay);
        }

        public A getAttachment() {
            return attachment;
        }

        public CompletionHandler<? super A> getCompletionHandler() {
            return completionHandler;
        }

        public void resetTimeout() {
            final ThreadAttachment localThreadAttachment = threadAttachment;
            if (localThreadAttachment != null) {
                localThreadAttachment.setTimeout(System.currentTimeMillis());
            }
        }

        public long getTimeout() {
            final ThreadAttachment localThreadAttachment = threadAttachment;
            if (localThreadAttachment != null) {
                return localThreadAttachment.getTimeout();
            }

            return SelectionKeyAttachment.UNLIMITED_TIMEOUT;
        }

        public long getIdleTimeoutDelay() {
            return idleTimeoutDelay;
        }

        public void setIdleTimeoutDelay(long idleTimeoutDelay) {
            this.idleTimeoutDelay = validateIdleTimeoutDelay(idleTimeoutDelay);
            SuspendResponseUtils.updateIdleTimeOutDelay(response.selectionKey,
                                                        response.getResponseAttachment());
        }

        void setThreadAttachment(ThreadAttachment threadAttachment) {
            this.threadAttachment = threadAttachment;
        }

        public void invokeCompletionHandler() {
            completionHandler.resumed(attachment);
        }

        public void resume() {
            invokeCompletionHandler();
            try {
                response.sendHeaders();
                response.flush();
                response.finish();
            } catch (IOException ex) {
                if (LoggerUtils.getLogger().isLoggable(Level.FINE)) {
                    LoggerUtils.getLogger().log(Level.FINEST, "resume", ex);
                }
            }
        }

        public final boolean onTimeOut(SelectionKey key) {
            lock.lock();
            try {
                SuspendResponseUtils.detach(key);
                if (!timeout()) {
                    SuspendResponseUtils.attach(key, this);
                }
            } finally {
                lock.unlock();
            }
            return false;
        }

        public void onKeySelected(SelectionKey selectionKey) {
            if (!selectionKey.isValid() || discardDisconnectEvent) {
                selectionKey.cancel();
                return;
            }
            boolean connectionClosed = true;
            try {
                connectionClosed = ((SocketChannel) selectionKey.channel()).read(ByteBuffer.allocate(1)) == -1;
            } catch (IOException ex) {
                if (LoggerUtils.getLogger().isLoggable(Level.FINE)) {
                    LoggerUtils.getLogger().log(Level.FINEST, "handleSelectionKey", ex);
                }
            } finally {
                if (connectionClosed) {
                    completionHandler.cancelled(attachment);
                    selectionKey.cancel();
                }
            }
        }

        /**
         * Method will be called to notify about async HTTP processing timeout
         * @return <tt>true</tt>, if async processing has been finished, or <tt>false</tt>
         * if we should re-register the channel to continue async HTTP request processing
         */
        public boolean timeout() {
            // If the buffers are empty, commit the response header
            try {
                cancel();
            } finally {
                if (!response.isCommitted()) {
                    try {
                        response.sendHeaders();
                        response.flush();
                        response.finish();
                    } catch (IOException ex) {
                        // Swallow?
                    }
                }
            }

            return true;
        }

        public void cancel() {
            completionHandler.cancelled(attachment);
        }
        
        private static long validateIdleTimeoutDelay(final long idleTimeoutDelay) {
            if (idleTimeoutDelay <= 0) {
                return SelectionKeyAttachment.UNLIMITED_TIMEOUT;
    }
    
            return idleTimeoutDelay;
        }
    }
        
        
    /**
     * Add a {@link ResponseFilter}, which will be called every bytes are
     * ready to be written.
     */
    public void addResponseFilter(final ResponseFilter responseFilter) {
        if (outputBuffer instanceof InternalOutputBuffer) {
            ((InternalOutputBuffer) outputBuffer).addLastOutputFilter(
                    new VoidOutputFilter() {

                        @Override
                        public int doWrite(ByteChunk chunk, Response res)
                                throws IOException {
                            responseFilter.filter(chunk);
                            buffer.doWrite(chunk, res);
                            return chunk.getLength();
                        }
                    });
        } else {
            throw new IllegalStateException("Not Supported");
        }
    }
    
}
