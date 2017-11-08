/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.attributes;

import org.glassfish.grizzly.Grizzly;

import java.util.logging.Level;
import java.util.logging.Logger;

class AttributeBuilderInitializer {

    private static final String PROP = "org.glassfish.grizzly.DEFAULT_ATTRIBUTE_BUILDER";

    private static final Logger LOGGER = Grizzly.logger(AttributeBuilderInitializer.class);

    @SuppressWarnings("unchecked")
    static AttributeBuilder initBuilder() {
        final String className = System.getProperty(PROP);
        if (className != null) {
            try {
                Class<? extends AttributeBuilder> builderClass = (Class<? extends AttributeBuilder>)
                        Class.forName(className,
                                true,
                                AttributeBuilder.class.getClassLoader());
                return builderClass.newInstance();
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.log(Level.SEVERE,
                            "Unable to load or create a new instance of AttributeBuilder {0}.  Cause: {1}",
                            new Object[]{className, e.getMessage()});
                }
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, e.toString(), e);
                }
                return new DefaultAttributeBuilder();
            }
        }
        return new DefaultAttributeBuilder();
    }

}
