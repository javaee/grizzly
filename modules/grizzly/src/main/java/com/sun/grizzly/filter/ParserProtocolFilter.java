/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
 */

package com.sun.grizzly.filter;

import com.sun.grizzly.Context;
import com.sun.grizzly.Context.AttributeScope;
import com.sun.grizzly.Controller;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstruction;
import com.sun.grizzly.ProtocolParser;
import com.sun.grizzly.SSLConfig;
import com.sun.grizzly.util.AttributeHolder;
import com.sun.grizzly.util.WorkerThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.ThreadAttachment.Mode;
import java.util.logging.Level;

/**
 * Simple ProtocolFilter implementation which read the available bytes
 * and delegate the decision of reading more bytes or not to a ProtocolParser.
 * The ProtocolParser will decide if more bytes are required before continuing
 * the invokation of the ProtocolChain.
 *
 * @author Jeanfrancois Arcand
 */
public abstract class ParserProtocolFilter extends ReadFilter {
    private static final String SSL_READ_RESULT = "PPF-SSL-ReadResult";

    /**
     * Should ParserProtocolFilter read the data from channel
     */
    protected boolean isSkipRead;

    /**
     * if set {@link SSLReadFilter} is used instead of {@link ReadFilter} for reading bytes
     */
    private SSLReadFilter sslReadFilter;

    public ParserProtocolFilter() {
    }

    /**
     * Configures this Filter to be used with SSL. Internally the reading of bytes
     * will be delegated to {@link SSLReadFilter}
     * @param sslConfig Configuration of SSL Settings
     */
    public void setSSLConfig(SSLConfig sslConfig) {
        if(sslConfig==null) {
            this.sslReadFilter=null;
            return;
        }
        sslReadFilter = new  SSLReadFilter();
        sslReadFilter.configure(sslConfig);
    }


    /**
     * Read available bytes and delegate the processing of them to the next
     * ProtocolFilter in the ProtocolChain.
     * @return <tt>true</tt> if the next ProtocolFilter on the ProtocolChain
     * need to be invoked.
     */
    @Override
    public boolean execute(Context ctx) throws IOException {
        ProtocolParser parser = null;

        // Remove Parser from Connection related attributes (if it was there)

        AttributeHolder connectionAttrs =
                ctx.getAttributeHolderByScope(AttributeScope.CONNECTION);
        if (connectionAttrs != null) {
            parser = (ProtocolParser) connectionAttrs.removeAttribute(ProtocolParser.PARSER);
        }

        if (parser == null) {
            parser = (ProtocolParser) ctx.getAttribute(ProtocolParser.PARSER);

            if (parser == null) {
                parser = newProtocolParser();
                ctx.setAttribute(ProtocolParser.PARSER, parser);
            }
        } else {
            ctx.setAttribute(ProtocolParser.PARSER, parser);
        }

        boolean isExpectingMoreData = parser.isExpectingMoreData();
        if (isExpectingMoreData || !parser.hasMoreBytesToParse()) {
            if (isExpectingMoreData) {
                // Saved ByteBuffer was restored. May be we will not need it next time
                ((WorkerThread) Thread.currentThread()).updateAttachment(
                        (sslReadFilter==null)?Mode.ATTRIBUTES_ONLY:Mode.SSL_ENGINE);
            }


            boolean continueExecution = isSkipRead || superExecute(ctx);
            WorkerThread workerThread = (WorkerThread) Thread.currentThread();
            ByteBuffer byteBuffer = workerThread.getByteBuffer();
            parser.startBuffer(byteBuffer);
            if (!continueExecution) {
                return continueExecution;
            }
        }
        if (!parser.hasNextMessage()) {
            return false;
        }

        return invokeProtocolParser(ctx, parser);
    }

    /**
     * Invoke the {@link ProtocolParser}. If more bytes are required,
     * register the {@link SelectionKey} back to its associated
     * {@link SelectorHandler}
     * @param ctx the Context object.
     * @return <tt>true</tt> if no more bytes are needed.
     */
    protected boolean invokeProtocolParser(Context ctx, ProtocolParser parser) {

        if (parser == null) {
            throw new IllegalStateException("ProcotolParser cannot be null");
        }

        Object o = parser.getNextMessage();
        ctx.setAttribute(ProtocolParser.MESSAGE, o);
        return true;
    }

