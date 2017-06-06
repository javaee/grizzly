/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import org.glassfish.grizzly.Buffer;

import java.io.IOException;

//
// Custom implementation of ISO/IEC 8859-1:1998
//
// The rationale behind this is not to deal with CharsetEncoder/CharsetDecoder,
// basically because it would require wrapping every single CharSequence into a
// CharBuffer and then copying it back.
//
// But why not to give a CharBuffer instead of Appendable? Because I can choose
// an Appendable (e.g. StringBuilder) that adjusts its length when needed and
// therefore not to deal with pre-sized CharBuffers or copying.
//
// The encoding is simple and well known: 1 byte <-> 1 char
//
final class ISO_8859_1 {

    private ISO_8859_1() { }

    public static final class Reader {

        public void read(Buffer source, Appendable destination) {
            for (int i = 0, len = source.remaining(); i < len; i++) {
                char c = (char) (source.get() & 0xff);
                try {
                    destination.append(c);
                } catch (IOException e) {
                    throw new RuntimeException
                            ("Error appending to the destination", e);
                }
            }
        }

        public Reader reset() {
            return this;
        }
    }

    public static final class Writer {

        private CharSequence source;
        private int pos;
        private int end;

        public Writer configure(CharSequence source, int start, int end) {
            this.source = source;
            this.pos = start;
            this.end = end;
            return this;
        }

        public boolean write(Buffer destination) {
            for (; pos < end; pos++) {
                char c = source.charAt(pos);
                if (c > '\u00FF') {
                    throw new IllegalArgumentException(
                            "Illegal ISO-8859-1 char: " + (int) c);
                }
                if (destination.hasRemaining()) {
                    destination.put((byte) c);
                } else {
                    return false;
                }
            }
            return true;
        }

        public Writer reset() {
            source = null;
            pos = -1;
            end = -1;
            return this;
        }
    }
}
