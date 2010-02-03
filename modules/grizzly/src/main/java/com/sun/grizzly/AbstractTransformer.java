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

package com.sun.grizzly;

import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 *
 * @author Alexey Stashok
 */
public abstract class AbstractTransformer<K, L> implements Transformer<K, L> {
    protected AttributeBuilder attributeBuilder =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER;

    protected Attribute<TransformationResult<K, L>> lastResultAttribute;

    private MemoryManager memoryManager;

    public AbstractTransformer() {
        String namePrefix = getNamePrefix();

        lastResultAttribute = attributeBuilder.createAttribute(namePrefix +
                ".lastResult");
    }

    protected String getNamePrefix() {
        return getClass().getName();
    }

    @Override
    public final TransformationResult<K, L> getLastResult(
            final AttributeStorage storage) {
        return lastResultAttribute.get(storage);
    }

    protected final TransformationResult<K, L> saveLastResult(
            final AttributeStorage storage,
            final TransformationResult<K, L> result) {
        lastResultAttribute.set(storage, result);
        return result;
    }

    @Override
    public void release(AttributeStorage storage) {
        lastResultAttribute.remove(storage);
    }


    protected MemoryManager obtainMemoryManager(AttributeStorage storage) {
        if (memoryManager != null) {
            return memoryManager;
        }

        if (storage instanceof Connection) {
            Connection connection = (Connection) storage;
            return connection.getTransport().getMemoryManager();
        }

        return TransportFactory.getInstance().getDefaultMemoryManager();
    }
    
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    public static <T> T getValue(final AttributeStorage storage,
            final Attribute<T> attribute,
            final T defaultValue) {
        final T value = attribute.get(storage);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }
}
