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

package com.sun.grizzly.utils;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Set of utility methods to work with Arrays.
 * 
 * @author Alexey Stashok
 */
public class ArrayUtils {
    /**
     * Add unique element to the array.
     * @param <T> type of the array element
     * @param array array
     * @param element element to add
     *
     * @return array, which will contain the new element. Either new array instance, if
     * passed array didn't contain the element, or the same array instance, if the element
     * is already present in the array.
     */
    public static <T> T[] addUnique(T[] array, T element) {
        final int idx = indexOf(array, element);
        if (idx == -1) {
            final int length = array.length;
            final T[] newArray = Arrays.copyOf(array, length + 1);
            newArray[length] = element;
            return newArray;
        }
        
        array[idx] = element;
        
        return array;
    }

    /**
     * Removes the element from the array.
     * @param <T> type of the array element
     * @param array array
     * @param element the element to remove
     * 
     * @return array, which won't contain the element. Either new array instance, if
     * passed array contains the element, or the same array instance, if the element
     * wasn't present in the array. <tt>null</tt> will be returned if the last
     * element was removed from the passed array.
     */
    public static <T> T[] remove(T[] array, T element) {
        final int idx = indexOf(array, element);
        if (idx != -1) {
            final int length = array.length;

            if (length == 1) {
                return null;
            }

            final T[] newArray = (T[]) Array.newInstance(
                    array.getClass().getComponentType(), length - 1);
            
            if (idx > 0) {
                System.arraycopy(array, 0, newArray, 0, idx);
            }

            if (idx < length - 1) {
                System.arraycopy(array, idx + 1, newArray, idx, length - idx - 1);
            }

            return newArray;
        }

        return array;
    }

    /**
     * Return the element index in the array.
     * @param <T> type of the array element
     * @param array array
     * @param element the element to look for.
     *
     * @return element's index, or <tt>-1</tt> if element wasn't found.
     */
    public static <T> int indexOf(T[] array, T element) {
        for (int i = 0; i < array.length; i++) {
            if (element.equals(array[i])) {
                return i;
            }
        }

        return -1;
    }
}
