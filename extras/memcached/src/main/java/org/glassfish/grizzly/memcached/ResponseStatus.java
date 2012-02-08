/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.memcached;

/**
 * Defines response's status of the memcached's binary protocol
 * <p/>
 * See http://code.google.com/p/memcached/wiki/BinaryProtocolRevamped#Response_Status
 *
 * @author Bongjae Chang
 */
public enum ResponseStatus {
    No_Error(0x0000, "No error"),
    Key_Not_Found(0x0001, "Key not found"),
    Key_Exists(0x0002, "Key exists"),
    Value_Too_Large(0x0003, "Value too large"),
    Invalid_Arguments(0x0004, "Invalid arguments"),
    Item_Not_Stored(0x0005, "Item not stored"),
    Incr_Decr_On_NonNumeric_Value(0x0006, "Incr/Decr on non-numeric value"),
    VBucket_Belongs_To_Another_Server(0x0007, "The vbucket belongs to another server"),
    Authentication_Error(0x0008, "Authentication error"),
    Authentication_Continue(0x0009, "Authentication continue"),
    Authentication_Required(0x0020, "Authentication required or not successful"), // ??
    Further_Authentication_Required(0x0021, "Further authentication steps required"), // ??
    Unknown_Command(0x0081, "Unknown command"),
    Out_Of_Memory(0x0082, "Out of memory"),
    Not_Supported(0x0083, "Not supported"),
    Internal_Error(0x0084, "Internal error"),
    Busy(0x0085, "Busy"),
    Temporary_Failure(0x0086, "Temporary failure");

    private final short status;
    private final String message;

    private ResponseStatus(int status, String message) {
        this.status = (short) (status & 0xffff);
        this.message = message;
    }

    public short status() {
        return status;
    }

    public String message() {
        return message;
    }

    public static ResponseStatus getResponseStatus(final short status) {
        switch (status) {
            case 0x0000:
                return No_Error;
            case 0x0001:
                return Key_Not_Found;
            case 0x0002:
                return Key_Exists;
            case 0x0003:
                return Value_Too_Large;
            case 0x0004:
                return Invalid_Arguments;
            case 0x0005:
                return Item_Not_Stored;
            case 0x0006:
                return Incr_Decr_On_NonNumeric_Value;
            case 0x0007:
                return VBucket_Belongs_To_Another_Server;
            case 0x0008:
                return Authentication_Error;
            case 0x0009:
                return Authentication_Continue;
            case 0x0020:
                return Authentication_Required;
            case 0x0021:
                return Further_Authentication_Required;
            case 0x0081:
                return Unknown_Command;
            case 0x0082:
                return Out_Of_Memory;
            case 0x0083:
                return Not_Supported;
            case 0x0084:
                return Internal_Error;
            case 0x0085:
                return Busy;
            case 0x0086:
                return Temporary_Failure;
            default:
                throw new IllegalArgumentException("invalid status");
        }
    }

    public static boolean isError(final ResponseStatus status) {
        return status != ResponseStatus.No_Error;
    }
}
