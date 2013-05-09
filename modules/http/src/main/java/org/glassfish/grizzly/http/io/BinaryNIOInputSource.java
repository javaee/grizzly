/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.InputSource;

/**
 * Adds the ability for binary based {@link org.glassfish.grizzly.InputSource}s to obtain the
 * incoming {@link org.glassfish.grizzly.Buffer} directly without having to
 * use intermediate objects to copy the data to.
 *
 * @since 2.0
 */
public interface BinaryNIOInputSource extends InputSource {

    /**
     * <p>
     * Returns the  the duplicate of the underlying
     * {@link org.glassfish.grizzly.Buffer} that backs this
     * <code>InputSource</code>. The content of the returned buffer will be
     * that of the underlying buffer. Changes to returned buffer's content will
     * be visible in the underlying buffer, and vice versa; the two buffers'
     * position, limit, and mark values will be independent.
     * </p>
     *
     * @return the duplicate of the underlying
     * {@link org.glassfish.grizzly.Buffer} that backs this
     *  <code>InputSource</code>.
     */
    Buffer getBuffer();

    /**
     * <p>
     * Returns the underlying {@link org.glassfish.grizzly.Buffer} that backs this
     *  <code>InputSource</code>. Unlike {@link #getBuffer()}, this method
     *  detaches the returned {@link Buffer}, so user becomes responsible for
     *  handling the {@link Buffer}'s life-cycle.
     * </p>
     *
     * @return the underlying {@link org.glassfish.grizzly.Buffer} that backs this
     *  <code>InputSource</code>.
     */
    Buffer readBuffer();
    
    /**
     * <p>
     * Returns the {@link org.glassfish.grizzly.Buffer} of a given size,
     * which represents a chunk of the underlying
     * {@link org.glassfish.grizzly.Buffer} that backs this
     *  <code>InputSource</code>. Unlike {@link #getBuffer()}, this method
     *  detaches the returned {@link Buffer}, so user becomes responsible for
     *  handling the {@link Buffer}'s life-cycle.
     * </p>
     * 
     * @param size the requested size of the {@link Buffer} to be returned.
     * 
     * @return the {@link Buffer} of a given size, which represents a chunk
     * of the underlying {@link Buffer} which contains incoming request
     *  data. This method detaches the returned
     * {@link Buffer}, so user code becomes responsible for handling its life-cycle.
     */
    Buffer readBuffer(int size);
    
}
