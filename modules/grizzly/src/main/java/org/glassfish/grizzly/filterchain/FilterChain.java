/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.filterchain;

import java.io.IOException;
import java.util.List;
import org.glassfish.grizzly.*;

/**
 * <p>
 * This class implement the "Chain of Responsibility" pattern (for more info, 
 * take a look at the classic "Gang of Four" design patterns book). Towards 
 * that end, the Chain API models a computation as a series of "protocol filter"
 * that can be combined into a "protocol chain". 
 * </p><p>
 * The API for Filter consists of a two set of methods (handleXXX() and
 * postXXX) which is passed a "protocol context" parameter containing the
 * dynamic state of the computation, and whose return value is a
 * {@link NextAction} that instructs <tt>FilterChain</tt>, how it should
 * continue processing. The owning ProtocolChain  must call the
 * postXXX() method of each Filter in a FilterChain in reverse
 * order of the invocation of their handleXXX() methods.
 * </p><p>
 * The following picture describe how it Filter(s) 
 * </p><p><pre><code>
 * -----------------------------------------------------------------------------
 * - Filter1.handleXXX() --> Filter2.handleXXX()                    |          -
 * -                                                                |          -
 * -                                                                |          -
 * -                                                                |          -
 * - Filter1.postXXX() <-- Filter2.postXXX()                        |          -
 * -----------------------------------------------------------------------------
 * </code></pre></p><p>
 * The "context" abstraction is designed to isolate Filter
 * implementations from the environment in which they are run 
 * (such as a Filter that can be used in either IIOP or HTTP parsing, 
 * without being tied directly to the API contracts of either of these 
 * environments). For Filter that need to allocate resources prior to 
 * delegation, and then release them upon return (even if a delegated-to 
 * Filter throws an exception), the "postXXX" method can be used
 * for cleanup. 
 * </p>
 *
 * @see Filter
 * @see Codec
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public interface FilterChain extends Processor<Context>, List<Filter> {
    FilterChainContext obtainFilterChainContext(Connection connection);
    FilterChainContext obtainFilterChainContext(Connection connection, Closeable closeable);
    FilterChainContext obtainFilterChainContext(Connection connection,
            int startIdx, int endIdx, int currentIdx);
    FilterChainContext obtainFilterChainContext(Connection connection,
            Closeable closeable,
            int startIdx, int endIdx, int currentIdx);

    /**
     * Get the index of {@link Filter} in chain, which type is filterType, or
     * <tt>-1</tt> if the {@link Filter} of required type was not found.
     * 
     * @param filterType the type of {@link Filter} to search.
     * @return the index of {@link Filter} in chain, which type is filterType, or
     * <tt>-1</tt> if the {@link Filter} of required type was not found.
     */
    int indexOfType(final Class<? extends Filter> filterType);

    /**
     * Method processes occurred {@link IOEvent} on this {@link FilterChain}.
     *
     * @param context processing context
     * @return {@link ProcessorResult}
     */
    ProcessorResult execute(FilterChainContext context);

    void flush(Connection connection,
            CompletionHandler<WriteResult> completionHandler);

    void fireEventUpstream(Connection connection,
            FilterChainEvent event,
            CompletionHandler<FilterChainContext> completionHandler);
    
    void fireEventDownstream(Connection connection,
            FilterChainEvent event,
            CompletionHandler<FilterChainContext> completionHandler);

    ReadResult read(FilterChainContext context) throws IOException;

    void fail(FilterChainContext context, Throwable failure);
}
