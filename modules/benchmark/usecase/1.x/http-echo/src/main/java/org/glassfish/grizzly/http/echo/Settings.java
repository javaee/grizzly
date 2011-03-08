/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.echo;

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

    private boolean binary = true;

    private boolean chunked = true;

    private Settings() {
    }

    public static Settings parse(String[] args) {
        Settings settings = new Settings();
        for (String arg : args) {
            String unit = arg.trim();

            if (!unit.startsWith("-")) continue;

            if (unit.equalsIgnoreCase("-help")) {
                help();
                System.exit(0);
            }

            String[] paramValue = unit.split("=");
            String param = paramValue[0].trim();
            String value = paramValue[1].trim();

            if ("-host".equalsIgnoreCase(param)) {
                settings.setHost(value);
            } else if ("-port".equalsIgnoreCase(param)) {
                settings.setPort(Integer.parseInt(value));
            } else if ("-workerThreads".equalsIgnoreCase(param)) {
                settings.setWorkerThreads(Integer.parseInt(value));
            } else if ("-selectorThreads".equalsIgnoreCase(param)) {
                settings.setSelectorThreads(Integer.parseInt(value));
            } else if ("-useLeaderFollower".equalsIgnoreCase(param)) {
                settings.setUseLeaderFollower(Boolean.valueOf(value));
            } else if ("-binary".equalsIgnoreCase(param)) {
                settings.setBinary(Boolean.valueOf(value));
            } else if ("-chunked".equalsIgnoreCase(param)) {
                settings.setChunked(Boolean.valueOf(value));
            }
        }

        return settings;
    }

    public static void help() {
        System.out.println("Use EchoServer -host=<HOST> -port=<PORT> -binary=<true|false> -workerThreads=<WORKER_THREADS_NUMBER> -selectorThreads=<SELECTOR_THREADS_NUMBER> -useLeaderFollower=<USE_LEADER_FOLLOWER>");
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

    public boolean isUseLeaderFollower() {
        return useLeaderFollower;
    }

    public void setUseLeaderFollower(boolean useLeaderFollower) {
        this.useLeaderFollower = useLeaderFollower;
    }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }

    public boolean isChunked() {
        return chunked;
    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nConfiguration:");
        sb.append("\n-----------------------------------");
        sb.append("\nHost: ").append(getHost());
        sb.append("\nPort: ").append(getPort());
        sb.append("\nWorker threads: ").append(getWorkerThreads());
        sb.append("\nSelector threads: ").append(getSelectorThreads());
        sb.append("\nuseLeaderFollower: ").append(isUseLeaderFollower());
        sb.append("\nchunked: ").append(isChunked());
        sb.append("\nStream Type: ").append(((isBinary()) ? "binary" : "character"));

        return sb.toString();
    }


}
