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

import java.util.Arrays;

import static java.lang.String.format;

final class IntegerReader {

    private static final byte NEW             = 0x0;
    private static final byte CONFIGURED      = 0x1;
    private static final byte FIRST_BYTE_READ = 0x2;
    private static final byte DONE            = 0x4;

    private byte state = NEW;

    private int N;
    private int maxValue;
    private int value;
    private long r;
    private long b = 1;

    public IntegerReader configure(int N) {
        return configure(N, Integer.MAX_VALUE);
    }

    //
    // Why is it important to configure 'maxValue' here. After all we can wait
    // for the integer to be fully read and then check it. Can't we?
    //
    // Two reasons.
    //
    // 1. Value wraps around long won't be unnoticed.
    // 2. It can spit out an exception as soon as it becomes clear there's
    // an overflow. Therefore, no need to wait for the value to be fully read.
    //
    public IntegerReader configure(int N, int maxValue) {
        if (state != NEW) {
            throw new IllegalStateException("Already configured");
        }
        checkPrefix(N);
        if (maxValue < 0) {
            throw new IllegalArgumentException(
                    "maxValue >= 0: maxValue=" + maxValue);
        }
        this.maxValue = maxValue;
        this.N = N;
        state = CONFIGURED;
        return this;
    }

    public boolean read(Buffer input) {
        if (state == NEW) {
            throw new IllegalStateException("Configure first");
        }
        if (state == DONE) {
            return true;
        }
        if (!input.hasRemaining()) {
            return false;
        }
        if (state == CONFIGURED) {
            int max = (2 << (N - 1)) - 1;
            int n = input.get() & max;
            if (n != max) {
                value = n;
                state = DONE;
                return true;
            } else {
                r = max;
            }
            state = FIRST_BYTE_READ;
        }
        if (state == FIRST_BYTE_READ) {
            // variable-length quantity (VLQ)
            byte i;
            do {
                if (!input.hasRemaining()) {
                    return false;
                }
                i = input.get();
                long increment = b * (i & 127);
                if (r + increment > maxValue) {
                    throw new IllegalArgumentException(format(
                            "Integer overflow: maxValue=%,d, value=%,d",
                            maxValue, r + increment));
                }
                r += increment;
                b *= 128;
            } while ((128 & i) == 128);

            value = (int) r;
            state = DONE;
            return true;
        }
        throw new InternalError(Arrays.toString(
                new Object[]{state, N, maxValue, value, r, b}));
    }

    public int get() throws IllegalStateException {
        if (state != DONE) {
            throw new IllegalStateException("Has not been fully read yet");
        }
        return value;
    }

    private static void checkPrefix(int N) {
        if (N < 1 || N > 8) {
            throw new IllegalArgumentException("1 <= N <= 8: N= " + N);
        }
    }

    public IntegerReader reset() {
        b = 1;
        state = NEW;
        return this;
    }
}
