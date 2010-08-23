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

package com.sun.grizzly.http.webxml.schema.version_3_0;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlList;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * 
 * 
 *       The handler-chain element defines the handlerchain.
 *       Handlerchain can be defined such that the handlers in the
 *       handlerchain operate,all ports of a service, on a specific
 *       port or on a list of protocol-bindings. The choice of elements
 *       service-name-pattern, port-name-pattern and protocol-bindings
 *       are used to specify whether the handlers in handler-chain are
 *       for a service, port or protocol binding. If none of these
 *       choices are specified with the handler-chain element then the
 *       handlers specified in the handler-chain will be applied on
 *       everything.
 * 
 *       
 * 
 * <p>Java class for service-ref_handler-chainType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="service-ref_handler-chainType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;choice minOccurs="0">
 *           &lt;element name="service-name-pattern" type="{http://java.sun.com/xml/ns/javaee}service-ref_qname-pattern"/>
 *           &lt;element name="port-name-pattern" type="{http://java.sun.com/xml/ns/javaee}service-ref_qname-pattern"/>
 *           &lt;element name="protocol-bindings" type="{http://java.sun.com/xml/ns/javaee}service-ref_protocol-bindingListType"/>
 *         &lt;/choice>
 *         &lt;element name="handler" type="{http://java.sun.com/xml/ns/javaee}service-ref_handlerType" maxOccurs="unbounded"/>
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
@XmlType(name = "service-ref_handler-chainType", propOrder = {
    "serviceNamePattern",
    "portNamePattern",
    "protocolBindings",
    "handler"
})
public class ServiceRefHandlerChainType {

    @XmlElement(name = "service-name-pattern")
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected java.lang.String serviceNamePattern;
    @XmlElement(name = "port-name-pattern")
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected java.lang.String portNamePattern;
    @XmlList
    @XmlElement(name = "protocol-bindings")
    protected List<java.lang.String> protocolBindings;
    @XmlElement(required = true)
    protected List<ServiceRefHandlerType> handler;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;

    /**
     * Gets the value of the serviceNamePattern property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getServiceNamePattern() {
        return serviceNamePattern;
    }

    /**
     * Sets the value of the serviceNamePattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setServiceNamePattern(java.lang.String value) {
        this.serviceNamePattern = value;
    }

    /**
     * Gets the value of the portNamePattern property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getPortNamePattern() {
        return portNamePattern;
    }

    /**
     * Sets the value of the portNamePattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setPortNamePattern(java.lang.String value) {
        this.portNamePattern = value;
    }

    /**
     * Gets the value of the protocolBindings property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the protocolBindings property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getProtocolBindings().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link java.lang.String }
     * 
     * 
     */
    public List<java.lang.String> getProtocolBindings() {
        if (protocolBindings == null) {
            protocolBindings = new ArrayList<java.lang.String>();
        }
        return this.protocolBindings;
    }

    /**
     * Gets the value of the handler property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the handler property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHandler().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServiceRefHandlerType }
     * 
     * 
     */
    public List<ServiceRefHandlerType> getHandler() {
        if (handler == null) {
            handler = new ArrayList<ServiceRefHandlerType>();
        }
        return this.handler;
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
