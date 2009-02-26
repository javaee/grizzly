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

package org.glassfish.grizzly;

/**
 * Represents the result of message encoding/decoding.
 * 
 * @author Alexey Stashok
 */
public class TransformationResult<T> {

    public enum Status {
        COMPLETED, INCOMPLED, ERROR;
    }

    private T message;
    private Status status;

    private int errorCode;
    private String errorDescription;

    public TransformationResult() {
        this(Status.COMPLETED);
    }

    public TransformationResult(Status status) {
        this(status, null);
    }

    public TransformationResult(Status status, T message) {
        this.status = status;
        this.message = message;
    }

    /**
     * Creates error transformation result with specific code and description.
     *
     * @param errorId id of the error
     * @param errorDescription error description
     */
    public TransformationResult(int errorCode, String errorDescription) {
        this.status = Status.ERROR;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public T getMessage() {
        return message;
    }

    public void setMessage(T message) {
        this.message = message;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("Transformation result. Status: ").append(status);
        sb.append(" message: ").append(message);

        if (status == Status.ERROR) {
            sb.append(" errorCode: ").append(errorCode);
            sb.append(" errorDescription: ").append(errorDescription);
        }

        return sb.toString();
    }
}
