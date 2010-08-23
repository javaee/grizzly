/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.webxml.schema.version_2_2;

import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.Serializable;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "formLoginPage",
    "formErrorPage"
})
@XmlRootElement(name = "form-login-config")
public class FormLoginConfig
    implements Serializable
{

    private final static long serialVersionUID = 1L;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    protected String id;
    @XmlElement(name = "form-login-page", required = true)
    protected FormLoginPage formLoginPage;
    @XmlElement(name = "form-error-page", required = true)
    protected FormErrorPage formErrorPage;
    @XmlTransient
    private VetoableChangeSupport support = (new VetoableChangeSupport(this));

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setId(String value) {
        try {
            support.fireVetoableChange("Id",id, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.id = value;
    }

    /**
     * Gets the value of the formLoginPage property.
     * 
     * @return
     *     possible object is
     *     {@link FormLoginPage }
     *     
     */
    public FormLoginPage getFormLoginPage() {
        return formLoginPage;
    }

    /**
     * Sets the value of the formLoginPage property.
     * 
     * @param value
     *     allowed object is
     *     {@link FormLoginPage }
     *     
     */
    public void setFormLoginPage(FormLoginPage value) {
        try {
            support.fireVetoableChange("FormLoginPage",formLoginPage, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.formLoginPage = value;
    }

    /**
     * Gets the value of the formErrorPage property.
     * 
     * @return
     *     possible object is
     *     {@link FormErrorPage }
     *     
     */
    public FormErrorPage getFormErrorPage() {
        return formErrorPage;
    }

    /**
     * Sets the value of the formErrorPage property.
     * 
     * @param value
     *     allowed object is
     *     {@link FormErrorPage }
     *     
     */
    public void setFormErrorPage(FormErrorPage value) {
        try {
            support.fireVetoableChange("FormErrorPage",formErrorPage, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.formErrorPage = value;
    }

    public void addVetoableChangeListener(VetoableChangeListener param0) {
        support.addVetoableChangeListener(param0);
    }

    public void addVetoableChangeListener(String param0, VetoableChangeListener param1) {
        support.addVetoableChangeListener(param0, param1);
    }

    public void removeVetoableChangeListener(String param0, VetoableChangeListener param1) {
        support.removeVetoableChangeListener(param0, param1);
    }

    public void removeVetoableChangeListener(VetoableChangeListener param0) {
        support.removeVetoableChangeListener(param0);
    }

}
