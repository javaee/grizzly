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
 * 	  The message-destinationType specifies a message
 * 	  destination. The logical destination described by this
 * 	  element is mapped to a physical destination by the Deployer.
 * 
 * 	  The message destination element contains:
 * 
 * 		  - an optional description
 * 		  - an optional display-name
 * 		  - an optional icon
 * 		  - a message destination name which must be unique
 * 		    among message destination names within the same
 * 		    Deployment File.
 * 
 * 	  Example:
 * 
 * 	  <message-destination>
 * 		  <message-destination-name>CorporateStocks
 * 		  </message-destination-name>
 * 	  </message-destination>
 * 
 * 	  
 *       
 * 
 * <p>Java class for message-destinationType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="message-destinationType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;group ref="{http://java.sun.com/xml/ns/j2ee}descriptionGroup"/>
 *         &lt;element name="message-destination-name" type="{http://java.sun.com/xml/ns/j2ee}string"/>
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
@XmlType(name = "message-destinationType", propOrder = {
    "description",
    "displayName",
    "icon",
    "messageDestinationName"
})
public class MessageDestinationType {

    protected List<DescriptionType> description;
    @XmlElement(name = "display-name")
    protected List<DisplayNameType> displayName;
    protected List<IconType> icon;
    @XmlElement(name = "message-destination-name", required = true)
    protected com.sun.grizzly.http.webxml.schema.version_2_4.String messageDestinationName;
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
     * Gets the value of the displayName property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the displayName property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDisplayName().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DisplayNameType }
     * 
     * 
     */
    public List<DisplayNameType> getDisplayName() {
        if (displayName == null) {
            displayName = new ArrayList<DisplayNameType>();
        }
        return this.displayName;
    }

    /**
     * Gets the value of the icon property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the icon property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getIcon().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link IconType }
     * 
     * 
     */
    public List<IconType> getIcon() {
        if (icon == null) {
            icon = new ArrayList<IconType>();
        }
        return this.icon;
    }

    /**
     * Gets the value of the messageDestinationName property.
     * 
     * @return
     *     possible object is
     *     {@link com.sun.grizzly.http.webxml.schema.version_2_4.String }
     *     
     */
    public com.sun.grizzly.http.webxml.schema.version_2_4.String getMessageDestinationName() {
        return messageDestinationName;
    }

    /**
     * Sets the value of the messageDestinationName property.
     * 
     * @param value
     *     allowed object is
     *     {@link com.sun.grizzly.http.webxml.schema.version_2_4.String }
     *     
     */
    public void setMessageDestinationName(com.sun.grizzly.http.webxml.schema.version_2_4.String value) {
        this.messageDestinationName = value;
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
