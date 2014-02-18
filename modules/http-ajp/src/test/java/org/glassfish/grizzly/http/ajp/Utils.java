/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.ajp;

import java.io.*;
import java.net.URL;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

/**
 * Utility class to parse AJP responses.
 * 
 * @author Alexey Stashok
 */
public class Utils {
    public static byte[] loadResourceFile(final String filename) throws Exception {
        final ClassLoader cl = Utils.class.getClassLoader();
        final URL url = cl.getResource(filename);
        
        if (url == null) {
            throw new IllegalStateException("File not found: " + filename);
        }
        
        final File file = new File(url.toURI());
        
        if (!file.exists()) {
            throw new IllegalStateException("File not found: " + filename);
        }
        
        final byte[] data = new byte[(int) file.length()];
        
        final DataInputStream dis = new DataInputStream(new FileInputStream(file));
        try {
            dis.readFully(data);
        } finally {
            dis.close();
        }
        
        return data;
    }
    
    public static byte getMethodCode(String method) {
        for (int i = 0; i < AjpConstants.methodTransArray.length; i++) {
            if (AjpConstants.methodTransArray[i].equalsIgnoreCase(method)) {
                return (byte) (i + 1);
            }
        }

        return -1;
    }

    public static byte getHeaderCode(String header) {
        for (int i = 0; i < AjpConstants.headerTransArray.length; i++) {
            if (AjpConstants.headerTransArray[i].equalsIgnoreCase(header)) {
                return (byte) (i + 1);
            }
        }

        return -1;
    }
    
    public static AjpResponse parseResponse(byte[] byteArray) {
        final Buffer buffer = Buffers.wrap(
                MemoryManager.DEFAULT_MEMORY_MANAGER, byteArray);
        
        int pos = 0;
        
        final AjpResponse ajpResponse = new AjpResponse();

        final int magic = AjpMessageUtils.readShort(buffer, pos);
        if (magic != 0x4142) {
            throw new RuntimeException("Invalid magic number: " + magic +
                    " buffer: \n" + dumpByteTable(buffer));
        }
        pos += 2;

        final int packetSize = AjpMessageUtils.readShort(buffer, pos);
        if (packetSize > AjpConstants.MAX_PACKET_SIZE - 2) {
            throw new RuntimeException("The packet size is too large: " + packetSize);
        }
        pos += 2;

        ajpResponse.setPacketLength(packetSize);
        
        int start = pos;
        final byte type = buffer.get(pos++);
        ajpResponse.setType(type);
        byte[] body;
        switch (type) {
            case AjpConstants.JK_AJP13_SEND_HEADERS:
            {
                ajpResponse.setResponseCode(AjpMessageUtils.readShort(buffer, pos));
                pos += 2;

                final int size = AjpMessageUtils.readShort(buffer, pos);
                pos += 2;

                final int oldPos = buffer.position();
                buffer.position(pos);
                body = new byte[size];
                buffer.get(body, 0, size);
                buffer.position(oldPos);
                pos += size;

                ajpResponse.setResponseMessage(new String(body));
                pos++;  // consume terminating 0x00


                AjpHttpRequest request = AjpHttpRequest.create();
                pos = AjpMessageUtils.decodeHeaders(buffer, pos, request);
                ajpResponse.setHeaders(request.getHeaders());
                break;
            }
            case AjpConstants.JK_AJP13_SEND_BODY_CHUNK:
            {
                final int size = AjpMessageUtils.readShort(buffer, pos);
                pos += 2;

                body = new byte[size];
                System.arraycopy(buffer, pos, body, 0, size);
                pos += size;
                
                pos ++;

                ajpResponse.setBody(body);
                break;
            }
            case AjpConstants.JK_AJP13_END_RESPONSE:
            {
                body = new byte[1];
                body[0] = buffer.get(pos++);
                ajpResponse.setBody(body);
                break;
            }
            case AjpConstants.JK_AJP13_GET_BODY_CHUNK:
            {
                body = new byte[2];
                body[0] = buffer.get(pos++);
                body[1] = buffer.get(pos++);
                ajpResponse.setBody(body);
                break;
            }
            default:
                throw new IllegalStateException("Unknown packet type: " + type + " content:\n" + dumpByteTable(buffer));
        }
        final int end = pos;
        if (end - start != packetSize) {
            throw new RuntimeException(String.format("packet type %s size mismatch: %s vs %s", type, end - start, packetSize));
        }
            
        return ajpResponse;
    }
    
    public static String dumpByteTable(Buffer buffer) {
        StringBuilder bytes = new StringBuilder();
        StringBuilder chars = new StringBuilder();
        StringBuilder table = new StringBuilder();
        int count = 0;
        for (int i = buffer.position(); i < buffer.limit(); i++) {
            count++;
            byte cur = buffer.get(i);
            bytes.append(String.format("%02x ", cur));
            chars.append(printable(cur));
            if (count % 16 == 0) {
                table.append(String.format("%s   %s", bytes, chars).trim());
                table.append("\n");
                chars = new StringBuilder();
                bytes = new StringBuilder();
            } else if (count % 8 == 0) {
                table.append(String.format("%s   ", bytes));
                bytes = new StringBuilder();
            }
        }
        if (bytes.length() > 0) {
            final int i = 50 - count % 8;
            final String format = "%-" + i + "s   %s";
            System.out.println("format = " + format);
            table.append(String.format(format, bytes, chars).trim());
        }

        return table.toString();
    }

    private static char printable(byte cur) {
        if ((cur & (byte) 0xa0) == 0xa0) {
            return '?';
        }
        return cur < 127 && cur > 31 || Character.isLetterOrDigit(cur) ? (char) cur : '.';
    }        
    
    static void readFully(final InputStream stream,
            final byte[] buffer, final int offset, final int length) throws IOException {
        int read = 0;
        while (read < length) {
            final int justRead = stream.read(buffer, offset + read, length - read);
            if (justRead == -1) {
                throw new EOFException();
            }
            
            read += justRead;
        }
    }
    
    static int getShort(byte[] b, int off) {
	return ((short) (((b[off + 1] & 0xFF)) + 
			((b[off + 0]) << 8))) & 0xFFFF;
    }    
}
