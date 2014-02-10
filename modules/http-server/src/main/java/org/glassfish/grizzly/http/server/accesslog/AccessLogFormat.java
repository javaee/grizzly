/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.accesslog;

import java.util.Date;

import org.glassfish.grizzly.http.server.Response;

/**
 * An interface defining a component capable of formatting {@link Response}s
 * into printable <em>access log entries</em>.
 *
 * <p>Implementations of this class <b>must</b> be thread-safe.</p>
 *
 * @author <a href="mailto:pier@usrz.com">Pier Fumagalli</a>
 * @author <a href="http://www.usrz.com/">USRZ.com</a>
 */
public interface AccessLogFormat {

    /**
     * Format the data contained in the specified {@link Response} and return
     * a {@link String} which can be appended to an access log file.
     *
     * @param response The {@link Response} holding the data to format.
     * @param timeStamp The {@link Date} at which the request was originated.
     * @param responseNanos The time, in nanoseconds, the {@link Response}
     *                      took to complete.
     */
    String format(Response response, Date timeStamp, long responseNanos);

}
