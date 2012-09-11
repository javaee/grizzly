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
package org.glassfish.grizzly.samples.udpmulticast;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.nio.transport.UDPNIOConnection;
import org.glassfish.grizzly.utils.Futures;

/**
 * Class represent chat command abstraction.
 * 
 * @author Alexey Stashok
 */
public abstract class ChatCommand {
    
    /**
     * Method parses chat command from the input String.
     */
    public static ChatCommand parse(final String command) throws Exception {
        if (command == null) {
            return null;
        }
        
        // Split input string, using space (0x20) as a delimiter
        String[] splitString = command.trim().split(" ");
        
        if (splitString.length == 0) {
            return null;
        }
        
        final int id;
        try {
            // parse the command id
            id = Integer.valueOf(splitString[0]);
        } catch (NumberFormatException e) {
            System.out.println("Bad command: can't parse command id");
            return null;
        }
        
        switch (id) {
            case 1:
                return new SendMsgCommand(splitString);
            case 2:
                return new JoinGroupCommand(splitString);
            case 3:
                return new LeaveGroupCommand(splitString);
            case 4:
                return new BlockSourceCommand(splitString);
            case 5:
                return new UnblockSourceCommand(splitString);
            case 6:
                return new ListNetworkInterfacesCommand(splitString);
            case 7:
                return new ExitCommand(splitString);
        }
        
        return null;
    }

    public boolean isExit() {
        return false;
    }
    
    public abstract void run(UDPNIOConnection connection) throws Exception;
    
    /**
     * Send message command
     */
    private static class SendMsgCommand extends ChatCommand {
        /**
         * multicast group address
         */
        private final InetAddress groupAddr;
        /**
         * Message to send
         */
        private final String msg;
        
        public SendMsgCommand(String[] params) throws Exception {
            if (params.length < 3) {
                throw new IllegalArgumentException("Send message command expects 2 parameters: group_addr message");
            }
            
            // get the multicast group address
            groupAddr = InetAddress.getByName(params[1]);
            
            // from the rest split parameters build a message to send
            final StringBuilder messageBuilder = new StringBuilder();
            
            for (int i = 2; i < params.length; i++) {
                if (messageBuilder.length() > 0) {
                    messageBuilder.append(' ');
                }
                
                messageBuilder.append(params[i].trim());
            }
            
            msg = messageBuilder.toString();
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
            // construct destination multicast address to send the message to
            final InetSocketAddress peerAddr = new InetSocketAddress(groupAddr,
                    ((InetSocketAddress) connection.getLocalAddress()).getPort());
            
            // Create Future to be able to block until the message is sent
            final FutureImpl<WriteResult<String, SocketAddress>> writeFuture =
                    Futures.<WriteResult<String, SocketAddress>>createSafeFuture();
            
            // Send the message
            connection.write(peerAddr, msg,
                    Futures.toCompletionHandler(writeFuture));
            
            // Block until the message is sent
            writeFuture.get(10, TimeUnit.SECONDS);
        }
    }
    
    /**
     * Join multicast group
     */
    private static class JoinGroupCommand extends ChatCommand {
        /**
         * multicast group address
         */
        private final InetAddress groupAddr;
        /**
         * Network Interface (like eth0, wlan0) to join the multicast group on
         */
        private final NetworkInterface ni;
        /**
         * Source address (optional parameter) to listen multicast messages from.
         */
        private final InetAddress source;
        
