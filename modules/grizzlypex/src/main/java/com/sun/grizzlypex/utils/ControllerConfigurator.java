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
/*
 * ControllerConfigurator.java
 */

package com.sun.grizzlypex.utils;

import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.ProtocolChainInstanceHandler;
import com.sun.grizzly.ProtocolFilter;
import com.sun.grizzly.SelectorHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexey Stashok
 */
public class ControllerConfigurator {
    private int readThreadsCount = 1;
    private ProtocolChainInstanceHandler protocolChainInstanceHandler;
    private List<ProtocolFilter> protocolFilters = new ArrayList<ProtocolFilter>(4);
    private Collection<SelectorHandler> selectorHandlers = new ArrayList<SelectorHandler>(4);
    
    /** Constructor */
    public ControllerConfigurator() {
    }
    
    public int getReadThreadsCount() {
        return readThreadsCount;
    }
    
    public void setReadThreadsCount(int readThreadsCount) {
        this.readThreadsCount = readThreadsCount;
    }
    
    public void addProtocolFilter(ProtocolFilter protocolFilter) {
        protocolFilters.add(protocolFilter);
    }
    
    public void removeProtocolFilter(ProtocolFilter protocolFilter) {
        protocolFilters.remove(protocolFilter);
    }
    
    public void addSelectorHandler(SelectorHandler selectorHandler) {
        selectorHandlers.add(selectorHandler);
    }
    
    public void setSelectorHandlers(Collection<SelectorHandler> selectorHandlers) {
        this.selectorHandlers = selectorHandlers;
    }
    
    public void removeSelectorHandler(SelectorHandler selectorHandler) {
        selectorHandlers.remove(selectorHandler);
    }

    public ProtocolChainInstanceHandler getProtocolChainInstanceHandler() {
        return protocolChainInstanceHandler;
    }

    public void setProtocolChainInstanceHandler(ProtocolChainInstanceHandler protocolChainInstanceHandler) {
        this.protocolChainInstanceHandler = protocolChainInstanceHandler;
    }

    public Controller createController() {
        final Controller controller = new Controller();
        
        for(SelectorHandler selectorHandler : selectorHandlers) {
            controller.setSelectorHandler(selectorHandler);
        }
        
        if (protocolChainInstanceHandler != null) {
            controller.setProtocolChainInstanceHandler(protocolChainInstanceHandler);
        } else {
            controller.setProtocolChainInstanceHandler(
                    createDefaultProtocolChainInstanceHandler(protocolFilters));
        }
        
        controller.setReadThreadsCount(readThreadsCount);
        
        return controller;
    }
    
    private static ProtocolChainInstanceHandler createDefaultProtocolChainInstanceHandler(final Collection<ProtocolFilter> filters) {
        return new DefaultProtocolChainInstanceHandler(){
            @Override
            public ProtocolChain poll() {
                ProtocolChain protocolChain = protocolChains.poll();
                if (protocolChain == null){
                    protocolChain = new DefaultProtocolChain();
                    for(ProtocolFilter filter : filters) {
                        protocolChain.addFilter(filter);
                    }
                }
                return protocolChain;
            }
        };
    }
}
