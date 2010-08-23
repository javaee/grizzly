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
    "icon",
    "displayName",
    "description",
    "distributable",
    "contextParam",
    "filter",
    "filterMapping",
    "listener",
    "servlet",
    "servletMapping",
    "sessionConfig",
    "mimeMapping",
    "welcomeFileList",
    "errorPage",
    "taglib",
    "resourceEnvRef",
    "resourceRef",
    "securityConstraint",
    "loginConfig",
    "securityRole",
    "envEntry",
    "ejbRef",
    "ejbLocalRef"
})
@XmlRootElement(name = "web-app")
public class WebApp
    implements Serializable
{

    private final static long serialVersionUID = 1L;
    @XmlAttribute
    @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
    @XmlID
    protected String id;
    protected Icon icon;
    @XmlElement(name = "display-name")
    protected DisplayName displayName;
    protected Description description;
    protected Distributable distributable;
    @XmlElement(name = "context-param")
    protected List<ContextParam> contextParam;
    protected List<Filter> filter;
    @XmlElement(name = "filter-mapping")
    protected List<FilterMapping> filterMapping;
    protected List<Listener> listener;
    protected List<Servlet> servlet;
    @XmlElement(name = "servlet-mapping")
    protected List<ServletMapping> servletMapping;
    @XmlElement(name = "session-config")
    protected SessionConfig sessionConfig;
    @XmlElement(name = "mime-mapping")
    protected List<MimeMapping> mimeMapping;
    @XmlElement(name = "welcome-file-list")
    protected WelcomeFileList welcomeFileList;
    @XmlElement(name = "error-page")
    protected List<ErrorPage> errorPage;
    protected List<Taglib> taglib;
    @XmlElement(name = "resource-env-ref")
    protected List<ResourceEnvRef> resourceEnvRef;
    @XmlElement(name = "resource-ref")
    protected List<ResourceRef> resourceRef;
    @XmlElement(name = "security-constraint")
    protected List<SecurityConstraint> securityConstraint;
    @XmlElement(name = "login-config")
    protected LoginConfig loginConfig;
    @XmlElement(name = "security-role")
    protected List<SecurityRole> securityRole;
    @XmlElement(name = "env-entry")
    protected List<EnvEntry> envEntry;
    @XmlElement(name = "ejb-ref")
    protected List<EjbRef> ejbRef;
    @XmlElement(name = "ejb-local-ref")
    protected List<EjbLocalRef> ejbLocalRef;
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
     * Gets the value of the icon property.
     * 
     * @return
     *     possible object is
     *     {@link Icon }
     *     
     */
    public Icon getIcon() {
        return icon;
    }

    /**
     * Sets the value of the icon property.
     * 
     * @param value
     *     allowed object is
     *     {@link Icon }
     *     
     */
    public void setIcon(Icon value) {
        try {
            support.fireVetoableChange("Icon",icon, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.icon = value;
    }

    /**
     * Gets the value of the displayName property.
     * 
     * @return
     *     possible object is
     *     {@link DisplayName }
     *     
     */
    public DisplayName getDisplayName() {
        return displayName;
    }

    /**
     * Sets the value of the displayName property.
     * 
     * @param value
     *     allowed object is
     *     {@link DisplayName }
     *     
     */
    public void setDisplayName(DisplayName value) {
        try {
            support.fireVetoableChange("DisplayName",displayName, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.displayName = value;
    }

    /**
     * Gets the value of the description property.
     * 
     * @return
     *     possible object is
     *     {@link Description }
     *     
     */
    public Description getDescription() {
        return description;
    }

    /**
     * Sets the value of the description property.
     * 
     * @param value
     *     allowed object is
     *     {@link Description }
     *     
     */
    public void setDescription(Description value) {
        try {
            support.fireVetoableChange("Description",description, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.description = value;
    }

    /**
     * Gets the value of the distributable property.
     * 
     * @return
     *     possible object is
     *     {@link Distributable }
     *     
     */
    public Distributable getDistributable() {
        return distributable;
    }

    /**
     * Sets the value of the distributable property.
     * 
     * @param value
     *     allowed object is
     *     {@link Distributable }
     *     
     */
    public void setDistributable(Distributable value) {
        try {
            support.fireVetoableChange("Distributable",distributable, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.distributable = value;
    }

    /**
     * Gets the value of the contextParam property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the contextParam property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getContextParam().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ContextParam }
     * 
     * 
     */
    public List<ContextParam> getContextParam() {
        if (contextParam == null) {
            contextParam = new ArrayList<ContextParam>();
        }
        return this.contextParam;
    }

    /**
     * Gets the value of the filter property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the filter property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFilter().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Filter }
     * 
     * 
     */
    public List<Filter> getFilter() {
        if (filter == null) {
            filter = new ArrayList<Filter>();
        }
        return this.filter;
    }

    /**
     * Gets the value of the filterMapping property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the filterMapping property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFilterMapping().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link FilterMapping }
     * 
     * 
     */
    public List<FilterMapping> getFilterMapping() {
        if (filterMapping == null) {
            filterMapping = new ArrayList<FilterMapping>();
        }
        return this.filterMapping;
    }

    /**
     * Gets the value of the listener property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the listener property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getListener().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Listener }
     * 
     * 
     */
    public List<Listener> getListener() {
        if (listener == null) {
            listener = new ArrayList<Listener>();
        }
        return this.listener;
    }

    /**
     * Gets the value of the servlet property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the servlet property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getServlet().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Servlet }
     * 
     * 
     */
    public List<Servlet> getServlet() {
        if (servlet == null) {
            servlet = new ArrayList<Servlet>();
        }
        return this.servlet;
    }

    /**
     * Gets the value of the servletMapping property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the servletMapping property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getServletMapping().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ServletMapping }
     * 
     * 
     */
    public List<ServletMapping> getServletMapping() {
        if (servletMapping == null) {
            servletMapping = new ArrayList<ServletMapping>();
        }
        return this.servletMapping;
    }

    /**
     * Gets the value of the sessionConfig property.
     * 
     * @return
     *     possible object is
     *     {@link SessionConfig }
     *     
     */
    public SessionConfig getSessionConfig() {
        return sessionConfig;
    }

    /**
     * Sets the value of the sessionConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link SessionConfig }
     *     
     */
    public void setSessionConfig(SessionConfig value) {
        try {
            support.fireVetoableChange("SessionConfig",sessionConfig, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.sessionConfig = value;
    }

    /**
     * Gets the value of the mimeMapping property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the mimeMapping property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMimeMapping().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link MimeMapping }
     * 
     * 
     */
    public List<MimeMapping> getMimeMapping() {
        if (mimeMapping == null) {
            mimeMapping = new ArrayList<MimeMapping>();
        }
        return this.mimeMapping;
    }

    /**
     * Gets the value of the welcomeFileList property.
     * 
     * @return
     *     possible object is
     *     {@link WelcomeFileList }
     *     
     */
    public WelcomeFileList getWelcomeFileList() {
        return welcomeFileList;
    }

    /**
     * Sets the value of the welcomeFileList property.
     * 
     * @param value
     *     allowed object is
     *     {@link WelcomeFileList }
     *     
     */
    public void setWelcomeFileList(WelcomeFileList value) {
        try {
            support.fireVetoableChange("WelcomeFileList",welcomeFileList, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.welcomeFileList = value;
    }

    /**
     * Gets the value of the errorPage property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the errorPage property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getErrorPage().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ErrorPage }
     * 
     * 
     */
    public List<ErrorPage> getErrorPage() {
        if (errorPage == null) {
            errorPage = new ArrayList<ErrorPage>();
        }
        return this.errorPage;
    }

    /**
     * Gets the value of the taglib property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the taglib property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTaglib().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Taglib }
     * 
     * 
     */
    public List<Taglib> getTaglib() {
        if (taglib == null) {
            taglib = new ArrayList<Taglib>();
        }
        return this.taglib;
    }

    /**
     * Gets the value of the resourceEnvRef property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the resourceEnvRef property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getResourceEnvRef().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ResourceEnvRef }
     * 
     * 
     */
    public List<ResourceEnvRef> getResourceEnvRef() {
        if (resourceEnvRef == null) {
            resourceEnvRef = new ArrayList<ResourceEnvRef>();
        }
        return this.resourceEnvRef;
    }

    /**
     * Gets the value of the resourceRef property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the resourceRef property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getResourceRef().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ResourceRef }
     * 
     * 
     */
    public List<ResourceRef> getResourceRef() {
        if (resourceRef == null) {
            resourceRef = new ArrayList<ResourceRef>();
        }
        return this.resourceRef;
    }

    /**
     * Gets the value of the securityConstraint property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the securityConstraint property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSecurityConstraint().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SecurityConstraint }
     * 
     * 
     */
    public List<SecurityConstraint> getSecurityConstraint() {
        if (securityConstraint == null) {
            securityConstraint = new ArrayList<SecurityConstraint>();
        }
        return this.securityConstraint;
    }

    /**
     * Gets the value of the loginConfig property.
     * 
     * @return
     *     possible object is
     *     {@link LoginConfig }
     *     
     */
    public LoginConfig getLoginConfig() {
        return loginConfig;
    }

    /**
     * Sets the value of the loginConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link LoginConfig }
     *     
     */
    public void setLoginConfig(LoginConfig value) {
        try {
            support.fireVetoableChange("LoginConfig",loginConfig, value);
        } catch (PropertyVetoException _x) {
            return;
        }
        this.loginConfig = value;
    }

    /**
     * Gets the value of the securityRole property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the securityRole property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSecurityRole().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link SecurityRole }
     * 
     * 
     */
    public List<SecurityRole> getSecurityRole() {
        if (securityRole == null) {
            securityRole = new ArrayList<SecurityRole>();
        }
        return this.securityRole;
    }

    /**
     * Gets the value of the envEntry property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the envEntry property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEnvEntry().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EnvEntry }
     * 
     * 
     */
    public List<EnvEntry> getEnvEntry() {
        if (envEntry == null) {
            envEntry = new ArrayList<EnvEntry>();
        }
        return this.envEntry;
    }

    /**
     * Gets the value of the ejbRef property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the ejbRef property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEjbRef().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EjbRef }
     * 
     * 
     */
    public List<EjbRef> getEjbRef() {
        if (ejbRef == null) {
            ejbRef = new ArrayList<EjbRef>();
        }
        return this.ejbRef;
    }

    /**
     * Gets the value of the ejbLocalRef property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the ejbLocalRef property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEjbLocalRef().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EjbLocalRef }
     * 
     * 
     */
    public List<EjbLocalRef> getEjbLocalRef() {
        if (ejbLocalRef == null) {
            ejbLocalRef = new ArrayList<EjbLocalRef>();
        }
        return this.ejbLocalRef;
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
