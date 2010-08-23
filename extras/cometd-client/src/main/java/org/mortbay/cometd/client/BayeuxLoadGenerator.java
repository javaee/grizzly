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

package org.mortbay.cometd.client;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.mortbay.cometd.AbstractBayeux;
import org.mortbay.jetty.client.HttpClient;
import org.mortbay.thread.QueuedThreadPool;
import org.mortbay.util.ajax.JSON;

import dojox.cometd.Bayeux;
import dojox.cometd.Client;
import dojox.cometd.Message;
import dojox.cometd.MessageListener;

public class BayeuxLoadGenerator
{
    SecureRandom _random= new SecureRandom();
    HttpClient http;
    InetSocketAddress address;
    ArrayList<BayeuxClient> clients=new ArrayList<BayeuxClient>();
    long _minLatency;
    long _maxLatency;
    long _totalLatency;
    AtomicInteger _got = new AtomicInteger();
    AtomicInteger _subscribed = new AtomicInteger();
    
    public BayeuxLoadGenerator() throws Exception
    {
        http=new HttpClient();
        
        http.setConnectorType(HttpClient.CONNECTOR_SELECT_CHANNEL);
        // http.setConnectorType(HttpClient.CONNECTOR_SOCKET);
        http.setMaxConnectionsPerAddress(20000);
        
        QueuedThreadPool pool = new QueuedThreadPool();
        pool.setMaxThreads(500);
        pool.setDaemon(true);
        http.setThreadPool(pool);
        http.start();
       
    }
    
    
    public void generateLoad() throws Exception
    {
        LineNumberReader in = new LineNumberReader(new InputStreamReader(System.in));

        System.err.print("server[localhost]: ");
        String t = in.readLine().trim();
        if (t.length()==0)
            t="localhost";
        String host=t;
        
        System.err.print("port[8080]: ");
        t = in.readLine().trim();
        if (t.length()==0)
            t="8080";
        int port = Integer.parseInt(t);

        System.err.print("context[/cometd]: ");
        t = in.readLine().trim();
        if (t.length()==0)
            t="/cometd";
        String uri=t+"/cometd";
        
        initSocketAddress(host, port);
        
        int nclients=100;
        int size=50;
        int rooms=100;
        int rooms_per_client=1;
        int publish=1000;
        int pause=100;
        int burst=10;
        int maxLatency=5000;

        System.err.print("base[/chat/demo]: ");
        t = in.readLine().trim();
        if (t.length()==0)
            t="/chat/demo";
        String base = t;

        System.err.print("rooms ["+rooms+"]: ");
        t = in.readLine().trim();
        if (t.length()==0)
            t=""+rooms;
        rooms=Integer.parseInt(t);
        
        System.err.print("rooms per client ["+rooms_per_client+"]: ");
        t = in.readLine().trim();
        if (t.length()==0)
            t=""+rooms_per_client;
        rooms_per_client=Integer.parseInt(t);
        
        
        System.err.print("max Latency ["+maxLatency+"]: ");
        t = in.readLine().trim();
        if (t.length()==0)
            t=""+maxLatency;
        maxLatency=Integer.parseInt(t);
        
        while(true)
        {
            System.err.println("--");

            System.err.print("clients ["+nclients+"]: ");
            t = in.readLine().trim();
            if (t.length()==0)
                t=""+nclients;
            nclients=Integer.parseInt(t);
            
            if (nclients<rooms || (nclients%rooms)!=0)
            {
                System.err.println("Clients must be a multiple of "+rooms);
                nclients=(nclients/rooms)*rooms;
                continue;
            }

            System.err.print("publish ["+publish+"]: ");
            t = in.readLine().trim();
            if (t.length()==0)
                t=""+publish;
            publish=Integer.parseInt(t);
            
            System.err.print("publish size ["+size+"]: ");
            t = in.readLine().trim();
            if (t.length()==0)
                t=""+size;
            size=Integer.parseInt(t);
            String chat="";
            for (int i=0;i<size;i++)
                chat+="x";

            System.err.print("pause ["+pause+"]: ");
            t = in.readLine().trim();
            if (t.length()==0)
                t=""+pause;
            pause=Integer.parseInt(t);

            System.err.print("batch ["+burst+"]: ");
            t = in.readLine().trim();
            if (t.length()==0)
                t=""+burst;
            burst=Integer.parseInt(t);

            generateLoad(uri, base, rooms, rooms_per_client, maxLatency,
                    nclients, publish, chat, pause, burst);
        }
    }

    public void initSocketAddress(String host, int port)
    {
        address=new InetSocketAddress(host,port);
    }

