/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.thrift;

// Generated code
import tutorial.*;
import shared.*;

import java.util.HashMap;

/**
 * This class is for testing basic thrift.
 *
 * CalculatorHandler implements thrift's services and thrift tutorial includes this file.
 * This class is based on shared.thrift and tutorial.thrift files in thrift tutorial.
 * See thrift_dir/tutorial/java/src/CalculatorHandler.java, thrift_dir/tutorial/shared.thrift and thrift_dir/tutorial/tutorial.thrift files.
 */
public class CalculatorHandler implements Calculator.Iface {

  private HashMap<Integer,SharedStruct> log;

  public CalculatorHandler() {
    log = new HashMap<Integer, SharedStruct>();
  }

  public void ping() {
    System.out.println("ping()");
  }

  public int add(int n1, int n2) {
    System.out.println("add(" + n1 + "," + n2 + ")");
    return n1 + n2;
  }

  public int calculate(int logid, Work work) throws InvalidOperation {
    System.out.println("calculate(" + logid + ", {" + work.op + "," + work.num1 + "," + work.num2 + "})");
    int val;
    switch (work.op) {
    case ADD:
      val = work.num1 + work.num2;
      break;
    case SUBTRACT:
      val = work.num1 - work.num2;
      break;
    case MULTIPLY:
      val = work.num1 * work.num2;
      break;
    case DIVIDE:
      if (work.num2 == 0) {
        InvalidOperation io = new InvalidOperation();
        io.what = work.op.getValue();
        io.why = "Cannot divide by 0";
        throw io;
      }
      val = work.num1 / work.num2;
      break;
    default:
      InvalidOperation io = new InvalidOperation();
      io.what = work.op.getValue();
      io.why = "Unknown operation";
      throw io;
    }

    SharedStruct entry = new SharedStruct();
    entry.key = logid;
    entry.value = Integer.toString(val);
    log.put(logid, entry);

    return val;
  }

  public SharedStruct getStruct(int key) {
    System.out.println("getStruct(" + key + ")");
    return log.get(key);
  }

  public void zip() {
    System.out.println("zip()");
  }

}

