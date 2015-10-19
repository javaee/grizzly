/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.utils.Futures;

/**
 *
 * @author oleksiys
 */
public final class ReusableFuture<V> implements FutureImpl<V> {
    private volatile FutureImpl<V> innerFuture;

    public ReusableFuture() {
        reset();
    }

    @Override
    public void addCompletionHandler(final CompletionHandler<V> completionHandler) {
        innerFuture.addCompletionHandler(completionHandler);
    }

    protected void reset() {
        innerFuture = Futures.createSafeFuture();
    }

    @Override
    public void result(V result) {
        innerFuture.result(result);
    }

    @Override
    public void failure(Throwable failure) {
        innerFuture.failure(failure);
    }

    @Override
    public void recycle(boolean recycleResult) {
        innerFuture.recycle(recycleResult);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return innerFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return innerFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
        return innerFuture.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return innerFuture.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return innerFuture.get(timeout, unit);
    }

    @Override
    public void recycle() {
        innerFuture = null;
    }

    @Override
    public V getResult() {
        return innerFuture.getResult();
    }

    @Override
    public void markForRecycle(boolean recycleResult) {
        innerFuture.markForRecycle(recycleResult);
    }
}
