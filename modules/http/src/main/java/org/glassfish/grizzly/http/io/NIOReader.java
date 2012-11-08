/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.io;


import java.io.Reader;
import java.nio.charset.CharsetDecoder;
import org.glassfish.grizzly.ReadHandler;

/**
 * Character stream implementation used to read character-based request
 * content.
 *
 * @since 2.0
 */
public abstract class NIOReader extends Reader implements NIOInputSource {

    /**
     * <p>
     * Notify the specified {@link ReadHandler} when the <tt>estimate</tt>
     * ({@link CharsetDecoder#averageCharsPerByte()}) on number of characters
     * that can be read without blocking is greater or equal to the specified
     * <code>size</code>.
     * </p>
     * 
     * <tt>Important note</tt>: this method guarantees to notify passed
     * {@link ReadHandler} only if there is at least one character available.
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
    @Override
    public abstract void notifyAvailable(ReadHandler handler, int size);

    /**
     * The same as {@link #ready()}.
     * 
     * @see #ready()
     */
    @Override
    public abstract boolean isReady();

    /**
     * Returns an estimate ({@link CharsetDecoder#averageCharsPerByte()}) on the
     * number of characters that may be read without blocking.
     * 
     * <tt>Important note</tt>: this method guarantees to return a value
     * greater than 0 if and only if there is at least one character available.
     * 
     * @return the estimate on number of characters that may be obtained without
     * blocking.
     */
    @Override
    public abstract int readyData();    
}
