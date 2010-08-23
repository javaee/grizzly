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

package com.sun.grizzly.connectioncache.impl.concurrent;

import com.sun.grizzly.connectioncache.spi.concurrent.ConcurrentQueue;

public class ConcurrentQueueImpl<V> implements ConcurrentQueue<V> {
    // This implementation of ConcurrentQueue is unsynchronized, for use in
    // other implementations that manage concurrency with locks.
    //
    // Structure: Head points to a node containing a null value, which is a special marker.
    // head.next is the first element, head.prev is the last.  The queue is empty if
    // head.next == head.prev == head.
    final Entry<V> head = new Entry<V>( null ) ;
    int count = 0 ;

    public ConcurrentQueueImpl() {
	head.next = head ;
	head.prev = head ;
    }

    private final class Entry<V> {
	Entry<V> next = null ;
	Entry<V> prev = null ;
	private HandleImpl<V> handle ;

	Entry( V value ) {
	    handle = new HandleImpl<V>( this, value ) ;
	}

	HandleImpl<V> handle() {
	    return handle ;
	}
    }

    private final class HandleImpl<V> implements Handle<V> {
	private Entry<V> entry ;
	private final V value ;
	private boolean valid ;

	HandleImpl( Entry<V> entry, V value ) {
	    this.entry = entry ;
	    this.value = value ;
	    this.valid = true ;
	}

	Entry<V> entry() {
	    return entry ;
	}

	public V value() {
	    return value ;
	}

	/** Delete the element corresponding to this handle 
	 * from the queue.  Takes constant time.
	 * @return  element deleted from queue, (yes or no)
  */
	public boolean remove() {
	    if (!valid) {
		return false ;
	    }

	    valid = false ;

	    entry.next.prev = entry.prev ;
	    entry.prev.next = entry.next ;
	    count-- ;

	    entry.prev = null ;
	    entry.next = null ;
	    entry.handle = null ;
	    entry = null ;
	    valid = false ;
	    return true ;
	}
    }

    public int size() {
	return count ;
    }

    /** Add a new element to the tail of the queue.
     * Returns a handle for the element in the queue.
     * @param arg  element to append to the queue
     * @return  {@link Handle} for the element in the queue
     */
    public Handle<V> offer( V arg ) {
	if (arg == null)
	    throw new IllegalArgumentException( "Argument cannot be null" ) ;

	Entry<V> entry = new Entry<V>( arg ) ;
	
	entry.next = head ;
	entry.prev = head.prev ;
	head.prev.next = entry ;
	head.prev = entry ;
	count++ ;

	return entry.handle() ;
    }

    /** Return an element from the head of the queue.
     * The element is removed from the queue.
     * @return  an element at the head of the queue
     */
    public V poll() {
	Entry<V> first = null ;
	V value = null ;

	first = head.next ;
	if (first == head)
	    return null ;
	else {
	    value = first.handle().value() ;

	    // assert that the following expression returns true!
	    first.handle().remove() ;
	}

	// Once first is removed from the queue, it is invisible to other threads,
	// so we don't need to synchronize here.
	first.next = null ;
	first.prev = null ;
	return value ;
    }
} 
