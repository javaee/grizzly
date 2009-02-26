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

package com.sun.grizzly.portunif;

import com.sun.grizzly.Context;
import java.io.IOException;

/**
 *
 * @author Alexey Stashok
 */
public interface PUPreProcessor {

    /**
     * Returns <code>PUPreProcessor</code> id
     * 
     * @return <code>PUPreProcessor</code> id
     */
    public String getId();

    /**
     * Method performs data preprocessing, before it will be passed to 
     * <code>ProtocolFinder</code> collection
     * 
     * @return true, if preprocessing was completed and initial data was changed, 
     * false if preprocessing didn't change anything in the source data set
     */
    public boolean process(Context context, 
            PUProtocolRequest protocolRequest) throws IOException;
    
    /**
     * Method is called if no <code>ProtocolFinder</code> was found.
     * <code>PUPreProcessor</code> should restore <code>PUProtocolRequest</code>
     * and make it ready for the next data read operation.
     */
    public void postProcess(Context context, 
            PUProtocolRequest protocolRequest) throws IOException;
}
