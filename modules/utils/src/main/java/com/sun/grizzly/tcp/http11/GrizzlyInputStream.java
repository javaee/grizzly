/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.grizzly.tcp.http11;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;

/**
 * This class handles reading bytes.
 * 
 * @author Remy Maucherat
 * @author Jean-Francois Arcand
 */
public class GrizzlyInputStream extends InputStream {


    // ----------------------------------------------------- Instance Variables


    protected GrizzlyInputBuffer ib;


    // ----------------------------------------------------------- Constructors


    public GrizzlyInputStream(GrizzlyInputBuffer ib) {
        this.ib = ib;
    }
    
    
    // --------------------------------------------------------- Public Methods


    /**
    * Prevent cloning the facade.
    */
    @Override
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }
    
    
    // -------------------------------------------------------- Package Methods


    /**
     * Clear facade.
     */
    void clear() {
        ib = null;
    }


    // --------------------------------------------- ServletInputStream Methods


    public int read()
        throws IOException {      
            
        if (System.getSecurityManager() != null){
            
            try{
                @SuppressWarnings("unchecked")
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = new Integer(ib.readByte());
                                return integer;
                            }

                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
            return ib.readByte();
        }   
    }

    @Override
    public int available() throws IOException {
        if (System.getSecurityManager() != null){
            try{
                @SuppressWarnings("unchecked")
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = new Integer(ib.available());
                                return integer;
                            }

                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
           return ib.available();
        }           
    }

    @Override
    public int read(final byte[] b) throws IOException {
        if (System.getSecurityManager() != null){
            try{
                @SuppressWarnings("unchecked")
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = 
                                    new Integer(ib.read(b, 0, b.length));
                                return integer;
                            }

                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
            return ib.read(b, 0, b.length);
        }          
    }


    @Override
    public int read(final byte[] b, final int off, final int len)
        throws IOException {
        
        if (System.getSecurityManager() != null){
            try{
                @SuppressWarnings("unchecked")
                Integer result = 
                    (Integer)AccessController.doPrivileged(
                        new PrivilegedExceptionAction(){

                            public Object run() throws IOException{
                                Integer integer = 
                                    new Integer(ib.read(b, off, len));
                                return integer;
                            }

                });
                return result.intValue();
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
            return ib.read(b, off, len);
        }        
    }

    
    /** 
     * Close the stream
     * Since we re-cycle, we can't allow the call to super.close()
     * which would permantely disable us.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void close() throws IOException {
        if (System.getSecurityManager() != null){
            try{
                AccessController.doPrivileged(
                    new PrivilegedExceptionAction(){

                        public Object run() throws IOException{
                            ib.close();
                            return null;
                        }

                });
            } catch(PrivilegedActionException pae){
                Exception e = pae.getException();
                if (e instanceof IOException){
                    throw (IOException)e;
                } else {
                    throw new RuntimeException(e.getMessage());
                }
            }
        } else {
             ib.close();
        }            
    }

}
