/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memory;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;

/**
 * {@link MemoryManager}s, which implement this interface, are able to convert
 * frequently used Java buffer types to Grizzly {@link Buffer}.
 *
 * @see MemoryUtils
 * @see MemoryManager
 * 
 * @author Alexey Stashok
 */
public interface WrapperAware {
    /**
     * Returns {@link Buffer}, which wraps the byte array.
     *
     * @param data byte array to wrap
     *
     * @return {@link Buffer} wrapper on top of passed byte array.
     */
    Buffer wrap(byte[] data);

    /**
     * Returns {@link Buffer}, which wraps the part of byte array with
     * specific offset and length.
     *
     * @param data byte array to wrap
     * @param offset byte buffer offset
     * @param length byte buffer length
     *
     * @return {@link Buffer} wrapper on top of passed byte array.
     */
    Buffer wrap(byte[] data, int offset, int length);
    
    /**
     * Returns {@link Buffer}, which wraps the {@link String}.
     *
     * @param s {@link String}
     *
     * @return {@link Buffer} wrapper on top of passed {@link String}.
     */
    Buffer wrap(String s);

    /**
     * Returns {@link Buffer}, which wraps the {@link String} with the specific
     * {@link Charset}.
     *
     * @param s {@link String}
     * @param charset {@link Charset}, which will be used, when converting
     * {@link String} to byte array.
     *
     * @return {@link Buffer} wrapper on top of passed {@link String}.
     */
    Buffer wrap(String s, Charset charset);

    /**
     * Returns {@link Buffer}, which wraps the {@link ByteBuffer}.
     *
     * @param byteBuffer {@link ByteBuffer} to wrap
     *
     * @return {@link Buffer} wrapper on top of passed {@link ByteBuffer}.
     */
    Buffer wrap(ByteBuffer byteBuffer);
}
