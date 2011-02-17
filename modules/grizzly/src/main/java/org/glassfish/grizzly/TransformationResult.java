/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

/**
 * Represents the result of message encoding/decoding.
 * 
 * @author Alexey Stashok
 */
public class TransformationResult<I, O> implements Cacheable {
    private static final ThreadCache.CachedTypeIndex<TransformationResult> CACHE_IDX =
            ThreadCache.obtainIndex(TransformationResult.class, 2);

    public static <I, O> TransformationResult<I, O> createErrorResult(
            int errorCode, String errorDescription) {
        return create(Status.ERROR, null, null, errorCode, errorDescription);
    }

    public static <I, O> TransformationResult<I, O> createCompletedResult(
            O message, I externalRemainder) {
        return create(Status.COMPLETE, message, externalRemainder, 0, null);
    }

    public static <I, O> TransformationResult<I, O> createIncompletedResult(
            I externalRemainder) {
        return create(Status.INCOMPLETE, null, externalRemainder, 0, null);
    }

    @SuppressWarnings("unchecked")
    private static <I, O> TransformationResult<I, O> create(Status status,
            O message, I externalRemainder, int errorCode, String errorDescription) {
        
        final TransformationResult<I, O> result = ThreadCache.takeFromCache(CACHE_IDX);
        if (result != null) {
            result.setStatus(status);
            result.setMessage(message);
            result.setExternalRemainder(externalRemainder);
            result.setErrorCode(errorCode);
            result.setErrorDescription(errorDescription);
            
            return result;
        }

        return new TransformationResult<I, O>(status, message, externalRemainder,
                errorCode, errorDescription);
    }

    public enum Status {
        COMPLETE, INCOMPLETE, ERROR
    }

    private O message;
    private Status status;

    private int errorCode;
    private String errorDescription;

    private I externalRemainder;

    public TransformationResult() {
        this(Status.COMPLETE, null, null);
    }

    public TransformationResult(Status status, O message, I externalRemainder) {
        this.status = status;
        this.message = message;
        this.externalRemainder = externalRemainder;
    }

    /**
     * Creates error transformation result with specific code and description.
     *
     * @param errorCode id of the error
     * @param errorDescription error description
     */
    public TransformationResult(int errorCode, String errorDescription) {
        this.status = Status.ERROR;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    protected TransformationResult(Status status, O message, I externalRemainder,
            int errorCode, String errorDescription) {
        this.status = status;
        this.message = message;
        this.externalRemainder = externalRemainder;

        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public O getMessage() {
        return message;
    }

    public void setMessage(O message) {
        this.message = message;
    }

    public I getExternalRemainder() {
        return externalRemainder;
    }

    public void setExternalRemainder(I externalRemainder) {
        this.externalRemainder = externalRemainder;
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

    /**
     * If implementation uses {@link org.glassfish.grizzly.utils.ObjectPool} to store
     * and reuse {@link TransformationResult} instances - this method will be
     * called before {@link TransformationResult} will be offered to pool.
     */
    public void reset() {
        message = null;
        status = null;

        errorCode = 0;
        errorDescription = null;
        externalRemainder = null;
    }

    /**
     * Recycle this {@link Context}
     */
    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }
}