        public JoinGroupCommand(String[] params) throws Exception {
            if (params.length != 3 && params.length != 4) {
                throw new IllegalArgumentException("Join group command expects 3 parameters (1 optional): group_addr network_interface [source]");
            }
            
            // get the multicast group address
            groupAddr = InetAddress.getByName(params[1]);
            
            // parse Network Interface by name or inet-address
            try {
                ni = NetworkInterface.getByName(params[2]) != null ?
                        NetworkInterface.getByName(params[2]) :
                        NetworkInterface.getByInetAddress(InetAddress.getByName(params[2]));
            } catch (Exception e) {
                throw new IllegalArgumentException("Passed network interface can't be resolved");
            }
            
            // parse the source address to listen multicast messages from (optional)
            source = params.length == 4 ? InetAddress.getByName(params[3]) : null;
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
            // Join the multicast group
            connection.join(groupAddr, ni, source);
            
            // construct destination multicast address to send the message to
            final InetSocketAddress peerAddr =
                    new InetSocketAddress(groupAddr,
                    ((InetSocketAddress) connection.getLocalAddress()).getPort());
            
            // Create Future to be able to block until the message is sent
            final FutureImpl<WriteResult<String, SocketAddress>> writeFuture =
                    Futures.<WriteResult<String, SocketAddress>>createSafeFuture();
            
            // Send the greeting message to group
            connection.write(peerAddr, "joined the group " + groupAddr,
                    Futures.toCompletionHandler(writeFuture));
            
            // Block until the message is sent
            writeFuture.get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Drop multicast group membership
     */
    private static class LeaveGroupCommand extends ChatCommand {
        /**
         * multicast group address
         */
        private final InetAddress groupAddr;
        /**
         * Network Interface (like eth0, wlan0) to join the multicast group on
         */
        private final NetworkInterface ni;
        private final InetAddress source;
        
        public LeaveGroupCommand(String[] params) throws Exception {
            if (params.length != 3 && params.length != 4) {
                throw new IllegalArgumentException("Leave group command expects 3 parameters (1 optional): group_addr network_interface [source]");
            }
            
            // get the multicast group address
            groupAddr = InetAddress.getByName(params[1]);
            
            // parse Network Interface by name or inet-address
            try {
                ni = NetworkInterface.getByName(params[2]) != null ?
                        NetworkInterface.getByName(params[2]) :
                        NetworkInterface.getByInetAddress(InetAddress.getByName(params[2]));
            } catch (Exception e) {
                throw new IllegalArgumentException("Passed network interface can't be resolved");
            }
            
            // parse the source address to listen multicast messages from (optional)
            source = params.length == 4 ? InetAddress.getByName(params[3]) : null;
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
            // Drop the multicast group membership
            connection.drop(groupAddr, ni, source);

            try {
                // construct destination multicast address to send the message to
                final InetSocketAddress peerAddr =
                        new InetSocketAddress(groupAddr,
                        ((InetSocketAddress) connection.getLocalAddress()).getPort());

                // Create Future to be able to block until the message is sent
                final FutureImpl<WriteResult<String, SocketAddress>> writeFuture =
                        Futures.<WriteResult<String, SocketAddress>>createSafeFuture();

                // Send the leave message to the group
                connection.write(peerAddr, "left the group " + groupAddr,
                        Futures.toCompletionHandler(writeFuture));

                // Block until the message is sent
                writeFuture.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
            }
        }
    }

    /**
     * Block specific peer in the multicast group
     */
    private static class BlockSourceCommand extends ChatCommand {
        /**
         * multicast group address
         */
        private final InetAddress groupAddr;
        /**
         * Network Interface (like eth0, wlan0) to join the multicast group on
         */
        private final NetworkInterface ni;
        /**
         * peer address we want to block
         */
        private final InetAddress source;
        
        public BlockSourceCommand(String[] params) throws Exception {
            if (params.length != 4) {
                throw new IllegalArgumentException("Block source command expects 3 parameters: group_addr network_interface source");
            }
            
            // get the multicast group address
            groupAddr = InetAddress.getByName(params[1]);
            
            // parse Network Interface by name or inet-address
            try {
                ni = NetworkInterface.getByName(params[2]) != null ?
                        NetworkInterface.getByName(params[2]) :
                        NetworkInterface.getByInetAddress(InetAddress.getByName(params[2]));
            } catch (Exception e) {
                throw new IllegalArgumentException("Passed network interface can't be resolved");
            }
            
            // the peer address we want to block
            source = InetAddress.getByName(params[3]);
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
            // block the peer address (stop receiving multicast messages from the peer)
            connection.block(groupAddr, ni, source);
        }
    }

    /**
     * Unblock specific peer in the multicast group
     */
    private static class UnblockSourceCommand extends ChatCommand {
        /**
         * multicast group address
         */
        private final InetAddress groupAddr;
        /**
         * Network Interface (like eth0, wlan0) to join the multicast group on
         */
        private final NetworkInterface ni;
        /**
         * peer address we want to unblock
         */
        private final InetAddress source;
        
        public UnblockSourceCommand(String[] params) throws Exception {
            if (params.length != 4) {
                throw new IllegalArgumentException("Unblock source command expects 3 parameters: group_addr network_interface source");
            }
            
            // get the multicast group address
            groupAddr = InetAddress.getByName(params[1]);
            
            // parse Network Interface by name or inet-address
            try {
                ni = NetworkInterface.getByName(params[2]) != null ?
                        NetworkInterface.getByName(params[2]) :
                        NetworkInterface.getByInetAddress(InetAddress.getByName(params[2]));
            } catch (Exception e) {
                throw new IllegalArgumentException("Passed network interface can't be resolved");
            }
            
            // the peer address we want to unblock
            source = InetAddress.getByName(params[3]);
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
            // unblock the peer address (allow receiving multicast messages from the peer)
            connection.unblock(groupAddr, ni, source);
        }
    }

    /**
     * List available network interfaces
     */
    private static class ListNetworkInterfacesCommand extends ChatCommand {
        public ListNetworkInterfacesCommand(String[] params) throws Exception {
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
            final Enumeration<NetworkInterface> niEnumeration = 
                    NetworkInterface.getNetworkInterfaces();
            
            while (niEnumeration.hasMoreElements()) {
                final NetworkInterface ni = niEnumeration.nextElement();
                System.out.println(ni.getName() + " support-multicast=" +
                        ni.supportsMulticast());
            }
        }
    }

    /**
     * Exit chat app
     */
    private static class ExitCommand extends ChatCommand {
        public ExitCommand(String[] params) throws Exception {
        }

        @Override
        public void run(final UDPNIOConnection connection) throws Exception {
        }

        @Override
        public boolean isExit() {
            return true;
        }
    }
}
