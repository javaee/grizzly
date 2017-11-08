/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.http2.hpack;


import org.glassfish.grizzly.Buffer;

final class LiteralWithIndexingWriter extends IndexNameValueWriter {

    private boolean tableUpdated;

    private CharSequence name;
    private CharSequence value;
    private int index;

    LiteralWithIndexingWriter() {
        super(0b0100_0000, 6);
    }

    @Override
    LiteralWithIndexingWriter index(int index) {
        super.index(index);
        this.index = index;
        return this;
    }

    @Override
    LiteralWithIndexingWriter name(CharSequence name, boolean useHuffman) {
        super.name(name, useHuffman);
        this.name = name;
        return this;
    }

    @Override
    LiteralWithIndexingWriter value(CharSequence value, boolean useHuffman) {
        super.value(value, useHuffman);
        this.value = value;
        return this;
    }

    @Override
    public boolean write(HeaderTable table, Buffer destination) {
        if (!tableUpdated) {
            CharSequence n;
            if (indexedRepresentation) {
                n = table.get(index).name;
            } else {
                n = name;
            }
            table.put(n, value);
            tableUpdated = true;
        }
        return super.write(table, destination);
    }

    @Override
    public IndexNameValueWriter reset() {
        tableUpdated = false;
        name = null;
        value = null;
        index = -1;
        return super.reset();
    }
}
