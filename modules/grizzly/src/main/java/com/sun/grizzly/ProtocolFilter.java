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

import java.io.IOException;

/**
 * A ProtocolFilter encapsulates a unit of processing work to be performed, 
 * whose purpose is to examine and/or modify the state of a transaction that is 
 * represented by a ProtocolContext. Individual ProtocolFilter can be assembled
 * into a ProtocolChain, which allows them to either complete the required 
 * processing or delegate further processing to the next ProtocolFilter in the 
 * ProtocolChain.
 *
 * ProtocolFilter implementations should be designed in a thread-safe manner, 
 * suitable for inclusion in multiple ProtocolChains that might be processed by
 * different threads simultaneously. In general, this implies that ProtocolFilter
 * classes should not maintain state information in instance variables. 
 * Instead, state information should be maintained via suitable modifications to
 * the attributes of the ProtocolContext that is passed to the execute() and
 * postExecute() methods.
 *
 * ProtocolFilter implementations typically retrieve and store state information
 * in the ProtocolContext instance that is passed as a parameter to the 
 * execute() and postExecute method, using particular keys into the Map that can
 * be acquired via ProtocolContext.getAttributes(). 
 *
 * @author Jeanfrancois Arcand
 */
public interface ProtocolFilter {
     
    
    public final static String SUCCESSFUL_READ = "succes_read";
    
    
    /**
     * Execute a unit of processing work to be performed. This ProtocolFilter
     * may either complete the required processing and return false, 
     * or delegate remaining processing to the next ProtocolFilter in a 
     * ProtocolChain containing this ProtocolFilter by returning true.
     * @param ctx {@link Context}
     * @return 
     * @throws java.io.IOException 
     */
    public boolean execute(Context ctx) throws IOException;
    
    
    /**
     * Execute any cleanup activities, such as releasing resources that were 
     * acquired during the execute() method of this ProtocolFilter instance.
     * @param ctx {@link Context}
     * @return 
     * @throws java.io.IOException 
     */
    public boolean postExecute(Context ctx) throws IOException;
}
