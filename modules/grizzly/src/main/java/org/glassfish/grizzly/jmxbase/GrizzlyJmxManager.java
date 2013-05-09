/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.jmxbase;

import org.glassfish.grizzly.utils.ServiceFinder;
import java.util.Iterator;
import org.glassfish.grizzly.monitoring.MonitoringUtils;

/**
 * Grizzly JMX manager
 * 
 * @author Alexey Stashok
 */
public abstract class GrizzlyJmxManager {
    private static final GrizzlyJmxManager manager;
    
    static {
        ServiceFinder<GrizzlyJmxManager> serviceFinder = ServiceFinder.find(GrizzlyJmxManager.class);
        final Iterator<GrizzlyJmxManager> it = serviceFinder.iterator();
        GrizzlyJmxManager jmxManager;
        
        if (it.hasNext()) {
            jmxManager = it.next();
        } else {
            try {
                jmxManager = (GrizzlyJmxManager) loadClass(
                        "org.glassfish.grizzly.monitoring.jmx.DefaultJmxManager")
                        .newInstance();
            } catch (Exception e) {
                jmxManager = new NullJmxManager();
            }
        }
        
        manager = jmxManager;
    }

    private static Class<?> loadClass(final String classname) throws ClassNotFoundException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MonitoringUtils.class.getClassLoader();
        }
        
        return classLoader.loadClass(classname);
    }
    
    /**
     * Return the <tt>GrizzlyJmxManager</tt> instance.
     *
     * @return the <tt>GrizzlyJmxManager</tt> instance.
     */
    public static GrizzlyJmxManager instance() {
        return manager;
    }

    /**
     * Register Grizzly JMX {@link Object} at the root.
     *
     * @param object JMX {@link Object} to register.
     * @return JMX Bean object.
     */
    public abstract Object registerAtRoot(Object object);
    
    /**
     * Register Grizzly JMX {@link Object} at the root with the passed name.
     *
     * @param object JMX {@link Object} to register.
     * @param name
     * @return JMX Bean object.
     */
    public abstract Object registerAtRoot(Object object, String name);

    /**
     * Register Grizzly JMX {@link Object} as child of the passed parent object.
     *
     * @param parent parent
     * @param object JMX {@link Object} to register.
     * @return JMX Bean object.
     */
    public abstract Object register(Object parent, Object object);
    
    /**
     * Register Grizzly JMX {@link Object} as child of the passed parent object
     * with the specific name.
     *
     * @param parent parent
     * @param object JMX {@link Object} to register.
     * @param name
     * @return JMX Bean object.
     */
    public abstract Object register(Object parent, Object object, String name);

    /**
     * Unregister Grizzly JMX {@link Object}.
     *
     * @param object JMX {@link Object} to deregister.
     */
    public abstract void deregister(Object object);
    
    /**
     * Null JMX manager
     */
    private static final class NullJmxManager extends GrizzlyJmxManager {

        @Override
        public Object registerAtRoot(Object object) {
            return null;
        }
        
        @Override
        public Object registerAtRoot(Object object, String name) {
            return null;
        }

        @Override
        public Object register(Object parent, Object object) {
            return null;
        }

        @Override
        public Object register(Object parent, Object object, String name) {
            return null;
        }

        @Override
        public void deregister(Object object) {
        }
    }
}
