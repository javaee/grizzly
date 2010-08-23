/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.connectioncache.spi.concurrent;

/** A class that provides a very simply unbounded queue.
 * The main requirement here is that the class support constant time (very fast)
 * deletion of arbitrary elements.  An instance of this class must be thread safe,
 * either by locking or by using a wait-free algorithm (preferred).
 * The interface is made as simple is possible to make it easier to produce
 * a wait-free implementation.
 * @param V  object type to be stored in this <code>ConcurrentQueue</code>
 */
public interface ConcurrentQueue<V> {
    /** A Handle provides the capability to delete an element of a ConcurrentQueue
     * very quickly.  Typically, the handle is stored in the element, so that an
     * element located from another data structure can be quickly deleted from 
     * a ConcurrentQueue.
     * @param V  type of {@link Handle}
     */
    public interface Handle<V> {
	/** Return the value that corresponds to this handle.
	 * @return  value that corresponds to this handle
         */
	V value() ;

	/** Delete the element corresponding to this handle 
	 * from the queue.  Takes constant time.  Returns
	 * true if the removal succeeded, or false if it failed.
	 * which can occur if another thread has already called
	 * poll or remove on this element.
	 * @return  true if the removal succeeded, or false if it failed
         */
	boolean remove() ;
    }

    /** Return the number of elements in the queue.
     * @return  number of elements in the queue
     */
    int size() ;

    /** Add a new element to the tail of the queue.
     * Returns a handle for the element in the queue.
     * @param arg  element to add to the queue
     * @return  a {@link Handle} for the element added to the queue
     */
    Handle<V> offer( V arg ) ;

    /** Return an element from the head of the queue.
     * The element is removed from the queue.
     * @return  element removed from the queue
     */
    V poll() ;
} 
