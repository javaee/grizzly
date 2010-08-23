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

package com.sun.grizzly;

/**
 * <p>
 * This class implement the "Chain of Responsibility" pattern (for more info, 
 * take a look at the classic "Gang of Four" design patterns book). Towards 
 * that end, the Chain API models a computation as a series of "protocol filter"
 * that can be combined into a "protocol chain". 
 * </p><p>
 * The API for ProtocolFilter consists of a two methods (execute() and 
 * postExecute) which is passed a "protocol context" parameter containing the 
 * dynamic state of the computation, and whose return value is a boolean 
 * that determines whether or not processing for the current chain has been 
 * completed (false), or whether processing should be delegated to the next 
 * ProtocolFilter in the chain (true). The owning ProtocolChain  must call the
 * postExectute() method of each ProtocolFilter in a ProtocolChain in reverse 
 * order of the invocation of their execute() methods.
 * </p><p>
 * The following picture describe how it ProtocolFilter(s) 
 * </p><p><pre><code>
 * -----------------------------------------------------------------------------
 * - ProtocolFilter1.execute() --> ProtocolFilter2.execute() -------|          -
 * -                                                                |          -
 * -                                                                |          -
 * -                                                                |          -
 * - ProtocolFilter1.postExecute() <-- ProtocolFilter2.postExecute()|          -    
 * -----------------------------------------------------------------------------
 * </code></pre></p><p>
 * The "context" abstraction is designed to isolate ProtocolFilter
 * implementations from the environment in which they are run 
 * (such as a ProtocolFilter that can be used in either IIOP or HTTP parsing, 
 * without being tied directly to the API contracts of either of these 
 * environments). For ProtocolFilter that need to allocate resources prior to 
 * delegation, and then release them upon return (even if a delegated-to 
 * ProtocolFilter throws an exception), the "postExecute" method can be used 
 * for cleanup. 
 * </p>
 * @author Jeanfrancois Arcand
 */
public interface ProtocolChain{
    /**
     * {@link ProtocolChain} result attribute name.
     */
    public static final String PROTOCOL_CHAIN_POST_INSTRUCTION = 
            "ChainPostInstruction";
    
    /**
     * Add a {@link ProtocolFilter} to the list. {@link ProtocolFilter}
     * will be invoked in the order they have been added.
     * @param protocolFilter {@link ProtocolFilter}
     * @return {@link ProtocolFilter} added successfully (yes/no) ?
     */
    public boolean addFilter(ProtocolFilter protocolFilter);
    
    
    /**
     * Remove the {@link ProtocolFilter} from this chain.
     * @param theFilter {@link ProtocolFilter} 
     * @return {@link ProtocolFilter} removed successfully (yes/no) ?
     */
    public boolean removeFilter(ProtocolFilter theFilter);
    
     
    /**
     * Insert a {@link ProtocolFilter} to the list at position 'pos'.
     * @param pos The insertion position 
     * @param protocolFilter {@link ProtocolFilter}
     */
    public void addFilter(int pos, ProtocolFilter protocolFilter);

    
    /**
     * Execute using the {@link Context} instance.
     * @param context <code>Context<code>
     * @throws java.lang.Exception 
     */
    public void execute(Context context) throws Exception;
    
  
}
