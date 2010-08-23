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
 * 	The service-impl-bean element defines the web service implementation.
 * 	A service implementation can be an EJB bean class or JAX-RPC web
 * 	component.  Existing EJB implementations are exposed as a web service
 * 	using an ejb-link.
 * 
 * 	Used in: port-component
 * 
 *       
 * 
 * <p>Java class for service-impl-beanType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="service-impl-beanType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice>
 *         &lt;element name="ejb-link" type="{http://java.sun.com/xml/ns/javaee}ejb-linkType"/>
 *         &lt;element name="servlet-link" type="{http://java.sun.com/xml/ns/javaee}servlet-linkType"/>
 *       &lt;/choice>
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}ID" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "service-impl-beanType", propOrder = {
    "ejbLink",
    "servletLink"
})
public class ServiceImplBeanType {

    @XmlElement(name = "ejb-link")
    protected EjbLinkType ejbLink;
    @XmlElement(name = "servlet-link")
    protected ServletLinkType servletLink;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;

    /**
     * Gets the value of the ejbLink property.
     * 
     * @return
     *     possible object is
     *     {@link EjbLinkType }
     *     
     */
    public EjbLinkType getEjbLink() {
        return ejbLink;
    }

    /**
     * Sets the value of the ejbLink property.
     * 
     * @param value
     *     allowed object is
     *     {@link EjbLinkType }
     *     
     */
    public void setEjbLink(EjbLinkType value) {
        this.ejbLink = value;
    }

    /**
     * Gets the value of the servletLink property.
     * 
     * @return
     *     possible object is
     *     {@link ServletLinkType }
     *     
     */
    public ServletLinkType getServletLink() {
        return servletLink;
    }

    /**
     * Sets the value of the servletLink property.
     * 
     * @param value
     *     allowed object is
     *     {@link ServletLinkType }
     *     
     */
    public void setServletLink(ServletLinkType value) {
        this.servletLink = value;
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
