/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2015 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */
package com.sun.grizzly.config.dom;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.types.PropertyBag;

import javax.validation.constraints.Pattern;

/**
 * Define SSL processing parameters
 */
@Configured
public interface Ssl extends ConfigBeanProxy, Injectable, PropertyBag {
    /**
     * Nickname of the server certificate in the certificate database or the PKCS#11 token. In the certificate, the name
     * format is tokenname:nickname. Including the tokenname: part of the name in this attribute is optional.
     */
    @Attribute
    String getCertNickname();

    void setCertNickname(String value);

    /**
     * Determines whether SSL3 client authentication is performed on every request, independent of ACL-based access
     * control.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getClientAuthEnabled();

    void setClientAuthEnabled(String value);

    /**
     * Determines if if the engine will request (want) or require (need) client authentication. Valid values:  want,
     * need, or left blank
     */
    @Attribute(dataType = String.class, defaultValue = "")
    @Pattern(regexp = "(|need|want)")
    String getClientAuth();

    void setClientAuth(String value);

    @Attribute
    String getCrlFile();

    void setCrlFile(String crlFile);

    /**
     * type of the keystore file
     */
    @Attribute(dataType = String.class)
    @Pattern(regexp = "(JKS|NSS)")
    String getKeyStoreType();

    void setKeyStoreType(String type);

    /**
     * password of the keystore file
     */
    @Attribute
    String getKeyStorePassword();

    void setKeyStorePassword(String password);

    /**
     * Location of the keystore file
     */
    @Attribute
    String getKeyStore();

    void setKeyStore(String location);

    @Attribute
    String getClassname();

    void setClassname(String value);

    /**
     * A comma-separated list of the SSL2 ciphers used, with the prefix + to enable or - to disable, for example +rc4.
     * Allowed values are rc4, rc4export, rc2, rc2export, idea, des, desede3. If no value is specified, all supported
     * ciphers are assumed to be enabled. NOT Used in PE
     */
    @Attribute
    @Pattern(
        regexp = "((\\+|\\-)(rc2|rc2export|rc4|rc4export|idea|des|desede3)(\\s*,\\s*(\\+|\\-)(rc2|rc2export|rc4|rc4export|idea|des|desede3))*)*")
    String getSsl2Ciphers();

    void setSsl2Ciphers(String value);

    /**
     * Determines whether SSL2 is enabled. NOT Used in PE. SSL2 is not supported by either iiop or web-services. When
     * this element is used as a child of the iiop-listener element then the only allowed value for this attribute is
     * "false".
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getSsl2Enabled();

    void setSsl2Enabled(String value);

    /**
     * Determines whether SSL3 is enabled. If both SSL2 and SSL3 are enabled for a virtual server, the server tries SSL3
     * encryption first. If that fails, the server tries SSL2 encryption.
     */
    @Attribute(defaultValue = "false", dataType = Boolean.class)
    String getSsl3Enabled();

    void setSsl3Enabled(String value);

    /**
     * A comma-separated list of the SSL3 ciphers used, with the prefix + to enable or - to disable, for example
     * +SSL_RSA_WITH_RC4_128_MD5. Allowed SSL3/TLS values are those that are supported by the JVM for the given security
     * provider and security service configuration. If no value is specified, all supported ciphers are assumed to be
     * enabled.
     */
    @Attribute
    String getSsl3TlsCiphers();

    void setSsl3TlsCiphers(String value);

    /**
     * Determines whether TLS is enabled.
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getTlsEnabled();

    void setTlsEnabled(String value);

    /**
     * Determines whether TLS rollback is enabled. TLS rollback should be enabled for Microsoft Internet Explorer 5.0
     * and 5.5. NOT Used in PE
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getTlsRollbackEnabled();

    void setTlsRollbackEnabled(String value);

    @Attribute
    String getTrustAlgorithm();

    void setTrustAlgorithm(String algorithm);

    @Attribute(dataType = Integer.class, defaultValue = "5")
    String getTrustMaxCertLength();

    void setTrustMaxCertLength(String maxLength);

    @Attribute
    String getTrustStore();

    void setTrustStore(String location);

    /**
     * type of the truststore file
     */
    @Attribute(dataType = String.class)
    @Pattern(regexp = "(JKS|NSS)")
    String getTrustStoreType();

    void setTrustStoreType(String type);

    /**
     * password of the truststore file
     */
    @Attribute
    String getTrustStorePassword();

    void setTrustStorePassword(String password);

    /**
     * Does SSL configuration allow implementation to initialize it lazily way
     */
    @Attribute(defaultValue = "true", dataType = Boolean.class)
    String getAllowLazyInit();

    void setAllowLazyInit(String value);
}
