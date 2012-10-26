/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.websockets;

import java.util.ArrayList;
import java.util.List;

/**
 * Representation of a WebSocket extension and its associated parameters.
 *
 * @since 2.3
 */
public final class Extension {

    private final String name;
    private final List<Parameter> parameters = new ArrayList<Parameter>();


    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new Extension with the specified name.
     *
     * @param name extension name
     */
    public Extension(final String name) {
        this.name = name;
    }


    // ---------------------------------------------------------- Public Methods


    /**
     * @return the extension name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return any parameters associated with this extension.
     */
    public List<Parameter> getParameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Extension extension = (Extension) o;

        return name.equals(extension.name)
                && parameters.equals(extension.parameters);

    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + parameters.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!parameters.isEmpty()) {
            for (Extension.Parameter p : parameters) {
                sb.append("; ");
                sb.append(p.toString());
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------- Nested Classes


    /**
     * Representation of extension parameters.
     */
    public static final class Parameter {

        private final String name;
        private String value;


        // -------------------------------------------------------- Constructors


        /**
         * Constructs a new parameter based on the provided values.
         *
         * @param name the name of the parameter (may not be <code>null</code>).
         * @param value the value of the parameter (may be <code>null</code>).
         *
         * @throws IllegalArgumentException if name is <code>null</code>.
         */
        public Parameter(final String name, final String value) {
            if (name == null) {
                throw new IllegalArgumentException("Parameter name may not be null");
            }
            this.name = name;
            this.value = value;
        }


        // ------------------------------------------------------ Public Methods

        /**
         * @return the parameter name.
         */
        public String getName() {
            return name;
        }

        /**
         * @return the parameter value; may be <code>null</code>.
         */
        public String getValue() {
            return value;
        }

        /**
         * Set the value of this parameter.
         *
         * @param value the value of this parameter.
         */
        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Parameter parameter = (Parameter) o;

            return name.equals(parameter.name)
                    && !(value != null
                             ? !value.equals(parameter.value)
                             : parameter.value != null);

        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (value != null) {
                sb.append('=').append(value);
            }
            return sb.toString();
        }
    } // END Parameter

}
