/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly.streams;

import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.utils.ResultAware;
import com.sun.grizzly.utils.conditions.Condition;

/**
 *
 * @author Alexey Stashok
 */
public class StreamDecodeCondition<E> implements Condition {

    private final StreamReader streamReader;
    
    private final Transformer<Stream, E> decoder;
    private final ResultAware<E> resultAware;

    public StreamDecodeCondition(StreamReader streamReader,
            Transformer<Stream, E> decoder,
            ResultAware<E> resultAware) {
        this.streamReader = streamReader;
        this.decoder = decoder;
        this.resultAware = resultAware;
    }

    @Override
    public boolean check() {
        final TransformationResult<Stream, E> result =
                decoder.transform(streamReader.getConnection(), streamReader);

        final Status status = result.getStatus();
        if (status == Status.COMPLETED) {
            resultAware.setResult(result.getMessage());
            return true;
        } else if (status == Status.INCOMPLETED) {
            return false;
        }

        throw new TransformationException(result.getErrorCode() + ": " +
                result.getErrorDescription());
    }
}
