/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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

package com.sun.grizzly.config;

import com.sun.hk2.component.Holder;
import com.sun.hk2.component.InhabitantsParser;
import com.sun.hk2.component.InhabitantsScanner;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigParser;
import org.jvnet.hk2.config.DomDocument;

import javax.xml.stream.XMLInputFactory;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Created Dec 18, 2008
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
public class Utils {
    private final static String habitatName = "default";
    private final static String inhabitantPath = "META-INF/inhabitants";

    public static Habitat getHabitat(final String fileURL) {
        final Habitat habitat = getNewHabitat();
        final ConfigParser parser = new ConfigParser(habitat);
        URL url = Utils.class.getClassLoader().getResource(fileURL);
        if (url == null) {
            try {
                url = new URL(fileURL);
            } catch (MalformedURLException e) {
                throw new GrizzlyConfigException(e.getMessage());
            }
        }
        if (url != null) {
            try {
                XMLInputFactory xif = XMLInputFactory.class.getClassLoader() == null
                        ? XMLInputFactory.newInstance()
                        : XMLInputFactory.newInstance(XMLInputFactory.class.getName(),
                        XMLInputFactory.class.getClassLoader());
                final DomDocument document = parser.parse(xif.createXMLStreamReader(url.openStream()));

                habitat.addComponent("document", document);
            } catch (Exception e) {
                e.printStackTrace();
                throw new GrizzlyConfigException(e.getMessage(), e);
            }
        }
        return habitat;
    }

    public static Habitat getNewHabitat() {
        final Holder<ClassLoader> holder = new Holder<ClassLoader>() {
            public ClassLoader get() {
                return getClass().getClassLoader();
            }
        };
        final Enumeration<URL> resources;
        try {
            resources = Utils.class.getClassLoader().getResources(inhabitantPath + "/" + habitatName);
        } catch (IOException e) {
            throw new GrizzlyConfigException(e);
        }
        if (resources == null) {
            System.out.println("Cannot find any inhabitant file in the classpath");
            return null;
        }
        final Habitat habitat = new Habitat();
        while (resources.hasMoreElements()) {
            final URL resource = resources.nextElement();
            final InhabitantsScanner scanner;
            try {
                scanner = new InhabitantsScanner(resource.openConnection().getInputStream(), habitatName);
            } catch (IOException e) {
                throw new GrizzlyConfigException(e);
            }
            final InhabitantsParser inhabitantsParser = new InhabitantsParser(habitat);
            try {
                inhabitantsParser.parse(scanner, holder);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return habitat;
    }
}
