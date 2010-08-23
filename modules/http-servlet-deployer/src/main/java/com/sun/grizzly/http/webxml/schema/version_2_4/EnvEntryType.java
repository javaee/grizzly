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

package com.sun.grizzly.http.webxml.schema.version_2_4;

import java.util.ArrayList;
import java.util.List;
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
 * 	The env-entryType is used to declare an application's
 * 	environment entry. The declaration consists of an optional
 * 	description, the name of the environment entry, and an
 * 	optional value.  If a value is not specified, one must be
 * 	supplied during deployment.
 * 
 * 	It is used by env-entry elements.
 * 
 *       
 * 
 * <p>Java class for env-entryType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="env-entryType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="description" type="{http://java.sun.com/xml/ns/j2ee}descriptionType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="env-entry-name" type="{http://java.sun.com/xml/ns/j2ee}jndi-nameType"/>
 *         &lt;element name="env-entry-type" type="{http://java.sun.com/xml/ns/j2ee}env-entry-type-valuesType"/>
 *         &lt;element name="env-entry-value" type="{http://java.sun.com/xml/ns/j2ee}xsdStringType" minOccurs="0"/>
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
@XmlType(name = "env-entryType", propOrder = {
    "description",
    "envEntryName",
    "envEntryType",
    "envEntryValue"
})
public class EnvEntryType {

    protected List<DescriptionType> description;
    @XmlElement(name = "env-entry-name", required = true)
    protected JndiNameType envEntryName;
    @XmlElement(name = "env-entry-type", required = true)
    protected EnvEntryTypeValuesType envEntryType;
    @XmlElement(name = "env-entry-value")
    protected XsdStringType envEntryValue;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;

    /**
     * Gets the value of the description property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the description property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDescription().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DescriptionType }
     * 
     * 
     */
    public List<DescriptionType> getDescription() {
        if (description == null) {
            description = new ArrayList<DescriptionType>();
        }
        return this.description;
    }

    /**
     * Gets the value of the envEntryName property.
     * 
     * @return
     *     possible object is
     *     {@link JndiNameType }
     *     
     */
    public JndiNameType getEnvEntryName() {
        return envEntryName;
    }

    /**
     * Sets the value of the envEntryName property.
     * 
     * @param value
     *     allowed object is
     *     {@link JndiNameType }
     *     
     */
    public void setEnvEntryName(JndiNameType value) {
        this.envEntryName = value;
    }

    /**
     * Gets the value of the envEntryType property.
     * 
     * @return
     *     possible object is
     *     {@link EnvEntryTypeValuesType }
     *     
     */
    public EnvEntryTypeValuesType getEnvEntryType() {
        return envEntryType;
    }

    /**
     * Sets the value of the envEntryType property.
     * 
     * @param value
     *     allowed object is
     *     {@link EnvEntryTypeValuesType }
     *     
     */
    public void setEnvEntryType(EnvEntryTypeValuesType value) {
        this.envEntryType = value;
    }

    /**
     * Gets the value of the envEntryValue property.
     * 
     * @return
     *     possible object is
     *     {@link XsdStringType }
     *     
     */
    public XsdStringType getEnvEntryValue() {
        return envEntryValue;
    }

    /**
     * Sets the value of the envEntryValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link XsdStringType }
     *     
     */
    public void setEnvEntryValue(XsdStringType value) {
        this.envEntryValue = value;
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
