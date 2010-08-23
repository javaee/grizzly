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
 *         The cookie-configType defines the configuration for the
 *         session tracking cookies of this web application.
 * 
 *         Used in: session-config
 * 
 *       
 * 
 * <p>Java class for cookie-configType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="cookie-configType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="name" type="{http://java.sun.com/xml/ns/javaee}cookie-nameType" minOccurs="0"/>
 *         &lt;element name="domain" type="{http://java.sun.com/xml/ns/javaee}cookie-domainType" minOccurs="0"/>
 *         &lt;element name="path" type="{http://java.sun.com/xml/ns/javaee}cookie-pathType" minOccurs="0"/>
 *         &lt;element name="comment" type="{http://java.sun.com/xml/ns/javaee}cookie-commentType" minOccurs="0"/>
 *         &lt;element name="http-only" type="{http://java.sun.com/xml/ns/javaee}true-falseType" minOccurs="0"/>
 *         &lt;element name="secure" type="{http://java.sun.com/xml/ns/javaee}true-falseType" minOccurs="0"/>
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
@XmlType(name = "cookie-configType", propOrder = {
    "name",
    "domain",
    "path",
    "comment",
    "httpOnly",
    "secure"
})
public class CookieConfigType {

    protected CookieNameType name;
    protected CookieDomainType domain;
    protected CookiePathType path;
    protected CookieCommentType comment;
    @XmlElement(name = "http-only")
    protected TrueFalseType httpOnly;
    protected TrueFalseType secure;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link CookieNameType }
     *     
     */
    public CookieNameType getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link CookieNameType }
     *     
     */
    public void setName(CookieNameType value) {
        this.name = value;
    }

    /**
     * Gets the value of the domain property.
     * 
     * @return
     *     possible object is
     *     {@link CookieDomainType }
     *     
     */
    public CookieDomainType getDomain() {
        return domain;
    }

    /**
     * Sets the value of the domain property.
     * 
     * @param value
     *     allowed object is
     *     {@link CookieDomainType }
     *     
     */
    public void setDomain(CookieDomainType value) {
        this.domain = value;
    }

    /**
     * Gets the value of the path property.
     * 
     * @return
     *     possible object is
     *     {@link CookiePathType }
     *     
     */
    public CookiePathType getPath() {
        return path;
    }

    /**
     * Sets the value of the path property.
     * 
     * @param value
     *     allowed object is
     *     {@link CookiePathType }
     *     
     */
    public void setPath(CookiePathType value) {
        this.path = value;
    }

    /**
     * Gets the value of the comment property.
     * 
     * @return
     *     possible object is
     *     {@link CookieCommentType }
     *     
     */
    public CookieCommentType getComment() {
        return comment;
    }

    /**
     * Sets the value of the comment property.
     * 
     * @param value
     *     allowed object is
     *     {@link CookieCommentType }
     *     
     */
    public void setComment(CookieCommentType value) {
        this.comment = value;
    }

    /**
     * Gets the value of the httpOnly property.
     * 
     * @return
     *     possible object is
     *     {@link TrueFalseType }
     *     
     */
    public TrueFalseType getHttpOnly() {
        return httpOnly;
    }

    /**
     * Sets the value of the httpOnly property.
     * 
     * @param value
     *     allowed object is
     *     {@link TrueFalseType }
     *     
     */
    public void setHttpOnly(TrueFalseType value) {
        this.httpOnly = value;
    }

    /**
     * Gets the value of the secure property.
     * 
     * @return
     *     possible object is
     *     {@link TrueFalseType }
     *     
     */
    public TrueFalseType getSecure() {
        return secure;
    }

    /**
     * Sets the value of the secure property.
     * 
     * @param value
     *     allowed object is
     *     {@link TrueFalseType }
     *     
     */
    public void setSecure(TrueFalseType value) {
        this.secure = value;
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
