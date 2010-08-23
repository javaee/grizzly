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

package com.sun.grizzly.http.webxml.schema.version_2_3;

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
    "description",
    "resRefName",
    "resType",
    "resAuth",
    "resSharingScope"
})
@XmlRootElement(name = "resource-ref")
public class ResourceRef
    implements Serializable
{

    private final static long serialVersionUID = 1L;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    protected String id;
    protected Description description;
    @XmlElement(name = "res-ref-name", required = true)
    protected ResRefName resRefName;
    @XmlElement(name = "res-type", required = true)
    protected ResType resType;
    @XmlElement(name = "res-auth", required = true)
    protected ResAuth resAuth;
    @XmlElement(name = "res-sharing-scope")
    protected ResSharingScope resSharingScope;
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
     * Gets the value of the description property.
     * 
     * @return
     *     possible object is
     *     {@link Description }
     *     
     */
    public Description getDescription() {
        return description;
    }

    /**
     * Sets the value of the description property.
     * 
     * @param value
     *     allowed object is
     *     {@link Description }
     *     
     */
    public void setDescription(Description value) {
        try {
            support.fireVetoableChange("Description",description, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.description = value;
    }

    /**
     * Gets the value of the resRefName property.
     * 
     * @return
     *     possible object is
     *     {@link ResRefName }
     *     
     */
    public ResRefName getResRefName() {
        return resRefName;
    }

    /**
     * Sets the value of the resRefName property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResRefName }
     *     
     */
    public void setResRefName(ResRefName value) {
        try {
            support.fireVetoableChange("ResRefName",resRefName, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.resRefName = value;
    }

    /**
     * Gets the value of the resType property.
     * 
     * @return
     *     possible object is
     *     {@link ResType }
     *     
     */
    public ResType getResType() {
        return resType;
    }

    /**
     * Sets the value of the resType property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResType }
     *     
     */
    public void setResType(ResType value) {
        try {
            support.fireVetoableChange("ResType",resType, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.resType = value;
    }

    /**
     * Gets the value of the resAuth property.
     * 
     * @return
     *     possible object is
     *     {@link ResAuth }
     *     
     */
    public ResAuth getResAuth() {
        return resAuth;
    }

    /**
     * Sets the value of the resAuth property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResAuth }
     *     
     */
    public void setResAuth(ResAuth value) {
        try {
            support.fireVetoableChange("ResAuth",resAuth, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.resAuth = value;
    }

    /**
     * Gets the value of the resSharingScope property.
     * 
     * @return
     *     possible object is
     *     {@link ResSharingScope }
     *     
     */
    public ResSharingScope getResSharingScope() {
        return resSharingScope;
    }

    /**
     * Sets the value of the resSharingScope property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResSharingScope }
     *     
     */
    public void setResSharingScope(ResSharingScope value) {
        try {
            support.fireVetoableChange("ResSharingScope",resSharingScope, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.resSharingScope = value;
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
