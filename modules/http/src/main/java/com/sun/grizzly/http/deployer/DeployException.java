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
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.http.deployer;

/**
 * Deploying error.
 *
 * @author Hubert Iwaniuk
 * @since Sep 17, 2009
 */
public class DeployException extends Exception {
    /** {@inheritDoc} */
    public DeployException() {
        super();
    }

    /** {@inheritDoc} */
    public DeployException(String message) {
        super(message);
    }

    /** {@inheritDoc} */
    public DeployException(String message, Throwable cause) {
        super(message, cause);
    }

    /** {@inheritDoc} */
    public DeployException(Throwable cause) {
        super(cause);
    }
}
