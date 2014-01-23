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

package org.glassfish.grizzly.memory;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.monitoring.MonitoringAware;

/**
 * <tt>MemoryManager</tt>, responsible for allocating and releasing memory,
 * required during application runtime.
 * <tt>MemoryManager</tt> implementations work with Grizzly {@link Buffer}s.
 *
 * @see Buffer
 *
 * @author Alexey Stashok
 */
public interface MemoryManager<E extends Buffer>
        extends MonitoringAware<MemoryProbe> {

    /**
     * <p>
     * The default {@link MemoryManager} implementation used by all created builder
     * instances.
     * </p>
     *
     * <p>
     * The default may be changed by one of two methods:
     * <ul>
     *     <li>
     *          Setting the system property {@value MemoryManagerInitializer#DMM_PROP_NAME}
     *          with the fully qualified name of the class that implements the
     *          MemoryManager interface.  Note that this class must be public and
     *          have a public no-arg constructor.
     *     </li>
     *     <li>
     *         Setting the system property {@value DefaultMemoryManagerFactory#DMMF_PROP_NAME}
     *         with the fully qualified name of the class that implements the
     *         {@link org.glassfish.grizzly.memory.DefaultMemoryManagerFactory} interface.
     *         Note that this class must be public and have a public no-arg
     *         constructor.
     *     </li>
     * </ul>
     *
     * </p>
     */
    public static final MemoryManager DEFAULT_MEMORY_MANAGER =
            MemoryManagerInitializer.initManager();

    /**
     * Allocated {@link Buffer} of the required size.
     *
     * @param size {@link Buffer} size to be allocated.
     * @return allocated {@link Buffer}.
     */
    public E allocate(int size);

    /**
     * Allocated {@link Buffer} at least of the provided size.
     * This could be useful for usecases like Socket.read(...), where
     * we're not sure how many bytes are available, but want to read as
     * much as possible.
     *
     * @param size the min {@link Buffer} size to be allocated.
     * @return allocated {@link Buffer}.
     */
    public E allocateAtLeast(int size);

    /**
     * Reallocate {@link Buffer} to a required size.
     * Implementation may choose the way, how reallocation could be done, either
     * by allocating new {@link Buffer} of required size and copying old
     * {@link Buffer} content there, or perform more complex logic related to
     * memory pooling etc.
     *
     * @param oldBuffer old {@link Buffer} to be reallocated.
     * @param newSize new {@link Buffer} required size.
     * @return reallocated {@link Buffer}.
     */
    public E reallocate(E oldBuffer, int newSize);

    /**
     * Release {@link Buffer}.
     * Implementation may ignore releasing and let JVM Garbage collector to take
     * care about the {@link Buffer}, or return {@link Buffer} to pool, in case
     * of more complex <tt>MemoryManager</tt> implementation.
     *
     * @param buffer {@link Buffer} to be released.
     */
    public void release(E buffer);
    
    /**
     * Return <tt>true</tt> if next {@link #allocate(int)} or {@link #allocateAtLeast(int)} call,
     * made in the current thread for the given memory size, going to return a {@link Buffer} based
     * on direct {@link java.nio.ByteBuffer}, or <tt>false</tt> otherwise.
     * 
     * @param size
     * @return 
     */
    public boolean willAllocateDirect(int size);
}
