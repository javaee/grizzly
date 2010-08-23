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
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
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
 *         &lt;group ref="{http://java.sun.com/xml/ns/javaee}descriptionGroup"/>
 *         &lt;element name="distributable" type="{http://java.sun.com/xml/ns/javaee}emptyType"/>
 *         &lt;element name="context-param" type="{http://java.sun.com/xml/ns/javaee}param-valueType"/>
 *         &lt;element name="filter" type="{http://java.sun.com/xml/ns/javaee}filterType"/>
 *         &lt;element name="filter-mapping" type="{http://java.sun.com/xml/ns/javaee}filter-mappingType"/>
 *         &lt;element name="listener" type="{http://java.sun.com/xml/ns/javaee}listenerType"/>
 *         &lt;element name="servlet" type="{http://java.sun.com/xml/ns/javaee}servletType"/>
 *         &lt;element name="servlet-mapping" type="{http://java.sun.com/xml/ns/javaee}servlet-mappingType"/>
 *         &lt;element name="session-config" type="{http://java.sun.com/xml/ns/javaee}session-configType"/>
 *         &lt;element name="mime-mapping" type="{http://java.sun.com/xml/ns/javaee}mime-mappingType"/>
 *         &lt;element name="welcome-file-list" type="{http://java.sun.com/xml/ns/javaee}welcome-file-listType"/>
 *         &lt;element name="error-page" type="{http://java.sun.com/xml/ns/javaee}error-pageType"/>
 *         &lt;element name="jsp-config" type="{http://java.sun.com/xml/ns/javaee}jsp-configType"/>
 *         &lt;element name="security-constraint" type="{http://java.sun.com/xml/ns/javaee}security-constraintType"/>
 *         &lt;element name="login-config" type="{http://java.sun.com/xml/ns/javaee}login-configType"/>
 *         &lt;element name="security-role" type="{http://java.sun.com/xml/ns/javaee}security-roleType"/>
 *         &lt;group ref="{http://java.sun.com/xml/ns/javaee}jndiEnvironmentRefsGroup"/>
 *         &lt;element name="message-destination" type="{http://java.sun.com/xml/ns/javaee}message-destinationType"/>
 *         &lt;element name="locale-encoding-mapping-list" type="{http://java.sun.com/xml/ns/javaee}locale-encoding-mapping-listType"/>
 *       &lt;/choice>
 *       &lt;attribute name="version" use="required" type="{http://java.sun.com/xml/ns/javaee}web-app-versionType" />
 *       &lt;attribute name="id" type="{http://www.w3.org/2001/XMLSchema}ID" />
 *       &lt;attribute name="metadata-complete" type="{http://www.w3.org/2001/XMLSchema}boolean" />
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

    @XmlElementRefs({
        @XmlElementRef(name = "filter", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "env-entry", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "security-role", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "persistence-context-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "service-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "security-constraint", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "servlet", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "listener", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "locale-encoding-mapping-list", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "message-destination-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "pre-destroy", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "resource-env-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "description", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "post-construct", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "message-destination", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "jsp-config", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "ejb-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "resource-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "mime-mapping", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "filter-mapping", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "session-config", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "persistence-unit-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "display-name", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "login-config", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "ejb-local-ref", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "context-param", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "welcome-file-list", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "icon", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "servlet-mapping", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "distributable", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class),
        @XmlElementRef(name = "error-page", namespace = "http://java.sun.com/xml/ns/javaee", type = JAXBElement.class)
    })
    protected List<JAXBElement<?>> descriptionAndDisplayNameAndIcon;
    @XmlAttribute(required = true)
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    protected java.lang.String version;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    @XmlSchemaType(name = "ID")
    protected java.lang.String id;
    @XmlAttribute(name = "metadata-complete")
    protected Boolean metadataComplete;

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
     * {@link JAXBElement }{@code <}{@link FilterType }{@code >}
     * {@link JAXBElement }{@code <}{@link EnvEntryType }{@code >}
     * {@link JAXBElement }{@code <}{@link SecurityRoleType }{@code >}
     * {@link JAXBElement }{@code <}{@link PersistenceContextRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link ServiceRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link SecurityConstraintType }{@code >}
     * {@link JAXBElement }{@code <}{@link ServletType }{@code >}
     * {@link JAXBElement }{@code <}{@link ListenerType }{@code >}
     * {@link JAXBElement }{@code <}{@link LocaleEncodingMappingListType }{@code >}
     * {@link JAXBElement }{@code <}{@link MessageDestinationRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link LifecycleCallbackType }{@code >}
     * {@link JAXBElement }{@code <}{@link ResourceEnvRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link DescriptionType }{@code >}
     * {@link JAXBElement }{@code <}{@link LifecycleCallbackType }{@code >}
     * {@link JAXBElement }{@code <}{@link MessageDestinationType }{@code >}
     * {@link JAXBElement }{@code <}{@link JspConfigType }{@code >}
     * {@link JAXBElement }{@code <}{@link EjbRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link ResourceRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link MimeMappingType }{@code >}
     * {@link JAXBElement }{@code <}{@link FilterMappingType }{@code >}
     * {@link JAXBElement }{@code <}{@link SessionConfigType }{@code >}
     * {@link JAXBElement }{@code <}{@link PersistenceUnitRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link DisplayNameType }{@code >}
     * {@link JAXBElement }{@code <}{@link LoginConfigType }{@code >}
     * {@link JAXBElement }{@code <}{@link ParamValueType }{@code >}
     * {@link JAXBElement }{@code <}{@link EjbLocalRefType }{@code >}
     * {@link JAXBElement }{@code <}{@link WelcomeFileListType }{@code >}
     * {@link JAXBElement }{@code <}{@link EmptyType }{@code >}
     * {@link JAXBElement }{@code <}{@link ServletMappingType }{@code >}
     * {@link JAXBElement }{@code <}{@link IconType }{@code >}
     * {@link JAXBElement }{@code <}{@link ErrorPageType }{@code >}
     * 
     * 
     */
    public List<JAXBElement<?>> getDescriptionAndDisplayNameAndIcon() {
        if (descriptionAndDisplayNameAndIcon == null) {
            descriptionAndDisplayNameAndIcon = new ArrayList<JAXBElement<?>>();
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

    /**
     * Gets the value of the metadataComplete property.
     * 
     * @return
     *     possible object is
     *     {@link Boolean }
     *     
     */
    public Boolean isMetadataComplete() {
        return metadataComplete;
    }

    /**
     * Sets the value of the metadataComplete property.
     * 
     * @param value
     *     allowed object is
     *     {@link Boolean }
     *     
     */
    public void setMetadataComplete(Boolean value) {
        this.metadataComplete = value;
    }

}
