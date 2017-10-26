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
package org.glassfish.grizzly.http2.hpack;

import org.glassfish.grizzly.Buffer;

import java.util.Arrays;

final class IntegerWriter {

    private static final byte NEW                = 0x0;
    private static final byte CONFIGURED         = 0x1;
    private static final byte FIRST_BYTE_WRITTEN = 0x2;
    private static final byte DONE               = 0x4;

    private byte state = NEW;

    private int payload;
    private int N;
    private int value;

    //
    //      0   1   2   3   4   5   6   7
    //    +---+---+---+---+---+---+---+---+
    //    |   |   |   |   |   |   |   |   |
    //    +---+---+---+-------------------+
    //    |<--------->|<----------------->|
    //       payload           N=5
    //
    // payload is the contents of the left-hand side part of the octet;
    //         it is truncated to fit into 8-N bits, where 1 <= N <= 8;
    //
    public IntegerWriter configure(int value, int N, int payload) {
        if (state != NEW) {
            throw new IllegalStateException("Already configured");
        }
        if (value < 0) {
            throw new IllegalArgumentException("value >= 0: value=" + value);
        }
        checkPrefix(N);
        this.value = value;
        this.N = N;
        this.payload = payload & 0xFF & (0xFFFFFFFF << N);
        state = CONFIGURED;
        return this;
    }

    public boolean write(Buffer output) {
        if (state == NEW) {
            throw new IllegalStateException("Configure first");
        }
        if (state == DONE) {
            return true;
        }

        if (!output.hasRemaining()) {
            return false;
        }
        if (state == CONFIGURED) {
            int max = (2 << (N - 1)) - 1;
            if (value < max) {
                output.put((byte) (payload | value));
                state = DONE;
                return true;
            }
            output.put((byte) (payload | max));
            value -= max;
            state = FIRST_BYTE_WRITTEN;
        }
        if (state == FIRST_BYTE_WRITTEN) {
            while (value >= 128 && output.hasRemaining()) {
                output.put((byte) (value % 128 + 128));
                value /= 128;
            }
            if (!output.hasRemaining()) {
                return false;
            }
            output.put((byte) value);
            state = DONE;
            return true;
        }
        throw new InternalError(Arrays.toString(
                new Object[]{state, payload, N, value}));
    }

    private static void checkPrefix(int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("1 <= N <= 8: N= " + N);
        }
    }

    public IntegerWriter reset() {
        state = NEW;
        return this;
    }
}
