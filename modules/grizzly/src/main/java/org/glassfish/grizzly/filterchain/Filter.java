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

package org.glassfish.grizzly.filterchain;

import java.io.IOException;
import org.glassfish.grizzly.attributes.Attribute;

/**
 * A Filter encapsulates a unit of processing work to be performed, 
 * whose purpose is to examine and/or modify the state of a transaction that is 
 * represented by a {@link FilterChainContext}. Individual Filter can be assembled
 * into a {@link FilterChain}, which allows them to either complete the required 
 * processing or delegate further processing to the next Filter in the 
 * {@link FilterChain}.
 *
 * Filter implementations should be designed in a thread-safe manner, 
 * suitable for inclusion in multiple {@link FilterChain} that might be 
 * processed by different threads simultaneously. In general, this implies that 
 * Filter classes should not maintain state information in instance variables. 
 * Instead, state information should be maintained via suitable modifications to
 * the attributes of the {@link FilterChainContext} that is passed to the 
 * {@link Filter#execute(FilterChainContext, NextAction)},
 * {@link Filter#postexecute(FilterChainContext, NextAction)} methods.
 *
 * Filter implementations typically retrieve and store state information
 * in the {@link FilterChainContext} instance that is passed as a parameter to the 
 * {@link Filter#execute(FilterChainContext, NextAction)},
 * {@link Filter#postexecute(FilterChainContext, NextAction)} methods.
 * using particular {@link Attribute}s that can
 * be acquired via {@link Attribute#get(FilterChainContext)} method.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public interface Filter {
    
    /**
     * Execute a unit of processing work to be performed. This {@link Filter}
     * may either complete the required processing, or delegate remaining
     * processing to the next {@link Filter} in a {@link FilterChain} containing
     * this {@link Filter}, by returning correspondent {@link NextAction} object.
     * @param ctx {@link FilterChainContext}
     * @param nextAction
     * @return {@link NextAction}
     * @throws java.io.IOException 
     */
    public NextAction execute(FilterChainContext ctx, NextAction nextAction)
            throws IOException;
    
    
    /**
     * Execute any cleanup activities, such as releasing resources that were 
     * acquired during the {@link Filter#execute()} method of this
     * {@link Filter} instance.
     * @param ctx {@link FilterChainContext}
     * @return {@link NextAction}
     * @throws java.io.IOException 
     */
    public NextAction postExecute(FilterChainContext ctx, NextAction nextAction)
            throws IOException;

    
    /**
     * Notification about exception, occured on the {@link FilterChain}
     * 
     * @param ctx event processing {@link FilterChainContext}
     * @param error error, which occurred during <tt>FilterChain</tt> execution
     */
    public void exceptionOccurred(FilterChainContext ctx, Throwable error);
    
    /**
     * Defines if this {@link Filter} could be indexed by a {@link FilterChain}
     * in order to find neighbour {@link Filter}s faster.
     * Most of the time it's very desired for a {@link Filter}s to be indexable,
     * but there are cases, when it's not appropriate, for example if single
     * {@link Filter} instance should be shared among several
     * {@link FilterChain}s.
     *
     * @return true, if this {@link Filter} is indexable, or false otherwise.
     */
    public boolean isIndexable();
    
    /**
     * Gets the {@link Filter} index.
     * 
     * @return the {@link Filter} index.
     */
    public int getIndex();
    
    /**
     * Sets the {@link Filter} index.
     * 
     * @param index the {@link Filter} index.
     */
    public void setIndex(int index);
}