    @Override
    public boolean postExecute(Context context) throws IOException {
        ProtocolParser parser = (ProtocolParser) context.getAttribute(ProtocolParser.PARSER);

        if (parser == null) {
            return true;
        }

        if (parser.hasMoreBytesToParse()) {
            // Need to say that we read successfully since bytes are left
            context.setAttribute(ProtocolChain.PROTOCOL_CHAIN_POST_INSTRUCTION,
                    ProtocolChainInstruction.REINVOKE);
            return true;
        }

        if (context.getKeyRegistrationState() !=
                Context.KeyRegistrationState.CANCEL) {

            SelectionKey key = context.getSelectionKey();
            if (parser.isExpectingMoreData()) {
                if (parser.releaseBuffer()) {
                    saveParser(key, parser);
                }

                saveByteBuffer(key);

                // Register to get more bytes.
                superPostExecute(context);
                return false;
            }

            if (parser.releaseBuffer()) {
                saveParser(key, parser);
            }
        } else {
            parser.releaseBuffer();
        }

        return superPostExecute(context);
    }

    private boolean superExecute(Context context) throws IOException {
        if (sslReadFilter == null) {
            return super.execute(context);
        } else {
            final boolean result = sslReadFilter.execute(context);
            if (result) {
                // Save "success read" state
                context.setAttribute(SSL_READ_RESULT, true);
            }

            return result;
        }
    }

    private boolean superPostExecute(Context context) throws IOException {
        if (sslReadFilter == null) {
            return super.postExecute(context);
        } else {
            Boolean readResult = (Boolean) context.removeAttribute(SSL_READ_RESULT);
            if (readResult != null && Boolean.TRUE.equals(readResult)) {
                ByteBuffer secureBuffer = ((WorkerThread) Thread.currentThread()).getInputBB();
                // if "success read" state is set and securedIn buffer has more bytes
                if (secureBuffer != null && secureBuffer.position() > 0) {
                    // Reinvoke the chain
                    context.setAttribute(ProtocolChain.PROTOCOL_CHAIN_POST_INSTRUCTION,
                            ProtocolChainInstruction.REINVOKE);
                    return true;
                }
            }

            return sslReadFilter.postExecute(context);
        }
    }
    /**
     * Return a new or cached ProtocolParser instance.
     */
    public abstract ProtocolParser newProtocolParser();

    private void saveByteBuffer(SelectionKey key) {
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        // Detach the current Thread data.
        ThreadAttachment threadAttachment = (sslReadFilter==null)? workerThread.updateAttachment(Mode.BYTE_BUFFER):
                 workerThread.updateAttachment(Mode.SSL_ARTIFACTS);

        // Attach it to the SelectionKey so the it can be resumed latter.
        key.attach(threadAttachment);
    }

    private void saveParser(SelectionKey key, ProtocolParser parser) {
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        // Detach the current Thread data.
        ThreadAttachment threadAttachment = workerThread.getAttachment();
        threadAttachment.setAttribute(ProtocolParser.PARSER, parser);
        // Attach it to the SelectionKey so the it can be resumed latter.
        key.attach(threadAttachment);
    }

    /**
     * Method returns true, if this Filter perform channel read operation on
     * execute, or false - Filter assumes the data was read by previous Filters in
     * a chain.
     * 
     * @return true, if Filter will perform channel read operation on execute.
     * False - Filter assumes the data was read by previous Filters in a chain. 
     */
    protected boolean isSkipRead() {
        return isSkipRead;
    }

    /**
     * Method set if this Filter should perform channel read operation on
     * execute, or should assumes the data was read by previous Filters in
     * a chain.
     * 
     * @param isSkipRead If isSkipRead is true, this Filter will perform 
     * channel read operation on execute. If false - Filter assumes the data 
     * was read by previous Filters in a chain.
     */
    protected void setSkipRead(boolean isSkipRead) {
        this.isSkipRead = isSkipRead;
    }
}
