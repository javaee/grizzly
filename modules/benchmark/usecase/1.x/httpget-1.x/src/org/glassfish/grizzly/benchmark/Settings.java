/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.benchmark;

/**
 *
 * @author oleksiys
 */
public class Settings {

    private String host = "localhost";
    private int port = 9011;

    private int workerThreads = 5;

    private int selectorThreads = Runtime.getRuntime().availableProcessors();

    private boolean useLeaderFollower = false;

    private Settings() {
    }

    public static Settings parse(String[] args) {
        Settings settings = new Settings();
        for(int i=0; i<args.length; i++) {
            String unit = args[i].trim();

            if (!unit.startsWith("-")) continue;

            if (unit.equalsIgnoreCase("-help")) {
                help();
                System.exit(0);
            }

            String[] paramValue = unit.split("=");
            String param = paramValue[0].trim();
            String value = paramValue[1].trim();

            if ("-host".equalsIgnoreCase(param)) {
                settings.host = value;
            } else if ("-port".equalsIgnoreCase(param)) {
                int port = Integer.parseInt(value);
                settings.port = port;
            } else if ("-workerThreads".equalsIgnoreCase(param)) {
                int workerThreads = Integer.parseInt(value);
                settings.workerThreads = workerThreads;
            } else if ("-selectorThreads".equalsIgnoreCase(param)) {
                int selectorThreads = Integer.parseInt(value);
                settings.selectorThreads = selectorThreads;
            } else if ("-useLeaderFollower".equalsIgnoreCase(param)) {
                boolean useLeaderFollower = Boolean.parseBoolean(value);
                settings.useLeaderFollower(useLeaderFollower);
            }
        }

        return settings;
    }

    public static void help() {
        System.out.println("Use EchoServer -host=<HOST> -port=<PORT> -workerThreads=<WORKER_THREADS_NUMBER> -selectorThreads=<SELECTOR_THREADS_NUMBER> -useLeaderFollower=<USE_LEADER_FOLLOWER>");
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getSelectorThreads() {
        return selectorThreads;
    }

    public void setSelectorThreads(int selectorThreads) {
        this.selectorThreads = selectorThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public boolean useLeaderFollower() {
        return useLeaderFollower;
    }

    public void useLeaderFollower(boolean useLeaderFollower) {
        this.useLeaderFollower = useLeaderFollower;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Configuration:");
        sb.append("\nHost: ").append(host);
        sb.append("\nPort: ").append(port);
        sb.append("\nWorker threads: ").append(workerThreads);
        sb.append("\nSelector threads: ").append(selectorThreads);
        sb.append("\nuseLeaderFollower: ").append(useLeaderFollower);

        return sb.toString();
    }


}
