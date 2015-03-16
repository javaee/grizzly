/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.glassfish.grizzly.http2.hpack;

import org.glassfish.grizzly.http2.compression.HeaderListener;
import java.util.*;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http2.hpack.HeaderFieldTable.DecTable;

// TODO: should expose only 2 methods: decodeAll decodeOneByOne(callback)
// callback is needed when someone wants to consume header fields as soon as
// they decoded
//
// (name, value) -> (name, list of values) -> (name -> list of values)
//
// The callback needs to notify the type of message corresponding to a
// particular header field returned. Suppose someone is going to implement a
// proxy, then he'll need to know that this particular field is of
// type 'NeverIndexed' as spec states that:
//
//
// ...Intermediaries MUST use the same representation for encoding
// this header field...
//
// ...When a header field is represented as a literal header field never
// indexed, it MUST always be encoded with this specific literal
// representation.  In particular, when a peer sends a header field that
// it received represented as a literal header field never indexed, it
// MUST use the same representation to forward this header field...
//


// TODO: should be atomic. i.e. if anything goes wrong no state is changed
// and client can safely retry with bigger buffer or whatever...
public class Decoder {
    private static final BinaryRepresentation INDEXED = Indexed.getInstance();
    private static final BinaryRepresentation LITERAL = Literal.getInstance();
    private static final BinaryRepresentation LITERAL_NEVER_INDEXED = LiteralNeverIndexed.getInstance();
    private static final BinaryRepresentation LITERAL_WITH_INDEXING = LiteralWithIndexing.getInstance();
    private static final BinaryRepresentation SIZE_UPDATE = SizeUpdate.getInstance();
    
    private final DecTable table;

    public Decoder(int settingsHeaderTableSize) {
        table = HeaderFieldTable.createDecodingTable(settingsHeaderTableSize, 16); // FIXME: DI
    }

    /**
     * Decodes all header fields in a given header block.
     * @param source
     * @param handler
     */
    public void decode(Buffer source, HeaderListener handler) {
        Objects.requireNonNull(source, "source == null");
        Objects.requireNonNull(handler, "handler == null");

        while (source.hasRemaining()) {
            final byte sig = source.get(source.position());
            getRepresentations(sig).process(source, table, handler);
        }
    }
    
    private BinaryRepresentation getRepresentations(final byte sig) {
        if (Indexed.matches(sig)) {
            return INDEXED;
        } else if (Literal.matches(sig)) {
            return LITERAL;
        } else if (LiteralNeverIndexed.matches(sig)) {
            return LITERAL_NEVER_INDEXED;
        } else if (LiteralWithIndexing.matches(sig)) {
            return LITERAL_WITH_INDEXING;
        } else if (SizeUpdate.matches(sig)) {
            return SIZE_UPDATE;
        }
        
        throw new IllegalStateException("Unknown representation: " + sig);
    }
}
