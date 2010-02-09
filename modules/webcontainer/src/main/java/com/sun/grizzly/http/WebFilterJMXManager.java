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

package com.sun.grizzly.http;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import com.sun.grizzly.tcp.RequestGroupInfo;

/**
 *
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public class WebFilterJMXManager {

    protected WebFilter webFilter;

    // ----------------------------------------------------- JMX Support ---/
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;
    /**
     * The JMX Management class.
     */
    private Management jmxManagement = null;


    public WebFilterJMXManager(WebFilter webFilter) {
        this.webFilter = webFilter;
    }

    public void registerComponent(Object bean, ObjectName oname,String type)
            throws Exception {
        if (domain != null && jmxManagement != null) {
            jmxManagement.registerComponent(bean, oname, type);
        }
    }

    public void unregisterComponent(ObjectName oname) throws Exception {
        if (domain != null && jmxManagement != null) {
            jmxManagement.unregisterComponent(oname);
        }
    }

    // ------------------------------- JMX and Monnitoring support --------//

    /**
     * Return the {@link Management} interface, or null if JMX management is
     * no enabled.
     * @return the {@link Management}
     */
    public Management getManagement() {
        return jmxManagement;
    }


    /**
     * Set the {@link Management} interface. Setting this interface automatically
     * expose Grizzl Http Engine mbeans.
     * @param jmxManagement
     */
    public void setManagement(Management jmxManagement) {
        this.jmxManagement = jmxManagement;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public MBeanServer getMserver() {
        return mserver;
    }

    public void setMserver(MBeanServer mserver) {
        this.mserver = mserver;
    }

    public ObjectName getOname() {
        return oname;
    }

    public void setOname(ObjectName oname) {
        this.oname = oname;
    }
}
