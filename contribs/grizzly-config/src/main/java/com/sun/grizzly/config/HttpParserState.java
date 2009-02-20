/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import java.nio.ByteBuffer;

/**
 * Object, which saves the parser state during processing a HTTP requests
 *
 * @author Alexey Stashok
 */
public class HttpParserState {
    public static final int PARAMETER_NOT_SET = Integer.MIN_VALUE;
    public static final int DEFAULT_STATE_PARAMETERS_NUM = 5;
    private ByteBuffer buffer;
    private boolean isCompleted;
    private int state;
    private int position;
    private int stateParameters[];

    public HttpParserState() {
        this(DEFAULT_STATE_PARAMETERS_NUM);
    }

    public HttpParserState(final int stateParametersNum) {
        stateParameters = new int[stateParametersNum];
        reset();
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(final ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public int getState() {
        return state;
    }

    public void setState(final int state) {
        this.state = state;
    }

    public int getStateParameter(final int i) {
        return stateParameters[i];
    }

    public int getStateParameter(final int i, final int defaultValue) {
        final int value = stateParameters[i];
        return value != PARAMETER_NOT_SET ? value : defaultValue;
    }

    public void setStateParameter(final int i, final int value) {
        stateParameters[i] = value;
    }

    public final void reset() {
        buffer = null;
        position = 0;
        state = 0;
        isCompleted = false;
        for (int i = 0; i < stateParameters.length; i++) {
            stateParameters[i] = PARAMETER_NOT_SET;
        }
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(final boolean isCompleted) {
        this.isCompleted = isCompleted;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("HttpParser state(").
            append(hashCode()).append(')');
        sb.append(" Buffer: ").append(buffer);
        sb.append(" isCompleted: ").append(isCompleted);
        sb.append(" State: ").append(state);
        sb.append(" position: ").append(position);
        for (int i = 0; i < stateParameters.length; i++) {
            sb.append("; parameter[").append(i).append("]: ").
                append(stateParameters[i]);
        }
        return sb.toString();
    }
}
