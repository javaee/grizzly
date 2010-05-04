/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */

package com.sun.grizzly.config.dom;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.Element;
import org.jvnet.hk2.config.types.PropertyBag;

import java.util.List;

/**
 * Defines logic of hosting several protocol on a single tcp port.
 */
@Configured
public interface PortUnification extends ConfigBeanProxy, Injectable, PropertyBag {
    /**
     * Port unification logic implementation class
     */
    @Attribute
    String getClassname();

    void setClassname(String value);

    /**
     * Set of protocol finders, which will be responsible for protocol recognition
     */
    @Element
    List<ProtocolFinder> getProtocolFinder();

    /**
     * If the data came on a network connection is recognized as HTTP packet
     * and it is passed to a default Web protocol - then, if Web protocol sticky
     * flag is enabled, the network connection gets associated with the Web
     * protocol forever, and port unification finder will never be called again
     * for this network connection. If the web protocol sticky flag is
     * <tt>false</tt> - then this time HTTP packet will be passed to a Web protocol,
     * but next time for a next data on this connection - protocol finders will
     * be called again to recognize the target protocol.
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getWebProtocolStickyEnabled();

    /**
     * If the data came on a network connection is recognized as HTTP packet
     * and it is passed to a default Web protocol - then, if Web protocol sticky
     * flag is enabled, the network connection gets associated with the Web
     * protocol forever, and port unification finder will never be called again
     * for this network connection. If the web protocol sticky flag is
     * <tt>false</tt> - then this time HTTP packet will be passed to a Web protocol,
     * but next time for a next data on this connection - protocol finders will
     * be called again to recognize the target protocol.
     */
    void setStickyProtocolEnabled(final String enabled);
}