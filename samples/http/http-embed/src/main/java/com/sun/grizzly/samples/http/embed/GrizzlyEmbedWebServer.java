/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.samples.http.embed;

import com.sun.grizzly.http.Management;
import com.sun.grizzly.http.embed.GrizzlyWebServer;

import com.sun.grizzly.http.embed.Statistics;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.management.ObjectName;
import org.apache.commons.modeler.Registry;

/**
 * Simple demo tha use Apache Commons Modeler for enabling JMX.
 * see <a href="">this</a> for a complete explanation of what this simple demo
 * does.
 * 
 * @author Jeanfrancois Arcand
 */
public class GrizzlyEmbedWebServer {


    // Simple scheduler that will outpot stats every 5 seconds.
    private static ScheduledThreadPoolExecutor ste = 
        new ScheduledThreadPoolExecutor(1);

    public static void main( String args[] ) throws Exception { 
        String path = args[0];
        if (args[0] == null || path == null){
            System.out.println("Invalid static resource path");
            System.exit(-1);            
        }

        GrizzlyWebServer ws = new GrizzlyWebServer(path);    
/*        ws.enableJMX(new Management() {

            public void registerComponent(Object bean, ObjectName oname, String type) 
            throws Exception{
            Registry.getRegistry().registerComponent(bean,oname,type);
            }

        public void unregisterComponent(ObjectName oname) throws Exception{
            Registry.getRegistry().
            unregisterComponent(oname);
        }  
        });
        */

        final Statistics stats = ws.getStatistics();
        stats.startGatheringStatistics();

        ste.scheduleAtFixedRate(new Runnable() {
            public void run() {
                System.out.println("Current connected users: " +
                    stats.getKeepAliveStatistics().getCountConnections());
                System.out.println("How many requests since startup:" +
                    stats.getRequestStatistics().getRequestCount());
                System.out.println("How many connection we queued because of all" +
                    "thread were busy: " +
                    stats.getThreadPoolStatistics().getCountQueued()); 
                System.out.println("Max Open Connection: "+
                    stats.getRequestStatistics().getMaxOpenConnections());

                System.out.println("Request Queued (15min avg): "+
                    stats.getThreadPoolStatistics().getCountQueued15MinuteAverage());
                System.out.println("Request Queued (total): "+
                    stats.getThreadPoolStatistics().getCountTotalQueued());
                System.out.println("Byte Sent: "+ stats.getRequestStatistics().getBytesSent());
                System.out.println("200 Count: "+ stats.getRequestStatistics().getCount200());
                System.out.println("404 Count: "+ stats.getRequestStatistics().getCount404()); 
            


                return;
            }
        }, 0, 10,TimeUnit.SECONDS);
        System.out.println("Grizzly WebServer listening on port 8080");
        ws.start();
    }
}
