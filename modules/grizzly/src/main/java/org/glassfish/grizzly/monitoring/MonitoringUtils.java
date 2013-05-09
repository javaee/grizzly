/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.monitoring;

import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;

/**
 * The class, which contains utility methods for monitoring support.
 * 
 * @author Alexey Stashok
 */
public class MonitoringUtils {
    private static final Logger LOGGER = Grizzly.logger(MonitoringUtils.class);
    
    /**
     * Load JMX object class and create an instance using constructor with
     * constructorParam.getClass() parameter. The constructorParam will be passed
     * to the constructor as a parameter.
     * 
     * @param jmxObjectClassname the JMX object class name.
     * @param constructorParam the parameter to be passed to the constructor.
     * @return instance of jmxObjectClassname class.
     */
    public static Object loadJmxObject(final String jmxObjectClassname,
            final Object constructorParam) {
        return loadJmxObject(jmxObjectClassname, constructorParam,
                constructorParam.getClass());
    }
    
    /**
     * Load JMX object class and create an instance using constructor with
     * contructorParamType parameter. The constructorParam will be passed to the
     * constructor as a parameter.
     * 
     * @param jmxObjectClassname the JMX object class name.
     * @param constructorParam the parameter to be passed to the constructor.
     * @param contructorParamType the constructor parameter type, used to find
     *                              appropriate constructor.
     * @return instance of jmxObjectClassname class.
     */
    public static Object loadJmxObject(final String jmxObjectClassname,
            final Object constructorParam, final Class contructorParamType) {
        try {
            final Class<?> clazz = loadClass(jmxObjectClassname);
            final Constructor<?> c = clazz.getDeclaredConstructor(contructorParamType);
            return c.newInstance(constructorParam);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Can not load JMX Object: " +
                    jmxObjectClassname, e);
        }
        
        return null;
    }
    
    private static Class<?> loadClass(final String classname) throws ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MonitoringUtils.class.getClassLoader();
        }
        
        return classLoader.loadClass(classname);
    }
}
