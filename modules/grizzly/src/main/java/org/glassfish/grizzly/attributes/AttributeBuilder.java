/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.attributes;

/**
 * <tt>AttributeBuilder</tt> is responsible for creating and indexing
 * {@link Attribute}s.
 * For faster access to {@link Attribute} value, each {@link Attribute} has
 * assigned index. <tt>AttributeBuilder</tt> is responsible to assign each
 * {@link Attribute} unique index.
 *
 * @see DefaultAttributeBuilder
 * 
 * @author Alexey Stashok
 */
public interface AttributeBuilder {

    /**
     * <p>
     * The default {@link AttributeBuilder} implementation used by all created builder
     * instances.
     * </p>
     *
     * <p>
     * The default may be changed by setting the system property <code>org.glassfish.grizzly.DEFAULT_ATTRIBUTE_BUILDER</code>
     * with the fully qualified name of the class that implements the AttributeBuilder interface.  Note that this class must
     * be public and have a public no-arg constructor.
     * </p>
     */
    public static AttributeBuilder DEFAULT_ATTRIBUTE_BUILDER =
            AttributeBuilderInitializer.initBuilder();

    /**
     * Create Attribute with name
     * 
     * @param <T> Type of attribute value
     * @param name attribute name

     * @return Attribute<T>
     */
    public <T> Attribute<T> createAttribute(String name);

    /**
     * Create Attribute with name and default value
     * 
     * @param <T> Type of attribute value
     * @param name attribute name
     * @param defaultValue attribute's default value
     * 
     * @return Attribute<T>
     */
    public <T> Attribute<T> createAttribute(String name, T defaultValue);

    /**
     * Create Attribute with name and initializer, which will be called, if
     * Attribute's value is null on a AttributedObject
     * 
     * @param <T> Type of attribute value
     * @param name attribute name
     * @param initializer NullaryFunction, which will be called, if Attribute's
     *                    value is null on a AttributedObject 
     * 
     * @return Attribute<T>
     */
    public <T> Attribute<T> createAttribute(String name, org.glassfish.grizzly.utils.NullaryFunction<T> initializer);
    
    /**
     * Create Attribute with name and initializer, which will be called, if
     * Attribute's value is null on a AttributedObject
     * 
     * @param <T> Type of attribute value
     * @param name attribute name
     * @param initializer NullaryFunction, which will be called, if Attribute's
     *                    value is null on a AttributedObject 
     * 
     * @return Attribute<T>
     * @deprecated pls. use {@link #createAttribute(java.lang.String, org.glassfish.grizzly.utils.NullaryFunction)}.
     */
    public <T> Attribute<T> createAttribute(String name, NullaryFunction<T> initializer);

    /**
     * Creates and returns new thread-safe {@link AttributeHolder}
     * 
     * @return thread-safe {@link AttributeHolder}
     */
    public AttributeHolder createSafeAttributeHolder();
    
    /**
     * Creates and returns new non thread-safe {@link AttributeHolder}
     * 
     * @return non thread-safe {@link AttributeHolder}
     */
    public AttributeHolder createUnsafeAttributeHolder();
}
