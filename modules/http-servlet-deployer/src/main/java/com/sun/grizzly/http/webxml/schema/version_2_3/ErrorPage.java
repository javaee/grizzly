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
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
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
    "errorCodeOrExceptionType",
    "location"
})
@XmlRootElement(name = "error-page")
public class ErrorPage
    implements Serializable
{

    private final static long serialVersionUID = 1L;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    protected String id;
    @XmlElements({
        @XmlElement(name = "error-code", required = true, type = ErrorCode.class),
        @XmlElement(name = "exception-type", required = true, type = ExceptionType.class)
    })
    protected List<Serializable> errorCodeOrExceptionType;
    @XmlElement(required = true)
    protected Location location;
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
     * Gets the value of the errorCodeOrExceptionType property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the errorCodeOrExceptionType property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getErrorCodeOrExceptionType().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ErrorCode }
     * {@link ExceptionType }
     * 
     * 
     */
    public List<Serializable> getErrorCodeOrExceptionType() {
        if (errorCodeOrExceptionType == null) {
            errorCodeOrExceptionType = new ArrayList<Serializable>();
        }
        return this.errorCodeOrExceptionType;
    }

    /**
     * Gets the value of the location property.
     * 
     * @return
     *     possible object is
     *     {@link Location }
     *     
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Sets the value of the location property.
     * 
     * @param value
     *     allowed object is
     *     {@link Location }
     *     
     */
    public void setLocation(Location value) {
        try {
            support.fireVetoableChange("Location",location, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.location = value;
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
