/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.util.concurrent.Future;

/**
 * Grizzly {@link Future} implementation.
 * Users can register additional {@link CompletionHandler}s using
 * {@link #addCompletionHandler(org.glassfish.grizzly.CompletionHandler)}
 * to be notified once the asynchronous computation, represented by
 * this <tt>Future</tt>, is complete.
 * 
 * A <tt>GrizzlyFuture</tt> instance can be recycled and reused.
 * 
 * @param <R> the result type
 * 
 * @author Alexey Stashok
 */
public interface GrizzlyFuture<R> extends Future<R>, Cacheable {
    /**
     * Adds a {@link CompletionHandler}, which will be notified once the
     * asynchronous computation, represented by this <tt>Future</tt>,
     * is complete.
     * 
     * @param completionHandler {@link CompletionHandler}
     * @since 2.3.4
     */
    void addCompletionHandler(CompletionHandler<R> completionHandler);
    
    /**
     * Mark <tt>GrizzlyFuture</tt> as recyclable, so once result will come -
     * <tt>GrizzlyFuture</tt> object will be recycled and returned to a
     * thread local object pool.
     * You can consider to use this method, if you're not interested in using
     * this <tt>GrizzlyFuture</tt> object.
     * 
     * @param recycleResult if <tt>true</tt> - the <tt>GrizzlyFuture</tt> result,
     * if it support recyclable mechanism, will be also recycled together
     * with this <tt>GrizzlyFuture</tt> object.
     * 
     * @deprecated
     */
    void markForRecycle(boolean recycleResult);

    /**
     * Recycle <tt>GrizzlyFuture</tt> now.
     * This method could be used, if you're not interested in using this
     * <tt>GrizzlyFuture</tt> object, and you're sure this object is not used
     * by any other application part.
     *
     * @param recycleResult if <tt>true</tt> - the <tt>GrizzlyFuture</tt> result,
     * if it support recyclable mechanism, will be also recycled together
     * with this <tt>GrizzlyFuture</tt> object.
     */
    void recycle(boolean recycleResult);
}
