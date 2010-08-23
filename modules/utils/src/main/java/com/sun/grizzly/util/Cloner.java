/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.lang.reflect.Constructor;
import java.util.logging.Level;

/**
 * Class Cloner creates a clone of given object, 
 * which should implement interface <code>Copyable</code>
 * 
 * @author Alexey Stashok
 */
public class Cloner {
    /**
     * Method creates a clone of given object pattern
     * Pattern parameter should implement <class>Copyable</class> interface
     * 
     * @param pattern represents object, which will be cloned. Should implement <code>Copyable</code>
     * @return clone
     */
    public static <T extends Copyable> T clone(T pattern) {
        try {
            T copy = null;
            
            try {
                // Try default Constructor
                copy = (T) pattern.getClass().newInstance();
            } catch(InstantiationException e) {
                // If didn't succeed with default - try other public constructors
                Constructor[] constructors = pattern.getClass().getConstructors();
                if (constructors.length == 0) {
                    // if there are no public constructors - try others
                    constructors = pattern.getClass().getDeclaredConstructors();
                }
                
                for(Constructor constructor : constructors) {
                    constructor.setAccessible(true);
                    Object[] params = 
                            new Object[constructor.getParameterTypes().length];
                    
                    try {
                        copy = (T) constructor.newInstance(params);
                        break;
                    } catch(InstantiationException ee) {
                    }
                }
            }
            
            if (copy == null) {
                throw new InstantiationException("Could not create " +
                        "an instance of class: " + pattern.getClass().getName());
            }
            
            pattern.copyTo(copy);
            return copy;
        } catch (Exception e) {
            throw new RuntimeException("Error copying objects! " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
