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
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;


/**
 * <p>Java class for web-appType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="web-appType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice maxOccurs="unbounded" minOccurs="0">
 *         &lt;group ref="{http://java.sun.com/xml/ns/j2ee}descriptionGroup"/>
 *         &lt;element name="distributable" type="{http://java.sun.com/xml/ns/j2ee}emptyType"/>
 *         &lt;element name="context-param" type="{http://java.sun.com/xml/ns/j2ee}param-valueType"/>
 *         &lt;element name="filter" type="{http://java.sun.com/xml/ns/j2ee}filterType"/>
 *         &lt;element name="filter-mapping" type="{http://java.sun.com/xml/ns/j2ee}filter-mappingType"/>
 *         &lt;element name="listener" type="{http://java.sun.com/xml/ns/j2ee}listenerType"/>
 *         &lt;element name="servlet" type="{http://java.sun.com/xml/ns/j2ee}servletType"/>
 *         &lt;element name="servlet-mapping" type="{http://java.sun.com/xml/ns/j2ee}servlet-mappingType"/>
 *         &lt;element name="session-config" type="{http://java.sun.com/xml/ns/j2ee}session-configType"/>
 *         &lt;element name="mime-mapping" type="{http://java.sun.com/xml/ns/j2ee}mime-mappingType"/>
 *         &lt;element name="welcome-file-list" type="{http://java.sun.com/xml/ns/j2ee}welcome-file-listType"/>
 *         &lt;element name="error-page" type="{http://java.sun.com/xml/ns/j2ee}error-pageType"/>
 *         &lt;element name="jsp-config" type="{http://java.sun.com/xml/ns/j2ee}jsp-configType"/>
 *         &lt;element name="security-constraint" type="{http://java.sun.com/xml/ns/j2ee}security-constraintType"/>
 *         &lt;element name="login-config" type="{http://java.sun.com/xml/ns/j2ee}login-configType"/>
 *         &lt;element name="security-role" type="{http://java.sun.com/xml/ns/j2ee}security-roleType"/>
 *         &lt;group ref="{http://java.sun.com/xml/ns/j2ee}jndiEnvironmentRefsGroup"/>
 *         &lt;element name="message-destination" type="{http://java.sun.com/xml/ns/j2ee}message-destinationType"/>
 *         &lt;element name="locale-encoding-mapping-list" type="{http://java.sun.com/xml/ns/j2ee}locale-encoding-mapping-listType"/>
 *       &lt;/choice>
 *       &lt;attribute name="version" use="required" type="{http://java.sun.com/xml/ns/j2ee}web-app-versionType" />
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}ID" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "web-appType", propOrder = {
    "descriptionAndDisplayNameAndIcon"
})
public class WebAppType {

    @XmlElements({
        @XmlElement(name = "display-name", type = DisplayNameType.class),
        @XmlElement(name = "filter", type = FilterType.class),
        @XmlElement(name = "resource-ref", type = ResourceRefType.class),
        @XmlElement(name = "servlet-mapping", type = ServletMappingType.class),
        @XmlElement(name = "context-param", type = ParamValueType.class),
        @XmlElement(name = "distributable", type = EmptyType.class),
        @XmlElement(name = "jsp-config", type = JspConfigType.class),
        @XmlElement(name = "service-ref", type = ServiceRefType.class),
        @XmlElement(name = "locale-encoding-mapping-list", type = LocaleEncodingMappingListType.class),
        @XmlElement(name = "resource-env-ref", type = ResourceEnvRefType.class),
        @XmlElement(name = "description", type = DescriptionType.class),
        @XmlElement(name = "security-constraint", type = SecurityConstraintType.class),
        @XmlElement(name = "message-destination-ref", type = MessageDestinationRefType.class),
        @XmlElement(name = "listener", type = ListenerType.class),
        @XmlElement(name = "env-entry", type = EnvEntryType.class),
        @XmlElement(name = "ejb-local-ref", type = EjbLocalRefType.class),
        @XmlElement(name = "mime-mapping", type = MimeMappingType.class),
        @XmlElement(name = "login-config", type = LoginConfigType.class),
        @XmlElement(name = "icon", type = IconType.class),
        @XmlElement(name = "session-config", type = SessionConfigType.class),
        @XmlElement(name = "servlet", type = ServletType.class),
        @XmlElement(name = "message-destination", type = MessageDestinationType.class),
        @XmlElement(name = "filter-mapping", type = FilterMappingType.class),
        @XmlElement(name = "error-page", type = ErrorPageType.class),
        @XmlElement(name = "ejb-ref", type = EjbRefType.class),
        @XmlElement(name = "welcome-file-list", type = WelcomeFileListType.class),
        @XmlElement(name = "security-role", type = SecurityRoleType.class)
    })
    protected List<Object> descriptionAndDisplayNameAndIcon;
    @XmlAttribute(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected java.lang.String version;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;

    /**
     * Gets the value of the descriptionAndDisplayNameAndIcon property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the descriptionAndDisplayNameAndIcon property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDescriptionAndDisplayNameAndIcon().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DisplayNameType }
     * {@link FilterType }
     * {@link ResourceRefType }
     * {@link ServletMappingType }
     * {@link ParamValueType }
     * {@link EmptyType }
     * {@link JspConfigType }
     * {@link ServiceRefType }
     * {@link LocaleEncodingMappingListType }
     * {@link ResourceEnvRefType }
     * {@link DescriptionType }
     * {@link SecurityConstraintType }
     * {@link MessageDestinationRefType }
     * {@link ListenerType }
     * {@link EnvEntryType }
     * {@link EjbLocalRefType }
     * {@link MimeMappingType }
     * {@link LoginConfigType }
     * {@link IconType }
     * {@link SessionConfigType }
     * {@link ServletType }
     * {@link MessageDestinationType }
     * {@link FilterMappingType }
     * {@link ErrorPageType }
     * {@link EjbRefType }
     * {@link WelcomeFileListType }
     * {@link SecurityRoleType }
     * 
     * 
     */
    public List<Object> getDescriptionAndDisplayNameAndIcon() {
        if (descriptionAndDisplayNameAndIcon == null) {
            descriptionAndDisplayNameAndIcon = new ArrayList<Object>();
        }
        return this.descriptionAndDisplayNameAndIcon;
    }

    /**
     * Gets the value of the version property.
     * 
     * @return
     *     possible object is
     *     {@link java.lang.String }
     *     
     */
    public java.lang.String getVersion() {
        return version;
    }

    /**
     * Sets the value of the version property.
     * 
     * @param value
     *     allowed object is
     *     {@link java.lang.String }
     *     
     */
    public void setVersion(java.lang.String value) {
        this.version = value;
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
