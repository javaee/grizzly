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
 * 
 * 	  The persistence-unit-ref element contains a declaration
 * 	  of Deployment Component's reference to a persistence unit
 * 	  associated within a Deployment Component's
 * 	  environment. It consists of:
 * 
 * 		  - an optional description
 * 		  - the persistence unit reference name
 * 		  - an optional persistence unit name.  If not specified,
 *                     the default persistence unit is assumed.
 * 		  - optional injection targets
 * 
 * 	  Examples:
 * 
 *             <persistence-unit-ref>
 *               <persistence-unit-ref-name>myPersistenceUnit
 *               </persistence-unit-ref-name>
 *             </persistence-unit-ref>
 * 
 *             <persistence-unit-ref>
 *               <persistence-unit-ref-name>myPersistenceUnit
 *                 </persistence-unit-ref-name>
 *               <persistence-unit-name>PersistenceUnit1
 *                 </persistence-unit-name>
 *             </persistence-unit-ref>
 * 
 * 	  
 *       
 * 
 * <p>Java class for persistence-unit-refType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="persistence-unit-refType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="description" type="{http://java.sun.com/xml/ns/javaee}descriptionType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="persistence-unit-ref-name" type="{http://java.sun.com/xml/ns/javaee}jndi-nameType"/>
 *         &lt;element name="persistence-unit-name" type="{http://java.sun.com/xml/ns/javaee}string" minOccurs="0"/>
 *         &lt;group ref="{http://java.sun.com/xml/ns/javaee}resourceGroup"/>
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
@XmlType(name = "persistence-unit-refType", propOrder = {
    "description",
    "persistenceUnitRefName",
    "persistenceUnitName",
    "mappedName",
    "injectionTarget"
})
public class PersistenceUnitRefType {

    protected List<DescriptionType> description;
    @XmlElement(name = "persistence-unit-ref-name", required = true)
    protected JndiNameType persistenceUnitRefName;
    @XmlElement(name = "persistence-unit-name")
    protected com.sun.grizzly.http.webxml.schema.version_2_5.String persistenceUnitName;
    @XmlElement(name = "mapped-name")
    protected XsdStringType mappedName;
    @XmlElement(name = "injection-target")
    protected List<InjectionTargetType> injectionTarget;
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
     * Gets the value of the persistenceUnitRefName property.
     * 
     * @return
     *     possible object is
     *     {@link JndiNameType }
     *     
     */
    public JndiNameType getPersistenceUnitRefName() {
        return persistenceUnitRefName;
    }

    /**
     * Sets the value of the persistenceUnitRefName property.
     * 
     * @param value
     *     allowed object is
     *     {@link JndiNameType }
     *     
     */
    public void setPersistenceUnitRefName(JndiNameType value) {
        this.persistenceUnitRefName = value;
    }

    /**
     * Gets the value of the persistenceUnitName property.
     * 
     * @return
     *     possible object is
     *     {@link com.sun.grizzly.http.webxml.schema.version_2_5.String }
     *     
     */
    public com.sun.grizzly.http.webxml.schema.version_2_5.String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    /**
     * Sets the value of the persistenceUnitName property.
     * 
     * @param value
     *     allowed object is
     *     {@link com.sun.grizzly.http.webxml.schema.version_2_5.String }
     *     
     */
    public void setPersistenceUnitName(com.sun.grizzly.http.webxml.schema.version_2_5.String value) {
        this.persistenceUnitName = value;
    }

    /**
     * Gets the value of the mappedName property.
     * 
     * @return
     *     possible object is
     *     {@link XsdStringType }
     *     
     */
    public XsdStringType getMappedName() {
        return mappedName;
    }

    /**
     * Sets the value of the mappedName property.
     * 
     * @param value
     *     allowed object is
     *     {@link XsdStringType }
     *     
     */
    public void setMappedName(XsdStringType value) {
        this.mappedName = value;
    }

    /**
     * Gets the value of the injectionTarget property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the injectionTarget property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getInjectionTarget().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link InjectionTargetType }
     * 
     * 
     */
    public List<InjectionTargetType> getInjectionTarget() {
        if (injectionTarget == null) {
            injectionTarget = new ArrayList<InjectionTargetType>();
        }
        return this.injectionTarget;
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
