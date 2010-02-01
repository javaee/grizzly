/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.asyncqueue;

import com.sun.grizzly.Interceptor;
import java.util.concurrent.Future;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.Transformer;

/**
 * {@link AsyncQueue} element unit
 * 
 * @author Alexey Stashok
 */
public class AsyncQueueRecord<R> {
    protected final Object originalMessage;
    protected Object message;
    protected final Future future;
    protected final R currentResult;
    protected final CompletionHandler completionHandler;
    protected final Transformer transformer;
    protected final Interceptor<R> interceptor;

    public AsyncQueueRecord(Object originalMessage, Future future,
            R currentResult, CompletionHandler completionHandler,
            Transformer transformer, Interceptor<R> interceptor) {

        this.originalMessage = originalMessage;
        this.message = originalMessage;
        this.future = future;
        this.currentResult = currentResult;
        this.completionHandler = completionHandler;
        this.transformer = transformer;
        this.interceptor = interceptor;
    }

    public Object getOriginalMessage() {
        return originalMessage;
    }

    public final Object getMessage() {
        return message;
    }

    public final void setMessage(Object message) {
        this.message = message;
    }

    public final Future getFuture() {
        return future;
    }

    public final R getCurrentResult() {
        return currentResult;
    }

    public final CompletionHandler getCompletionHandler() {
        return completionHandler;
    }

    public final Transformer getTransformer() {
        return transformer;
    }

    public final Interceptor<R> getInterceptor() {
        return interceptor;
    }
}
