/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.ssl;

import com.sun.grizzly.SSLSelectorHandler;
import com.sun.grizzly.http.SelectorThread;
import java.nio.channels.SelectionKey;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.SelectionKeyOP;
import java.nio.channels.ClosedChannelException;

/**
 * <code>SelectorHandler</code> implementation <code>SelectorThread</code> 
 * passes to <code>Controller</code>. It is very similar to 
 * <code>TCPSelectorHandler</code>, however has some difference in preSelect()
 * processing
 * 
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class SSLSelectorThreadHandler extends SSLSelectorHandler {
    private SelectorThread selectorThread;
    
    public SSLSelectorThreadHandler() {}
    
    public SSLSelectorThreadHandler(SelectorThread selectorThread) {
        this.selectorThread = selectorThread;
    }
    
    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        SSLSelectorThreadHandler copyHandler = (SSLSelectorThreadHandler) copy;
        copyHandler.selectorThread = selectorThread;
    }

    public void setSelectorThread(SelectorThread selectorThread) {
        this.selectorThread = selectorThread;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected void onReadOp(SelectionKeyOP selectionKeyOp) 
            throws ClosedChannelException {
        super.onReadOp(selectionKeyOp);
        SelectionKey key = selectionKeyOp.getSelectionKey();
        if (key != null) {
            selectorThread.getKeepAliveCounter().trap(key);
        }
    }
}
