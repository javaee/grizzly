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

import com.sun.grizzly.Context;
import com.sun.grizzly.portunif.PUProtocolRequest;
import com.sun.grizzly.portunif.ProtocolFinder;
import java.io.IOException;

/**
 * {@link ProtocolFinder} wrapper, which is used in Grizzly configuration, to
 * support Finder -> Protocol mapping.
 *
 * @author Alexey Stashok
 */
public class ConfigProtocolFinderWrapper implements ProtocolFinder {
    private final String name;
    private final ProtocolFinder wrappedFinder;

    public ConfigProtocolFinderWrapper(String name, ProtocolFinder wrappedFinder) {
        this.name = name;
        this.wrappedFinder = wrappedFinder;
    }

    public String getName() {
        return name;
    }

    public ProtocolFinder getWrappedFinder() {
        return wrappedFinder;
    }

    public String find(Context context, PUProtocolRequest puProtocolRequest) throws IOException {
        if (wrappedFinder.find(context, puProtocolRequest) != null) {
            return name;
        }

        return null;
    }
}
