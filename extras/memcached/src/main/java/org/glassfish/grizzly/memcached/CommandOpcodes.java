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
 * Defines opcodes of the memcached's binary protocol
 * <p/>
 * See http://code.google.com/p/memcached/wiki/BinaryProtocolRevamped#Command_Opcodes
 *
 * @author Bongjae Chang
 */
public enum CommandOpcodes {
    Get(0x00),
    Gets(0x00),
    Set(0x01),
    Add(0x02),
    Replace(0x03),
    Delete(0x04),
    Increment(0x05),
    Decrement(0x06),
    Quit(0x07),
    Flush(0x08),
    GetQ(0x09),
    GetsQ(0x09),
    Noop(0x0a),
    Version(0x0b),
    GetK(0x0c),
    GetKQ(0x0d),
    Append(0x0e),
    Prepend(0x0f),
    Stat(0x10),
    SetQ(0x11),
    AddQ(0x12),
    ReplaceQ(0x13),
    DeleteQ(0x14),
    IncrementQ(0x15),
    DecrementQ(0x16),
    QuitQ(0x17),
    FlushQ(0x18),
    AppendQ(0x19),
    PrependQ(0x1a),
    Verbosity(0x1b),
    Touch(0x1c),
    GAT(0x1d),
    GATQ(0x1e),
    SASL_List(0x20),
    SASL_Auth(0x21),
    SASL_Step(0x22),
    RGet(0x30),
    RSet(0x31),
    RSetQ(0x32),
    RAppend(0x33),
    RAppendQ(0x34),
    RPrepend(0x35),
    RPrependQ(0x36),
    RDelete(0x37),
    RDeleteQ(0x38),
    RIncr(0x39),
    RIncrQ(0x3a),
    RDecr(0x3b),
    RDecrQ(0x3c),
    Set_VBucket(0x3d),
    Get_VBucket(0x3e),
    Del_VBucket(0x3f),
    TAP_Connect(0x40),
    TAP_Mutation(0x41),
    TAP_Delete(0x42),
    TAP_Flush(0x43),
    TAP_Opaque(0x44),
    TAP_VBucket_Set(0x45),
    TAP_Checkpoint_Start(0x46),
    TAP_Checkpoint_End(0x47);

    private final byte opcode;

    private CommandOpcodes(int opcode) {
        this.opcode = (byte) (opcode & 0xff);
    }

    public byte opcode() {
        return opcode;
    }
}
