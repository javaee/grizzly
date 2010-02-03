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
package com.sun.grizzly.smart.transformers;

import java.lang.reflect.Array;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.attributes.AttributeStorage;

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
    public String getName() {
        return ArrayEncoder.class.getName();
    }

    @Override
    public TransformationResult<Object, Buffer> transform(
            AttributeStorage storage, Object input)
            throws TransformationException {

        if (input == null) {
            throw new TransformationException("Input should not be null");
        }
        
        // Optimize for transforming array of bytes
        if (componentType.isPrimitive() && componentType.equals(byte.class)) {

            final MemoryManager memoryManager = obtainMemoryManager(storage);
            Buffer output = memoryManager.allocate(size(storage, input));

            int currentElementIdx = getValue(storage, currentElementIdxAttribute, 0);
            int size = size(storage, input);
            int bytesToCopy = Math.min(output.remaining(),
                    size - currentElementIdx);
            output.put((byte[]) input, currentElementIdx, bytesToCopy);
            currentElementIdx += bytesToCopy;

            if (currentElementIdx < size) {
                return saveLastResult(storage,
                        TransformationResult.<Object, Buffer>createIncompletedResult(
                        null, false));
            }

            output.flip();
            
            return saveLastResult(storage,
                    TransformationResult.<Object, Buffer>createCompletedResult(
                    output, null, false));
        } else {
            return super.transform(storage, input);
        }
    }

    @Override
    public void release(AttributeStorage storage) {
        currentElementIdxAttribute.remove(storage);
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
            currentElementIdxAttribute.set(storage, --currentElementIdx);
            return true;
        }

        return false;
    }

    @Override
    protected boolean next(AttributeStorage storage, Object sequence) {
        int currentElementIdx = getValue(storage, currentElementIdxAttribute, -1);
        if (currentElementIdx < Array.getLength(sequence)) {
            currentElementIdxAttribute.set(storage, ++currentElementIdx);
            return true;
        }

        return false;
    }

    @Override
    protected int size(AttributeStorage storage, Object sequence) {
        return Array.getLength(sequence);
    }
}
