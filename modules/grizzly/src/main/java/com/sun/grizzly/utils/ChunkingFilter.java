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

package com.sun.grizzly.utils;

import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.filterchain.AbstractCodecFilter;
import com.sun.grizzly.memory.BufferUtils;
import java.util.logging.Logger;


/**
 * The Filter is responsible to break the incoming/outgoing data into chunks and
 * pass them down/up by the {@link FilterChain}.
 * This Filter could be useful for testing reasons to check if all Filters in
 * the {@link FilterChain} work properly with chunked data.
 * 
 * @author Alexey Stashok
 */
public class ChunkingFilter extends AbstractCodecFilter<Buffer, Buffer> {
    private static final Logger logger = Grizzly.logger(ChunkingFilter.class);

    private final int chunkSize;

    /**
     * Construct a <tt>ChunkFilter</tt>, which will break incoming/outgoing data
     * into chunks of the specified size.
     *
     * @param chunkSize the chunk size.
     */
    public ChunkingFilter(int chunkSize) {
        super(new ChunkingDecoder(chunkSize),
                new ChunkingEncoder(chunkSize));
        this.chunkSize = chunkSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public static final class ChunkingDecoder extends ChunkingTransformer {

        public ChunkingDecoder(int chunk) {
            super(chunk);
        }

        @Override
        protected TransformationResult<Buffer, Buffer> transformImpl(
                AttributeStorage storage,
                Buffer input) throws TransformationException {
            return super.transformImpl(storage, input);
        }
    }

    public static final class ChunkingEncoder extends ChunkingTransformer {

        public ChunkingEncoder(int chunk) {
            super(chunk);
        }

        @Override
        protected TransformationResult<Buffer, Buffer> transformImpl(
                AttributeStorage storage,
                Buffer input) throws TransformationException {
            return super.transformImpl(storage, input);
//            return TransformationResult.createCompletedResult(input, null);
        }
    }

    public static abstract class ChunkingTransformer
            extends AbstractTransformer<Buffer, Buffer> {
        private final int chunk;

        public ChunkingTransformer(int chunk) {
            this.chunk = chunk;
        }

        @Override
        public String getName() {
            return "ChunkingTransformer";
        }

        @Override
        protected TransformationResult<Buffer, Buffer> transformImpl(
                AttributeStorage storage, Buffer input)
                throws TransformationException {

            if (!input.hasRemaining()) {
                return TransformationResult.<Buffer, Buffer>createIncompletedResult(input);
            }

            final int chunkSize = Math.min(chunk, input.remaining());

            final int oldInputPos = input.position();
            final int oldInputLimit = input.limit();

            BufferUtils.setPositionLimit(input, oldInputPos, oldInputPos + chunkSize);
            
            final Buffer output = obtainMemoryManager(storage).allocate(chunkSize);
            output.put(input).flip();

            BufferUtils.setPositionLimit(input, oldInputPos + chunkSize, oldInputLimit);

            return TransformationResult.<Buffer, Buffer>createCompletedResult(
                    output, input);
        }

        @Override
        public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
            return input != null && input.hasRemaining();
        }
    }
}
