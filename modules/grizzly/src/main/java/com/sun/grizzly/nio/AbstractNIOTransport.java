/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 */

package com.sun.grizzly.nio;

import com.sun.grizzly.AbstractTransport;
import com.sun.grizzly.Connection;
import java.io.IOException;
import java.nio.channels.Selector;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractNIOTransport extends AbstractTransport
        implements NIOTransport {
    protected SelectorHandler selectorHandler;
    protected SelectionKeyHandler selectionKeyHandler;

    protected int selectorRunnersCount;
    
    protected SelectorRunner[] selectorRunners;
    
    protected NIOChannelDistributor nioChannelDistributor;

    public AbstractNIOTransport(String name) {
        super(name);
    }

    public SelectionKeyHandler getSelectionKeyHandler() {
        return selectionKeyHandler;
    }

    public void setSelectionKeyHandler(SelectionKeyHandler selectionKeyHandler) {
        this.selectionKeyHandler = selectionKeyHandler;
    }

    public SelectorHandler getSelectorHandler() {
        return selectorHandler;
    }

    public void setSelectorHandler(SelectorHandler selectorHandler) {
        this.selectorHandler = selectorHandler;
    }

    public int getSelectorRunnersCount() {
        return selectorRunnersCount;
    }

    public void setSelectorRunnersCount(int selectorRunnersCount) {
        this.selectorRunnersCount = selectorRunnersCount;
    }

    
    protected void startSelectorRunners() throws IOException {
        selectorRunners = new SelectorRunner[selectorRunnersCount];
        
        synchronized(selectorRunners) {
            for (int i = 0; i < selectorRunnersCount; i++) {
                SelectorRunner runner =
                        new SelectorRunner(this, SelectorFactory.instance().create());
                runner.start();
                selectorRunners[i] = runner;
            }
        }
    }
    
    protected void stopSelectorRunners() throws IOException {
        if (selectorRunners == null) return;
        
        synchronized(selectorRunners) {
            for (int i = 0; i < selectorRunners.length; i++) {
                SelectorRunner runner = selectorRunners[i];
                if (runner != null) {
                    runner.stop();
                    selectorRunners[i] = null;

                    Selector selector = runner.getSelector();
                    if (selector != null) {
                        try {
                            selector.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
            
            selectorRunners = null;
        }
    }

    public NIOChannelDistributor getNioChannelDistributor() {
        return nioChannelDistributor;
    }

    public void setNioChannelDistributor(NIOChannelDistributor
            nioChannelDistributor) {
        this.nioChannelDistributor = nioChannelDistributor;
    }

    protected SelectorRunner[] getSelectorRunners() {
        return selectorRunners;
    }

    protected abstract void closeConnection(Connection connection)
            throws IOException;
}
