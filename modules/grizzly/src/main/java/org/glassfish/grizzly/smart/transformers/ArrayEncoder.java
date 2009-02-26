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
package org.glassfish.grizzly.smart.transformers;

import java.lang.reflect.Array;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.util.AttributeStorage;

/**
 *
 * @author oleksiys
 */
public class ArrayEncoder extends SequenceEncoder<Object> {

    protected Attribute<Integer> currentElementIdxAttribute;

    public ArrayEncoder() {
        String prefix = ArrayEncoder.class.getName();
        currentElementIdxAttribute = attributeBuilder.createAttribute(
                prefix + ".currentElementIdx");
    }

    @Override
    public TransformationResult<Buffer> transform(AttributeStorage storage,
            Object input, Buffer output) throws TransformationException {

        if (input == null) {
            throw new TransformationException("Input should not be null");
        }

        // Optimize for transforming array of bytes
        if (componentType.isPrimitive() && componentType.equals(byte.class)) {

            MemoryManager memoryManager = null;
            if (output == null) {
                if (storage instanceof Connection) {
                    Connection connection = (Connection) storage;
                    memoryManager = connection.getTransport().getMemoryManager();
                    output = memoryManager.allocate(size(storage, input));
                } else {
                    throw new TransformationException("Output Buffer is null and there is no way to allocate one");
                }
            }

            int currentElementIdx = getValue(storage, currentElementIdxAttribute, 0);
            int size = size(storage, input);
            int bytesToCopy = Math.min(output.remaining(),
                    size - currentElementIdx);
            output.put((byte[]) input, currentElementIdx, bytesToCopy);
            currentElementIdx += bytesToCopy;

            if (currentElementIdx < size) {
                saveState(storage, currentElementIdx, incompletedResult);
                return incompletedResult;
            }
            
            TransformationResult<Buffer> result = new TransformationResult<Buffer>(
                    Status.COMPLETED, output.duplicate().flip());
            saveState(storage, result);
            return result;
        } else {
            return super.transform(storage, input, output);
        }
    }

    @Override
    public void release(AttributeStorage storage) {
        removeValue(storage, currentElementIdxAttribute);
        super.release(storage);
    }

    @Override
    protected Object get(AttributeStorage storage, Object sequence) {
        int currentElementIdx = getValue(storage, currentElementIdxAttribute, -1);
        return Array.get(sequence, currentElementIdx);
    }

    @Override
    protected boolean previous(AttributeStorage storage, Object sequence) {
        int currentElementIdx = getValue(storage, currentElementIdxAttribute, -1);
        if (currentElementIdx >= 0) {
            setValue(storage, currentElementIdxAttribute, --currentElementIdx);
            return true;
        }

        return false;
    }

    @Override
    protected boolean next(AttributeStorage storage, Object sequence) {
        int currentElementIdx = getValue(storage, currentElementIdxAttribute, -1);
        if (currentElementIdx < Array.getLength(sequence)) {
            setValue(storage, currentElementIdxAttribute, ++currentElementIdx);
            return true;
        }

        return false;
    }

    @Override
    protected int size(AttributeStorage storage, Object sequence) {
        return Array.getLength(sequence);
    }

    protected void saveState(AttributeStorage storage, int currentElementIdx,
            TransformationResult<Buffer> lastResult) {
        setValue(storage, currentElementIdxAttribute, currentElementIdx);
        super.saveState(storage, lastResult);
    }
}
