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

import java.io.InputStream;
import java.io.Reader;

/**
 * <p>
 * This interface defines methods to allow an {@link InputStream} or
 * {@link Reader} to notify the developer <em>when</em> and <em>how much</em>
 * data is ready to be read without blocking.
 * </p>
 *
 * @since 2.0
 */
public interface InputSource {


    /**
     * <p>
     * Notify the specified {@link ReadHandler} when any number of bytes or
     * characters can be read without blocking.
     * </p>
     *
     * <p>
     * Invoking this method is equivalent to calling: notifyAvailable(handler, 1).
     * </p>
     *
     * @param handler the {@link ReadHandler} to notify.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked or if all request
     *  data has already been read.
     *
     * @see ReadHandler#onDataAvailable()
     * @see ReadHandler#onAllDataRead()
     */
    void notifyAvailable(final ReadHandler handler);


    /**
     * <p>
     * Notify the specified {@link ReadHandler} when the number of bytes that
     * can be read without blocking is greater or equal to the specified
     * <code>size</code>.
     * </p>
     *
     * @param handler the {@link ReadHandler} to notify.
     * @param size the least number of bytes that must be available before
     *  the {@link ReadHandler} is invoked.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>,
     *  or if <code>size</code> is less or equal to zero.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked or if all request
     *  data has already been read.
     *
     * @see ReadHandler#onDataAvailable()
     * @see ReadHandler#onAllDataRead()
     */
    void notifyAvailable(final ReadHandler handler, final int size);


    /**
     * @return <code>true</code> when all data for this particular request
     *  has been read, otherwise returns <code>false</code>.
     */
    boolean isFinished();


    /**
     * @return the number of bytes (or characters) that may be obtained
     *  without blocking.  Note when dealing with characters, this method
     *  may return an estimate on the number of characters available. 
     */
    int readyData();


    /**
     * @return <code>true</code> if data can be obtained without blocking,
     *  otherwise returns <code>false</code>.
     */
    boolean isReady();

}
