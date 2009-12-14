
/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 */

package com.sun.grizzly.util;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

/**
 *
 * @author gustav trede
 */
public class DataStructures {

    private final static Class<?> LTQclass, CLQclass;

    static{
        Class<?> LTQ = LinkedBlockingQueue.class ,
                 CLQ = ConcurrentLinkedQueue.class;
        int jver = 0;
        try{
            jver = Integer.valueOf(System.getProperty("java.version").
                    substring(0,3).replace(".", ""));
            if (jver > 16){
                LTQ = getAndVerify("maskedclasses.LinkedTransferQueue");
            }else
            if (jver == 16){
                LTQ = getAndVerify("maskedclasses.LinkedTransferQueue");
                CLQ = getAndVerify("maskedclasses.ConcurrentLinkedQueue");
            }else{
                CLQ = LTQ;
               //LTQ = getAndVerify("com.sun.grizzly.util.LinkedTransferQueue");
            }
        }catch(Throwable t){
            LoggerUtils.getLogger().log(Level.WARNING,
                 "failed loading grizzly version of datastructure classes", t);
        }
        LTQclass =  LTQ;
        CLQclass =  CLQ;
        LoggerUtils.getLogger().fine("JVM version "+jver+" detected," +
               " grizzly loaded datastructure classes: "+LTQclass+" "+CLQclass);
    }

    private final static Class<?> getAndVerify(String cname) throws Throwable{
        Class<?> cl = DataStructures.class.getClassLoader().loadClass(cname);
        return cl.newInstance().getClass();
    }

    public final static BlockingQueue<?>  getLTQinstance(){
        try{
            return  (BlockingQueue<?>) LTQclass.newInstance();
        }catch(Exception ea){
            throw new RuntimeException(ea);
        }
    }

    @SuppressWarnings("unchecked")
    public final static <T>BlockingQueue<T>  getLTQinstance(Class<T> t){
        try{
            return  (BlockingQueue<T>) LTQclass.newInstance();
        }catch(Exception ea){
            throw new RuntimeException(ea);
        }
    }
    
    public final static Queue<?> getCLQinstance(){
        try{
            return (Queue<?>) CLQclass.newInstance();
        }catch(Exception ea){
            throw new RuntimeException(ea);
        }
    }

    @SuppressWarnings("unchecked")
    public final static <T>Queue<T> getCLQinstance(Class<T> t){
        try{
            return (Queue<T>) CLQclass.newInstance();
        }catch(Exception ea){
            throw new RuntimeException(ea);
        }
    }

}
