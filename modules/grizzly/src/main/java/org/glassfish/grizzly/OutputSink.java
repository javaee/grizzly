/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

/**
 * <p>
 * This interface defines methods to allow an {@link java.io.OutputStream} or
 * {@link java.io.Writer} to allow the developer to check with the runtime
 * whether or not it's possible to write a certain amount of data, or if it's
 * not possible, to be notified when it is.
 * </p>
 *
 * @since 2.0
 */
public interface OutputSink {


    /**
     * Instructs the <code>OutputSink</code> to invoke the provided
     * {@link WriteHandler} when it is possible to write more bytes (or characters).
     *
     * Note that once the {@link WriteHandler} has been notified, it will not
     * be considered for notification again at a later point in time. 
     *
     * @param handler the {@link WriteHandler} that should be notified
     *  when it's possible to write more data.
     *
     * @throws IllegalStateException if this method is invoked and a handler
     *  from a previous invocation is still present (due to not having yet been
     *  notified).  
     * 
     * @since 2.3
     */
    void notifyCanWrite(final WriteHandler handler);

    /**
     * Instructs the <code>OutputSink</code> to invoke the provided
     * {@link WriteHandler} when it is possible to write <code>length</code>
     * bytes (or characters).
     *
     * Note that once the {@link WriteHandler} has been notified, it will not
     * be considered for notification again at a later point in time. 
     *
     * @param handler the {@link WriteHandler} that should be notified
     *  when it's possible to write <code>length</code> bytes.
     * @param length the number of bytes or characters that require writing.
     *
     * @throws IllegalStateException if this method is invoked and a handler
     *  from a previous invocation is still present (due to not having yet been
     *  notified).
     * 
     * @deprecated the <code>length</code> parameter will be ignored. Pls. use {@link #notifyCanWrite(org.glassfish.grizzly.WriteHandler)}.
     */
    @Deprecated
    void notifyCanWrite(final WriteHandler handler, final int length);

    /**
     * @return <code>true</code> if a write to this <code>OutputSink</code>
     *  will succeed, otherwise returns <code>false</code>.
     * 
     * @since 2.3
     */
    boolean canWrite();

    /**
     * @param length specifies the number of bytes (or characters) that require writing
     *
     * @return <code>true</code> if a write to this <code>OutputSink</code>
     *  will succeed, otherwise returns <code>false</code>.
     * 
     * @deprecated the <code>length</code> parameter will be ignored. Pls. use {@link #canWrite()}.
     */
    @Deprecated
    boolean canWrite(final int length);

}
