/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

/**
 * Exceptions utilities.
 *
 * @author Alexey Stashok
 */
public class Exceptions {

    /**
     * Returns the {@link Throwable}'s stack trace information as {@link String}.
     * The result {@link String} format will be the same as reported by {@link Throwable#printStackTrace()}.
     *
     * @param t {@link Throwable}.
     * @return the {@link Throwable}'s stack trace information as {@link String}.
     */
    public static String getStackTraceAsString(final Throwable t) {
        final StringWriter stringWriter = new StringWriter(2048);
        final PrintWriter pw = new PrintWriter(stringWriter);
        t.printStackTrace(pw);

        pw.close();
        return stringWriter.toString();
    }

    /**
     * Wrap the given {@link Throwable} by {@link IOException}.
     *
     * @param t {@link Throwable}.
     * @return {@link IOException}.
     */
    public static IOException makeIOException(final Throwable t) {
        if (IOException.class.isAssignableFrom(t.getClass())) {
            return (IOException) t;
        }

        return new IOException(t);
    }
    
    /**
     * @return {@link String} representation of all the JVM threads
     * 
     * @see Thread#getAllStackTraces()
     */
    public static String getAllStackTracesAsString() {
        final StringBuilder sb = new StringBuilder(256);
        
        final Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        
        for (Map.Entry<Thread, StackTraceElement[]> entry : all.entrySet()) {
            sb.append(entry.getKey()).append('\n');
            
            for (StackTraceElement traceElement : entry.getValue()) {
                sb.append("\tat ").append(traceElement).append('\n');                
            }
            
        }
        
        return sb.toString();
    }
}
