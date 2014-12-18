/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.attributes.AttributeStorage;

/**
 *
 * @author Alexey Stashok
 */
public abstract class AbstractTransformer<K, L> implements Transformer<K, L> {
    protected final AttributeBuilder attributeBuilder =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER;

    protected final Attribute<LastResultAwareState<K, L>> stateAttr;

    private MemoryManager memoryManager;

    public AbstractTransformer() {
        String namePrefix = getNamePrefix();

        stateAttr = attributeBuilder.createAttribute(namePrefix + ".state");
    }

    protected String getNamePrefix() {
        return getClass().getName();
    }

    @Override
    public final TransformationResult<K, L> transform(AttributeStorage storage,
            K input) throws TransformationException {
        return saveLastResult(storage, transformImpl(storage, input));
    }

    protected abstract TransformationResult<K, L> transformImpl(
            AttributeStorage storage,
            K input) throws TransformationException;

    @Override
    public final TransformationResult<K, L> getLastResult(
            final AttributeStorage storage) {
        final LastResultAwareState<K, L> state = stateAttr.get(storage);
        if (state != null) {
            return state.getLastResult();
        }

        return null;
    }

    protected final TransformationResult<K, L> saveLastResult(
            final AttributeStorage storage,
            final TransformationResult<K, L> result) {
        obtainStateObject(storage).setLastResult(result);
        return result;
    }

    @Override
    public void release(AttributeStorage storage) {
        stateAttr.remove(storage);
    }

    protected MemoryManager obtainMemoryManager(AttributeStorage storage) {
        if (memoryManager != null) {
            return memoryManager;
        }

        if (storage instanceof Connection) {
            Connection connection = (Connection) storage;
            return connection.getMemoryManager();
        }

        return MemoryManager.DEFAULT_MEMORY_MANAGER;
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

    protected final LastResultAwareState<K, L> obtainStateObject(
            final AttributeStorage storage) {
        
        LastResultAwareState<K, L> value = stateAttr.get(storage);
        if (value == null) {
            value = createStateObject();
            stateAttr.set(storage, value);
        }

        return value;
    }
    
    protected LastResultAwareState<K, L> createStateObject() {
        return new LastResultAwareState<K, L>();
    }

    public static class LastResultAwareState<K, L> {
        protected TransformationResult<K, L> lastResult;

        public TransformationResult<K, L> getLastResult() {
            return lastResult;
        }

        public void setLastResult(TransformationResult<K, L> lastResult) {
            this.lastResult = lastResult;
        }
    }
}
