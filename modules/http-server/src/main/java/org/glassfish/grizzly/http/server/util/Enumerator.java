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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.http.server.util;


import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;


/**
 * HttpHandler class that wraps an <code>Enumeration</code> around a Java2
 * collection classes object <code>Iterator</code> so that existing APIs
 * returning Enumerations can easily run on top of the new collections.
 * Constructors are provided to easily create such wrappers.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 1.3 $ $Date: 2007/06/18 14:17:08 $
 */

public final class Enumerator<E> implements Enumeration<E> {


    // ----------------------------------------------------------- Constructors


    /**
     * Return an Enumeration over the values of the specified Collection.
     *
     * @param collection Collection whose values should be enumerated
     */
    public Enumerator(Collection<E> collection) {

        this(collection.iterator());

    }


    /**
     * Return an Enumeration over the values of the specified Collection.
     *
     * @param collection Collection whose values should be enumerated
     * @param clone true to clone iterator
     */
    public Enumerator(Collection<E> collection, boolean clone) {

        this(collection.iterator(), clone);

    }


    /**
     * Return an Enumeration over the values returned by the
     * specified {@link Iterable}.
     *
     * @param iterable {@link Iterable} to be wrapped
     */
    public Enumerator(Iterable<E> iterable) {
        this(iterable.iterator());
    }


    /**
     * Return an Enumeration over the values returned by the
     * specified {@link Iterable}.
     *
     * @param iterable {@link Iterable} to be wrapped
     * @param clone true to clone iterator
     */
    public Enumerator(Iterable<E> iterable, boolean clone) {
        this(iterable.iterator(), clone);
    }


    /**
     * Return an Enumeration over the values returned by the
     * specified Iterator.
     *
     * @param iterator Iterator to be wrapped
     */
    public Enumerator(Iterator<E> iterator) {

        this.iterator = iterator;

    }


    /**
     * Return an Enumeration over the values returned by the
     * specified Iterator.
     *
     * @param iterator Iterator to be wrapped
     * @param clone true to clone iterator
     */
    public Enumerator(Iterator<E> iterator, boolean clone) {

        super();
        if (!clone) {
            this.iterator = iterator;
        } else {
            List<E> list = new ArrayList<E>();
            while (iterator.hasNext()) {
                list.add(iterator.next());
            }
            this.iterator = list.iterator();
        }

    }

    
    /**
     * Return an Enumeration over the values of the specified Map.
     *
     * @param map Map whose values should be enumerated
     */
    public Enumerator(Map<?, E> map) {

        this(map.values().iterator());

    }


    /**
     * Return an Enumeration over the values of the specified Map.
     *
     * @param map Map whose values should be enumerated
     * @param clone true to clone iterator
     */
    public Enumerator(Map<?, E> map, boolean clone) {

        this(map.values().iterator(), clone);

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The <code>Iterator</code> over which the <code>Enumeration</code>
     * represented by this class actually operates.
     */
    private Iterator<E> iterator = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Tests if this enumeration contains more elements.
     *
     * @return <tt>true</tt> if and only if this enumeration object
     *  contains at least one more element to provide, <tt>false</tt>
     *  otherwise
     */
    public boolean hasMoreElements() {

        return (iterator.hasNext());

    }


    /**
     * Returns the next element of this enumeration if this enumeration
     * has at least one more element to provide.
     *
     * @return the next element of this enumeration
     *
     * @exception NoSuchElementException if no more elements exist
     */
    public E nextElement() throws NoSuchElementException {

        return (iterator.next());

    }


}
