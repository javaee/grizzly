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
 * 
 * 	  The resource-refType contains a declaration of a
 * 	  Deployment Component's reference to an external resource. It
 * 	  consists of an optional description, the resource manager
 * 	  connection factory reference name, the indication of the
 * 	  resource manager connection factory type expected by the
 * 	  Deployment Component code, the type of authentication
 * 	  (Application or Container), and an optional specification of
 * 	  the shareability of connections obtained from the resource
 * 	  (Shareable or Unshareable).
 * 
 * 	  Example:
 * 
 * 	  <resource-ref>
 * 	      <res-ref-name>jdbc/EmployeeAppDB</res-ref-name>
 * 	      <res-type>javax.sql.DataSource</res-type>
 * 	      <res-auth>Container</res-auth>
 * 	      <res-sharing-scope>Shareable</res-sharing-scope>
 * 	  </resource-ref>
 * 
 * 	  
 *       
 * 
 * <p>Java class for resource-refType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="resource-refType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="description" type="{http://java.sun.com/xml/ns/j2ee}descriptionType" maxOccurs="unbounded" minOccurs="0"/>
 *         &lt;element name="res-ref-name" type="{http://java.sun.com/xml/ns/j2ee}jndi-nameType"/>
 *         &lt;element name="res-type" type="{http://java.sun.com/xml/ns/j2ee}fully-qualified-classType"/>
 *         &lt;element name="res-auth" type="{http://java.sun.com/xml/ns/j2ee}res-authType"/>
 *         &lt;element name="res-sharing-scope" type="{http://java.sun.com/xml/ns/j2ee}res-sharing-scopeType" minOccurs="0"/>
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
@XmlType(name = "resource-refType", propOrder = {
    "description",
    "resRefName",
    "resType",
    "resAuth",
    "resSharingScope"
})
public class ResourceRefType {

    protected List<DescriptionType> description;
    @XmlElement(name = "res-ref-name", required = true)
    protected JndiNameType resRefName;
    @XmlElement(name = "res-type", required = true)
    protected FullyQualifiedClassType resType;
    @XmlElement(name = "res-auth", required = true)
    protected ResAuthType resAuth;
    @XmlElement(name = "res-sharing-scope")
    protected ResSharingScopeType resSharingScope;
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
     * Gets the value of the resRefName property.
     * 
     * @return
     *     possible object is
     *     {@link JndiNameType }
     *     
     */
    public JndiNameType getResRefName() {
        return resRefName;
    }

    /**
     * Sets the value of the resRefName property.
     * 
     * @param value
     *     allowed object is
     *     {@link JndiNameType }
     *     
     */
    public void setResRefName(JndiNameType value) {
        this.resRefName = value;
    }

    /**
     * Gets the value of the resType property.
     * 
     * @return
     *     possible object is
     *     {@link FullyQualifiedClassType }
     *     
     */
    public FullyQualifiedClassType getResType() {
        return resType;
    }

    /**
     * Sets the value of the resType property.
     * 
     * @param value
     *     allowed object is
     *     {@link FullyQualifiedClassType }
     *     
     */
    public void setResType(FullyQualifiedClassType value) {
        this.resType = value;
    }

    /**
     * Gets the value of the resAuth property.
     * 
     * @return
     *     possible object is
     *     {@link ResAuthType }
     *     
     */
    public ResAuthType getResAuth() {
        return resAuth;
    }

    /**
     * Sets the value of the resAuth property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResAuthType }
     *     
     */
    public void setResAuth(ResAuthType value) {
        this.resAuth = value;
    }

    /**
     * Gets the value of the resSharingScope property.
     * 
     * @return
     *     possible object is
     *     {@link ResSharingScopeType }
     *     
     */
    public ResSharingScopeType getResSharingScope() {
        return resSharingScope;
    }

    /**
     * Sets the value of the resSharingScope property.
     * 
     * @param value
     *     allowed object is
     *     {@link ResSharingScopeType }
     *     
     */
    public void setResSharingScope(ResSharingScopeType value) {
        this.resSharingScope = value;
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
