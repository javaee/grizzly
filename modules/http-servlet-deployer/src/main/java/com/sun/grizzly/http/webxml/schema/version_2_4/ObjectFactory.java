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

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlElementDecl;
import javax.xml.bind.annotation.XmlRegistry;
import javax.xml.namespace.QName;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the com.sun.java.xml.ns.j2ee package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _WebApp_QNAME = new QName("http://java.sun.com/xml/ns/j2ee", "web-app");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: com.sun.java.xml.ns.j2ee
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link XsdNonNegativeIntegerType }
     * 
     */
    public XsdNonNegativeIntegerType createXsdNonNegativeIntegerType() {
        return new XsdNonNegativeIntegerType();
    }

    /**
     * Create an instance of {@link EjbRefType }
     * 
     */
    public EjbRefType createEjbRefType() {
        return new EjbRefType();
    }

    /**
     * Create an instance of {@link EjbLocalRefType }
     * 
     */
    public EjbLocalRefType createEjbLocalRefType() {
        return new EjbLocalRefType();
    }

    /**
     * Create an instance of {@link PathType }
     * 
     */
    public PathType createPathType() {
        return new PathType();
    }

    /**
     * Create an instance of {@link ErrorCodeType }
     * 
     */
    public ErrorCodeType createErrorCodeType() {
        return new ErrorCodeType();
    }

    /**
     * Create an instance of {@link TrueFalseType }
     * 
     */
    public TrueFalseType createTrueFalseType() {
        return new TrueFalseType();
    }

    /**
     * Create an instance of {@link SecurityConstraintType }
     * 
     */
    public SecurityConstraintType createSecurityConstraintType() {
        return new SecurityConstraintType();
    }

    /**
     * Create an instance of {@link FormLoginConfigType }
     * 
     */
    public FormLoginConfigType createFormLoginConfigType() {
        return new FormLoginConfigType();
    }

    /**
     * Create an instance of {@link TaglibType }
     * 
     */
    public TaglibType createTaglibType() {
        return new TaglibType();
    }

    /**
     * Create an instance of {@link ListenerType }
     * 
     */
    public ListenerType createListenerType() {
        return new ListenerType();
    }

    /**
     * Create an instance of {@link JspConfigType }
     * 
     */
    public JspConfigType createJspConfigType() {
        return new JspConfigType();
    }

    /**
     * Create an instance of {@link MimeTypeType }
     * 
     */
    public MimeTypeType createMimeTypeType() {
        return new MimeTypeType();
    }

    /**
     * Create an instance of {@link XsdAnyURIType }
     * 
     */
    public XsdAnyURIType createXsdAnyURIType() {
        return new XsdAnyURIType();
    }

    /**
     * Create an instance of {@link FilterMappingType }
     * 
     */
    public FilterMappingType createFilterMappingType() {
        return new FilterMappingType();
    }

    /**
     * Create an instance of {@link EjbLinkType }
     * 
     */
    public EjbLinkType createEjbLinkType() {
        return new EjbLinkType();
    }

    /**
     * Create an instance of {@link LocaleEncodingMappingListType }
     * 
     */
    public LocaleEncodingMappingListType createLocaleEncodingMappingListType() {
        return new LocaleEncodingMappingListType();
    }

    /**
     * Create an instance of {@link DescriptionType }
     * 
     */
    public DescriptionType createDescriptionType() {
        return new DescriptionType();
    }

    /**
     * Create an instance of {@link FullyQualifiedClassType }
     * 
     */
    public FullyQualifiedClassType createFullyQualifiedClassType() {
        return new FullyQualifiedClassType();
    }

    /**
     * Create an instance of {@link NonEmptyStringType }
     * 
     */
    public NonEmptyStringType createNonEmptyStringType() {
        return new NonEmptyStringType();
    }

    /**
     * Create an instance of {@link LocalHomeType }
     * 
     */
    public LocalHomeType createLocalHomeType() {
        return new LocalHomeType();
    }

    /**
     * Create an instance of {@link EnvEntryType }
     * 
     */
    public EnvEntryType createEnvEntryType() {
        return new EnvEntryType();
    }

    /**
     * Create an instance of {@link EmptyType }
     * 
     */
    public EmptyType createEmptyType() {
        return new EmptyType();
    }

    /**
     * Create an instance of {@link JavaTypeType }
     * 
     */
    public JavaTypeType createJavaTypeType() {
        return new JavaTypeType();
    }

    /**
     * Create an instance of {@link AuthMethodType }
     * 
     */
    public AuthMethodType createAuthMethodType() {
        return new AuthMethodType();
    }

    /**
     * Create an instance of {@link XsdStringType }
     * 
     */
    public XsdStringType createXsdStringType() {
        return new XsdStringType();
    }

    /**
     * Create an instance of {@link HomeType }
     * 
     */
    public HomeType createHomeType() {
        return new HomeType();
    }

    /**
     * Create an instance of {@link MessageDestinationRefType }
     * 
     */
    public MessageDestinationRefType createMessageDestinationRefType() {
        return new MessageDestinationRefType();
    }

    /**
     * Create an instance of {@link ServiceRefType }
     * 
     */
    public ServiceRefType createServiceRefType() {
        return new ServiceRefType();
    }

    /**
     * Create an instance of {@link WarPathType }
     * 
     */
    public WarPathType createWarPathType() {
        return new WarPathType();
    }

    /**
     * Create an instance of {@link RemoteType }
     * 
     */
    public RemoteType createRemoteType() {
        return new RemoteType();
    }

    /**
     * Create an instance of {@link UrlPatternType }
     * 
     */
    public UrlPatternType createUrlPatternType() {
        return new UrlPatternType();
    }

    /**
     * Create an instance of {@link EjbRefNameType }
     * 
     */
    public EjbRefNameType createEjbRefNameType() {
        return new EjbRefNameType();
    }

    /**
     * Create an instance of {@link EnvEntryTypeValuesType }
     * 
     */
    public EnvEntryTypeValuesType createEnvEntryTypeValuesType() {
        return new EnvEntryTypeValuesType();
    }

    /**
     * Create an instance of {@link XsdNMTOKENType }
     * 
     */
    public XsdNMTOKENType createXsdNMTOKENType() {
        return new XsdNMTOKENType();
    }

    /**
     * Create an instance of {@link RunAsType }
     * 
     */
    public RunAsType createRunAsType() {
        return new RunAsType();
    }

    /**
     * Create an instance of {@link FilterType }
     * 
     */
    public FilterType createFilterType() {
        return new FilterType();
    }

    /**
     * Create an instance of {@link XsdQNameType }
     * 
     */
    public XsdQNameType createXsdQNameType() {
        return new XsdQNameType();
    }

    /**
     * Create an instance of {@link GenericBooleanType }
     * 
     */
    public GenericBooleanType createGenericBooleanType() {
        return new GenericBooleanType();
    }

    /**
     * Create an instance of {@link DisplayNameType }
     * 
     */
    public DisplayNameType createDisplayNameType() {
        return new DisplayNameType();
    }

    /**
     * Create an instance of {@link SessionConfigType }
     * 
     */
    public SessionConfigType createSessionConfigType() {
        return new SessionConfigType();
    }

    /**
     * Create an instance of {@link JspPropertyGroupType }
     * 
     */
    public JspPropertyGroupType createJspPropertyGroupType() {
        return new JspPropertyGroupType();
    }

    /**
     * Create an instance of {@link ResourceRefType }
     * 
     */
    public ResourceRefType createResourceRefType() {
        return new ResourceRefType();
    }

    /**
     * Create an instance of {@link JndiNameType }
     * 
     */
    public JndiNameType createJndiNameType() {
        return new JndiNameType();
    }

    /**
     * Create an instance of {@link DispatcherType }
     * 
     */
    public DispatcherType createDispatcherType() {
        return new DispatcherType();
    }

    /**
     * Create an instance of {@link MessageDestinationTypeType }
     * 
     */
    public MessageDestinationTypeType createMessageDestinationTypeType() {
        return new MessageDestinationTypeType();
    }

    /**
     * Create an instance of {@link MessageDestinationUsageType }
     * 
     */
    public MessageDestinationUsageType createMessageDestinationUsageType() {
        return new MessageDestinationUsageType();
    }

    /**
     * Create an instance of {@link MessageDestinationLinkType }
     * 
     */
    public MessageDestinationLinkType createMessageDestinationLinkType() {
        return new MessageDestinationLinkType();
    }

    /**
     * Create an instance of {@link UserDataConstraintType }
     * 
     */
    public UserDataConstraintType createUserDataConstraintType() {
        return new UserDataConstraintType();
    }

    /**
     * Create an instance of {@link MessageDestinationType }
     * 
     */
    public MessageDestinationType createMessageDestinationType() {
        return new MessageDestinationType();
    }

    /**
     * Create an instance of {@link String }
     * 
     */
    public String createString() {
        return new String();
    }

    /**
     * Create an instance of {@link WebResourceCollectionType }
     * 
     */
    public WebResourceCollectionType createWebResourceCollectionType() {
        return new WebResourceCollectionType();
    }

    /**
     * Create an instance of {@link FilterNameType }
     * 
     */
    public FilterNameType createFilterNameType() {
        return new FilterNameType();
    }

    /**
     * Create an instance of {@link LoginConfigType }
     * 
     */
    public LoginConfigType createLoginConfigType() {
        return new LoginConfigType();
    }

    /**
     * Create an instance of {@link ResSharingScopeType }
     * 
     */
    public ResSharingScopeType createResSharingScopeType() {
        return new ResSharingScopeType();
    }

    /**
     * Create an instance of {@link ServletType }
     * 
     */
    public ServletType createServletType() {
        return new ServletType();
    }

    /**
     * Create an instance of {@link JavaIdentifierType }
     * 
     */
    public JavaIdentifierType createJavaIdentifierType() {
        return new JavaIdentifierType();
    }

    /**
     * Create an instance of {@link IconType }
     * 
     */
    public IconType createIconType() {
        return new IconType();
    }

    /**
     * Create an instance of {@link PortComponentRefType }
     * 
     */
    public PortComponentRefType createPortComponentRefType() {
        return new PortComponentRefType();
    }

    /**
     * Create an instance of {@link JspFileType }
     * 
     */
    public JspFileType createJspFileType() {
        return new JspFileType();
    }

    /**
     * Create an instance of {@link ParamValueType }
     * 
     */
    public ParamValueType createParamValueType() {
        return new ParamValueType();
    }

    /**
     * Create an instance of {@link AuthConstraintType }
     * 
     */
    public AuthConstraintType createAuthConstraintType() {
        return new AuthConstraintType();
    }

    /**
     * Create an instance of {@link TransportGuaranteeType }
     * 
     */
    public TransportGuaranteeType createTransportGuaranteeType() {
        return new TransportGuaranteeType();
    }

    /**
     * Create an instance of {@link MimeMappingType }
     * 
     */
    public MimeMappingType createMimeMappingType() {
        return new MimeMappingType();
    }

    /**
     * Create an instance of {@link ServletNameType }
     * 
     */
    public ServletNameType createServletNameType() {
        return new ServletNameType();
    }

    /**
     * Create an instance of {@link XsdIntegerType }
     * 
     */
    public XsdIntegerType createXsdIntegerType() {
        return new XsdIntegerType();
    }

    /**
     * Create an instance of {@link WelcomeFileListType }
     * 
     */
    public WelcomeFileListType createWelcomeFileListType() {
        return new WelcomeFileListType();
    }

    /**
     * Create an instance of {@link XsdBooleanType }
     * 
     */
    public XsdBooleanType createXsdBooleanType() {
        return new XsdBooleanType();
    }

    /**
     * Create an instance of {@link WebAppType }
     * 
     */
    public WebAppType createWebAppType() {
        return new WebAppType();
    }

    /**
     * Create an instance of {@link EjbRefTypeType }
     * 
     */
    public EjbRefTypeType createEjbRefTypeType() {
        return new EjbRefTypeType();
    }

    /**
     * Create an instance of {@link XsdPositiveIntegerType }
     * 
     */
    public XsdPositiveIntegerType createXsdPositiveIntegerType() {
        return new XsdPositiveIntegerType();
    }

    /**
     * Create an instance of {@link ServletMappingType }
     * 
     */
    public ServletMappingType createServletMappingType() {
        return new ServletMappingType();
    }

    /**
     * Create an instance of {@link RoleNameType }
     * 
     */
    public RoleNameType createRoleNameType() {
        return new RoleNameType();
    }

    /**
     * Create an instance of {@link ResourceEnvRefType }
     * 
     */
    public ResourceEnvRefType createResourceEnvRefType() {
        return new ResourceEnvRefType();
    }

    /**
     * Create an instance of {@link SecurityRoleRefType }
     * 
     */
    public SecurityRoleRefType createSecurityRoleRefType() {
        return new SecurityRoleRefType();
    }

    /**
     * Create an instance of {@link SecurityRoleType }
     * 
     */
    public SecurityRoleType createSecurityRoleType() {
        return new SecurityRoleType();
    }

    /**
     * Create an instance of {@link HttpMethodType }
     * 
     */
    public HttpMethodType createHttpMethodType() {
        return new HttpMethodType();
    }

    /**
     * Create an instance of {@link ResAuthType }
     * 
     */
    public ResAuthType createResAuthType() {
        return new ResAuthType();
    }

    /**
     * Create an instance of {@link LocaleEncodingMappingType }
     * 
     */
    public LocaleEncodingMappingType createLocaleEncodingMappingType() {
        return new LocaleEncodingMappingType();
    }

    /**
     * Create an instance of {@link ErrorPageType }
     * 
     */
    public ErrorPageType createErrorPageType() {
        return new ErrorPageType();
    }

    /**
     * Create an instance of {@link ServiceRefHandlerType }
     * 
     */
    public ServiceRefHandlerType createServiceRefHandlerType() {
        return new ServiceRefHandlerType();
    }

    /**
     * Create an instance of {@link LocalType }
     * 
     */
    public LocalType createLocalType() {
        return new LocalType();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link WebAppType }{@code >}}
     * 
     */
    @XmlElementDecl(namespace = "http://java.sun.com/xml/ns/j2ee", name = "web-app")
    public JAXBElement<WebAppType> createWebApp(WebAppType value) {
        return new JAXBElement<WebAppType>(_WebApp_QNAME, WebAppType.class, null, value);
    }

}
