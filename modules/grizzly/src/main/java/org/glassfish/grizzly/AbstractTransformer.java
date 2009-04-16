/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package org.glassfish.grizzly;

import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.attributes.AttributeStorage;

/**
 *
 * @author oleksiys
 */
public abstract class AbstractTransformer<K, L> implements Transformer<K, L> {
    protected static TransformationResult incompletedResult =
            new TransformationResult(Status.INCOMPLED);

    protected AttributeBuilder attributeBuilder =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER;

    protected Attribute<K> inputBufferAttribute;
    protected Attribute<L> outputBufferAttribute;

    protected Attribute<TransformationResult<L>> lastResultAttribute;

    private MemoryManager memoryManager;

    public AbstractTransformer() {
        String namePrefix = getClass().getName();

        inputBufferAttribute = attributeBuilder.createAttribute(namePrefix +
                ".inputBuffer");
        outputBufferAttribute = attributeBuilder.createAttribute(namePrefix +
                ".outputBuffer");
        lastResultAttribute = attributeBuilder.createAttribute(namePrefix +
                ".lastResult");
    }

    public TransformationResult<L> transform(AttributeStorage storage)
            throws TransformationException {

        return transform(storage, getInput(storage), getOutput(storage));
    }

    public K getInput(AttributeStorage storage) {
        return getValue(storage, inputBufferAttribute);
    }

    public void setInput(AttributeStorage storage, K input) {
        inputBufferAttribute.set(storage.obtainAttributes(), input);
    }

    public L getOutput(AttributeStorage storage) {
        return getValue(storage, outputBufferAttribute);
    }

    public void setOutput(AttributeStorage storage, L output) {
        outputBufferAttribute.set(storage.obtainAttributes(), output);
    }

    public TransformationResult<L> getLastResult(AttributeStorage storage) {
        return getValue(storage, lastResultAttribute);
    }

    public AttributeHolder getProperties(AttributeStorage storage) {
        return storage.getAttributes();
    }

    public void hibernate(AttributeStorage storage) {
    }

    public void release(AttributeStorage storage) {
        removeValue(storage, inputBufferAttribute);
        removeValue(storage, outputBufferAttribute);
        removeValue(storage, lastResultAttribute);
    }

    protected static <E> E getValue(AttributeStorage storage,
            Attribute<E> attribute) {
        return getValue(storage, attribute, null);
    }

    protected static <E> E getValue(AttributeStorage storage,
            Attribute<E> attribute, E defaultValue) {

        AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            E value = attribute.get(holder);
            if (value != null) {
                return value;
            }
        }

        return defaultValue;
    }

    protected static <E> void setValue(AttributeStorage storage,
            Attribute<E> attribute, E value) {

        AttributeHolder holder = storage.obtainAttributes();
        attribute.set(holder, value);
    }

    protected static <E> E removeValue(AttributeStorage storage,
            Attribute<E> attribute) {

        AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            return attribute.remove(holder);
        }

        return null;
    }

    protected MemoryManager obtainMemoryManager(AttributeStorage storage) {
        if (memoryManager != null) {
            return memoryManager;
        }

        if (storage instanceof Connection) {
            Connection connection = (Connection) storage;
            return connection.getTransport().getMemoryManager();
        }

        return null;
    }
    
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void setMemoryManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }
}
