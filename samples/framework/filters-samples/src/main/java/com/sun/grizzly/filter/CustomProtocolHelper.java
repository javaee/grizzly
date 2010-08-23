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
 */

package com.sun.grizzly.filter;

import com.sun.grizzly.Controller;
import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.util.WorkerThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.logging.*;

/**
 * Varoius small ByteBuffer helpers for logging and ByteBuffer handling.
 *
 * @author John Vieten 25.06.2008
 * @version 1.0
 */
public class CustomProtocolHelper {

    private static Logger logger = Logger.getLogger("grizzlysamples");

    /**
     * @param neededBytes number of bytes that the given buffer should be able to hold
     * @param buf         the bytebuffer which is queried for free space
     * @return if buf can hold additinasl neededBytes
     */
    public static boolean byteBufferHasEnoughSpace(int neededBytes, ByteBuffer buf) {
        return (buf.capacity() - buf.position()) >= neededBytes;
    }

    /**
     * Gives  current Thread a completely new Bytebuffer of @see Message.MessageMaxLength
     */

    public static ByteBuffer giveGrizzlyNewByteBuffer() {
        ByteBuffer newSpace = ByteBuffer.allocate(Message.MessageMaxLength);
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        workerThread.setByteBuffer(newSpace);
        return newSpace;

    }

    /**
     * Gives  current Thread a completely new Bytebuffer of @see Message.MessageMaxLength
     * with the given byteBuffer copied into it.
     *
     * @param buf the buffer which should be put into the newly created byteBuffer.
     */
    public static void giveGrizzlyNewByteBuffer(ByteBuffer buf) {
        ByteBuffer newSpace = ByteBuffer.allocate(Message.MessageMaxLength);
        newSpace.put(buf);
        WorkerThread workerThread = (WorkerThread) Thread.currentThread();
        workerThread.setByteBuffer(newSpace);

    }

     public static ByteBuffer sliceBuffer(ByteBuffer byteBuffer,int start,int end) {
        int pos = byteBuffer.position();
        int limit = byteBuffer.limit();
        byteBuffer.position(start);
        byteBuffer.limit(end);
        ByteBuffer result = byteBuffer.slice();

        byteBuffer.limit(limit);
        byteBuffer.position(pos);
        return result;
    }


    /**
     * Return the current <code>Logger</code> used Customprotocol.
     */
    public static Logger logger() {
        return logger;
    }

    public static void logFine(String msg, Throwable t) {
      if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.SEVERE, msg, t);
    }
    }

    public static void log(String msg) {
    if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.SEVERE, msg);
      }
    }

    /**
     * Print the contents of the buffer out to the PrintStream in
     * hex and ASCII.
     * 
     * Note this was taken from the java corba sources.
     *
     * @param msg    The message to use as the header for this display
     * @param buffer The ByteBuffer containing the data.  The contents
     *               from 0 to buffer.position() are printed out.  Remember to set
     *               position!
     */
    public static String printBuffer(String msg, ByteBuffer buffer) {
        StringBuffer sbuf = new StringBuffer();
        int length = buffer.position();
        sbuf.append("--------------------------------------------------------\n\n");
        sbuf.append(msg).append("\n");
        sbuf.append("\n");
        sbuf.append("Thread  : ").append(Thread.currentThread().getName()).append("\n");
        sbuf.append("Total length (ByteBuffer position) : ").append(length).append("\n");
        sbuf.append("Byte Buffer capacity               : ").append(buffer.capacity()).append("\n\n");

        try {
            char[] charBuf = new char[16];
            for (int i = 0; i < length; i += 16) {
                int j = 0;

                // For every 16 bytes, there is one line of output.  First,
                // the hex output of the 16 bytes with each byte separated
                // by a space.
                while (j < 16 && (i + j) < length) {
                    int k = buffer.get(i + j);
                    if (k < 0)
                        k = 256 + k;
                    String hex = Integer.toHexString(k);
                    if (hex.length() == 1)
                        hex = "0" + hex;
                    sbuf.append(hex).append(" ");
                    j++;
                }

                // Add any extra spaces to align the
                // text column in case we didn't end
                // at 16
                while (j < 16) {
                    sbuf.append("   ");
                    j++;
                }

                // Now output the ASCII equivalents.  Non-ASCII
                // characters are shown as periods.
                int x = 0;
                while (x < 16 && x + i < length) {
                    if (isPrintable((char) buffer.get(i + x)))
                        charBuf[x] = (char) buffer.get(i + x);
                    else
                        charBuf[x] = '.';
                    x++;
                }
                sbuf.append(new String(charBuf, 0, x)).append("\n");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        sbuf.append("--------------------------------------------------------\n");
        return sbuf.toString();
    }

    public static boolean isPrintable(char c) {
        if (Character.isJavaIdentifierStart(c)) {
            // Letters and $ _
            return true;
        }
        if (Character.isDigit(c)) {
            return true;
        }
        switch (Character.getType(c)) {
            case Character.MODIFIER_SYMBOL:
                return true; // ` ^
            case Character.DASH_PUNCTUATION:
                return true; // -
            case Character.MATH_SYMBOL:
                return true; // = ~ + | < >
            case Character.OTHER_PUNCTUATION:
                return true; // !@#%&*;':",./?
            case Character.START_PUNCTUATION:
                return true; // ( [ {
            case Character.END_PUNCTUATION:
                return true; // ) ] }
        }
        return false;
    }
    public static void startController(final Controller controller) {
        final CountDownLatch latch = new CountDownLatch(1);
        controller.addStateListener(new ControllerStateListenerAdapter() {
            @Override
            public void onReady() {
                latch.countDown();
            }

            @Override
            public void onException(Throwable e) {
                if (latch.getCount() > 0) {
                    Controller.logger().log(Level.SEVERE, "Exception during " +
                            "starting the controller", e);
                    latch.countDown();
                } else {
                    Controller.logger().log(Level.SEVERE, "Exception during " +
                            "controller processing", e);
                }
            }
        });
       
        new Thread(controller).start();

        try {
            latch.await();
        } catch (InterruptedException ex) {
        }
        
        if (!controller.isStarted()) {
            throw new IllegalStateException("Controller is not started!");
        }
    }
   /**
     *  Stop controller in seperate thread
     */
    public static void stopController(Controller controller) {
        try {
            controller.stop();
        } catch(IOException e) {
        }
    }  

}