    public long generateLoad(String uri, String base,
            int rooms, int rooms_per_client, int maxLatency, int nclients,
            int publish, String chat, int pause, int burst) throws Exception
    {
        while (clients.size()<nclients)
        {
            int u=clients.size();
            BayeuxClient client = new BayeuxClient(http,address,uri)
            {
                public void deliver(Client from, Message message)
                {
                    if (Bayeux.META_SUBSCRIBE.equals(message.get(Bayeux.CHANNEL_FIELD)) &&
                        ((Boolean)message.get(Bayeux.SUCCESSFUL_FIELD)).booleanValue())
                        _subscribed.incrementAndGet();
                    super.deliver(from,message);
                }
            };
                

            MessageListener listener = new MessageListener()
            {
                public void deliver(Client fromClient, Client toClient, Message msg)
                {
                    Object data=(Object)msg.get(AbstractBayeux.DATA_FIELD);
                    if (data!=null)
                    {
                        String msgId=(String)msg.get(AbstractBayeux.ID_FIELD);
                        // System.err.println(name+": "+data);
                        if (msgId!=null)
                        {
                            long latency= System.currentTimeMillis()-Long.parseLong(msgId);
                            synchronized(BayeuxLoadGenerator.this)
                            {
                                _got.getAndIncrement();
                                if (_maxLatency<latency)
                                    _maxLatency=latency;
                                if (_minLatency==0 || latency<_minLatency)
                                    _minLatency=latency;
                                _totalLatency+=latency;
                            }
                        }
                    }
                }

            };
            client.addListener(listener);
                
            client.start();
                
            clients.add(client);
            Thread.sleep(100);
            if (clients.size()%25==0){
                int i=clients.size();
                System.err.println("clients = "+(i>=1000?"":i>=100?"0":i>=10?"00":"000")+i);                
            }
                    
            client.startBatch();
            if (rooms_per_client==1)
            {
                int room=u%rooms;
                client.subscribe(room>0?(base+"/"+room):base);
            }
            else
            {
                for (int i=0;i<rooms_per_client;i++)
                {
                    int room=_random.nextInt(rooms);
                    client.subscribe(room>0?(base+"/"+room):base);
                }
            }
            client.endBatch();
        }
            
        while (clients.size()>nclients)
        {
            BayeuxClient client=clients.remove(0);
            client.remove(false);
            _subscribed.addAndGet(-rooms_per_client);
            if (clients.size()%10==0)
            {
                int i=clients.size();
                System.err.println("clients = "+(i>=1000?"":i>=100?"0":i>=10?"00":"000")+i);
                Thread.sleep(300);
            }
        }
            

        Thread.sleep(500);

        while(_subscribed.get()!=nclients*rooms_per_client)
        {
            // System.err.println(destination.toDetailString());
            System.err.println("Subscribed:"+_subscribed.get()+" != "+(nclients*rooms_per_client)+" ...");
            Thread.sleep(1000);
        }
            
        System.err.println("Clients: "+nclients+" subscribed:"+_subscribed.get());

        
        synchronized(this)
        {
            _got.set(0);
            _minLatency=0;
            _maxLatency=0;
            _totalLatency=0;
        }


           
        long start=System.currentTimeMillis();
        trial:
        for (int i=1;i<=publish;)
        {
            // System.err.print(i);
            // System.err.print(',');
            int u=_random.nextInt(nclients);
            BayeuxClient c = clients.get(u);
            final String name = "Client"+(u>=1000?"":u>=100?"0":u>=10?"00":"000")+u;
            Object msg=new JSON.Literal("{\"user\":\""+name+"\",\"chat\":\""+chat+" "+i+"\"}");
            c.startBatch();
            for (int b=0;b<burst;b++)
            {
                int room=_random.nextInt(rooms);
                String id=""+System.currentTimeMillis();
                c.publish(room>0?(base+"/"+room):base,msg,id);
                i++;
                    
                if (i%10==0)
                {
                    long latency=0;
                    synchronized(this)
                    {
                        if (_got.get()>0) 
                            latency=_totalLatency/_got.get();
                    }
                    if (latency>maxLatency)
                    {
                        System.err.println("\nABORTED!");
                        break trial;
                    }
                        
                    char dot=(char)('0'+(int)(latency/100));
                    System.err.print(dot);
                    if (i%1000==0)
                        System.err.println();
                }
            }
            c.endBatch();

            if (pause>0)
                Thread.sleep(pause);
                
        }

        Thread.sleep(_maxLatency);

        for (BayeuxClient c : clients)
        {
            if (!c.isPolling())
                System.err.println("PROBLEM WITH "+c);
        }

        System.err.println();

        long last=0;
        int sleep=100;
        while (_got.get()<(nclients/rooms*rooms_per_client*publish)) 
        {
            System.err.println("Got:"+_got.get()+" < "+(nclients/rooms*rooms_per_client*publish)+" ...");
            Thread.sleep(sleep);
            if (last!=0 && _got.get()==last) { 
                break;
            }
            last=_got.get();
            sleep+=100;
        }
        System.err.println("Got:"+_got.get()+" of "+(nclients/rooms*rooms_per_client*publish));
            
        long end=System.currentTimeMillis();

        int result = _got.get();
        System.err.println("Got "+result+" at "+(result*1000/(end-start))+"/s, latency min/ave/max ="+_minLatency+"/"+(_totalLatency/result)+"/"+_maxLatency+"ms");

        return result;
    }
    
    public static void main(String[] args) throws Exception
    {
        BayeuxLoadGenerator gen = new BayeuxLoadGenerator();
        
        gen.generateLoad();
    }
    
}
