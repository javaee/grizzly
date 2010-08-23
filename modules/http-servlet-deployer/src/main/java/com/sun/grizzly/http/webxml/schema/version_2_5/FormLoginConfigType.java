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

package com.sun.grizzly.http.webxml.schema.version_2_5;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * 
 * 
 * 	The form-login-configType specifies the login and error
 * 	pages that should be used in form based login. If form based
 * 	authentication is not used, these elements are ignored.
 * 
 * 	Used in: login-config
 * 
 *       
 * 
 * <p>Java class for form-login-configType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="form-login-configType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="form-login-page" type="{http://java.sun.com/xml/ns/javaee}war-pathType"/>
 *         &lt;element name="form-error-page" type="{http://java.sun.com/xml/ns/javaee}war-pathType"/>
 *       &lt;/sequence>
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}ID" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "form-login-configType", propOrder = {
    "formLoginPage",
    "formErrorPage"
})
public class FormLoginConfigType {

    @XmlElement(name = "form-login-page", required = true)
    protected WarPathType formLoginPage;
    @XmlElement(name = "form-error-page", required = true)
    protected WarPathType formErrorPage;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;

    /**
     * Gets the value of the formLoginPage property.
     * 
     * @return
     *     possible object is
     *     {@link WarPathType }
     *     
     */
    public WarPathType getFormLoginPage() {
        return formLoginPage;
    }

    /**
     * Sets the value of the formLoginPage property.
     * 
     * @param value
     *     allowed object is
     *     {@link WarPathType }
     *     
     */
    public void setFormLoginPage(WarPathType value) {
        this.formLoginPage = value;
    }

    /**
     * Gets the value of the formErrorPage property.
     * 
     * @return
     *     possible object is
     *     {@link WarPathType }
     *     
     */
    public WarPathType getFormErrorPage() {
        return formErrorPage;
    }

    /**
     * Sets the value of the formErrorPage property.
     * 
     * @param value
     *     allowed object is
     *     {@link WarPathType }
     *     
     */
    public void setFormErrorPage(WarPathType value) {
        this.formErrorPage = value;
    }

    /**
     * Gets the value of the id property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getId() {
        return id;
    }

    /**
     * Sets the value of the id property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setId(java.lang.String value) {
        this.id = value;
    }

}
