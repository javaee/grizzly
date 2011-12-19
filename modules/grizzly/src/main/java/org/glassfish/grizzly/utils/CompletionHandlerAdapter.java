/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.impl.FutureImpl;

/**
 *
 * @author Alexey Stashok
 */
public class CompletionHandlerAdapter<A, B>
        implements CompletionHandler<B> {

    private final static GenericAdapter DIRECT_ADAPTER = new GenericAdapter() {
        @Override
        public Object adapt(final Object result) {
            return result;
        }
    };

    private final GenericAdapter<B, A> adapter;
    private final FutureImpl<A> future;
    private final CompletionHandler<A> completionHandler;

    public CompletionHandlerAdapter(FutureImpl<A> future) {
        this(future, null);
    }

    public CompletionHandlerAdapter(FutureImpl<A> future,
            CompletionHandler<A> completionHandler) {
        this(future, completionHandler, null);
    }

    public CompletionHandlerAdapter(FutureImpl<A> future,
            CompletionHandler<A> completionHandler,
            GenericAdapter<B, A> adapter) {
        this.future = future;
        this.completionHandler = completionHandler;
        if (adapter != null) {
            this.adapter = adapter;
        } else {
            this.adapter = getDirectAdapter();
        }
    }


    @Override
    public void cancelled() {
        if (completionHandler != null) {
            completionHandler.cancelled();
        }
        
        if (future != null) {
            future.cancel(false);            
        }        
    }

    @Override
    public void failed(Throwable throwable) {
        if (completionHandler != null) {
            completionHandler.failed(throwable);
        }
        
        if (future != null) {
            future.failure(throwable);
        }
    }

    @Override
    public void completed(B result) {
        final A adaptedResult = adapt(result);

        if (completionHandler != null) {
            completionHandler.completed(adaptedResult);
        }
        
        if (future != null) {
            future.result(adaptedResult);
        }        
    }

    @Override
    public void updated(B result) {
        final A adaptedResult = adapt(result);

        if (completionHandler != null) {
            completionHandler.updated(adaptedResult);
        }
    }

    protected A adapt(B result) {
        return adapter.adapt(result);
    }
    
    @SuppressWarnings("unchecked")
    private static <K, V> GenericAdapter<K, V> getDirectAdapter() {
        return DIRECT_ADAPTER;
    }
}
