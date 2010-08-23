/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.filterchain;

import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 * {@link Transformer}, which serializes {@link GIOPMessage} into a {@link Buffer}.
 *
 * @author Alexey Stashok
 */
public class GIOPEncoder extends AbstractTransformer<GIOPMessage, Buffer> {

    @Override
    protected TransformationResult<GIOPMessage, Buffer> transformImpl(
            final AttributeStorage storage,
            final GIOPMessage input) throws TransformationException {

        final int size = 4 + 1 + 1 + 1 + 1 + 4 + input.getBodyLength();
        final Buffer output = obtainMemoryManager(storage).allocate(size);
        output.allowBufferDispose(true);
        // GIOP header
        output.put(input.getGIOPHeader());

        // Major version
        output.put(input.getMajor());

        // Minor version
        output.put(input.getMinor());

        // Flags
        output.put(input.getFlags());

        // Value
        output.put(input.getValue());

        // Body length
        output.putInt(input.getBodyLength());

        // Body
        output.put(input.getBody());

        return TransformationResult.<GIOPMessage, Buffer>createCompletedResult(
                output.flip(), null);
    }

    @Override
    public String getName() {
        return "GIOPEncoder";
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, GIOPMessage input) {
        return false;
    }
}
