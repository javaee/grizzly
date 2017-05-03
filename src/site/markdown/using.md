In addition to java.net projects such as [GlassFish][gforg], [Shoal][shoal], and
[Jersey][jersey], here\'s just a few external organizations using Grizzly in their projects.
If you\'d like to have your project listed, drop us a line on our [mailing lists][lists].

[lists]: mailing.html
[gforg]: https://glassfish.java.net
[jersey]: https://jersey.java.net
[shoal]: https://shoal.java.net

---


### Wellfleet Software

[![wellfleet](images/wellfleet.png)][wellfleetlink]

"My company, [Wellfleet Software][wellfleetlink], is using Grizzly in our [WellGEO RegRep][geo] product
implementing a Geospatial Registry and Repository based on [ebXML RegRep 4.0][ebxml] standard.

The Grizzly project is used to implement an embedded web server to host a Subscriber
endpoint for PubSubHubBub protocol.  One specific application of this Grizzly-based Hub
Subscriber is in our Administration UI application where the UI is kept synchronized with
changes in the server by receiving notification of changes from the server as an Atom feed.
The Grizzly project provided an efficient and effective solution to synchronize any client
to data changes in our WellGEO RegRep server.

We are particularly pleased with the quality of the Grizzly project and with the
responsiveness of the dev team. Thank you for the good work on Grizzly."


[wellfleetlink]: http://www.wellfleetsoftware.com
[geo]: http://www.wellfleetsoftware.com/products
[ebxml]: https://wiki.oasis-open.org/regrep/FrontPage

### Open-Xchange

[![openxchange](images/ox-s.png)][ox]

"I just wanted to inform you that that we released OX App Suite 7.0.1 last week [[1][l1]].
This is the first version that offers the option to install Grizzly as part of the
backend.

An overview of how Grizzly is used within OX App Suite can be seen at [[2][l2]], i
thought you might be interested in this. OX App Suite is free (GPLv2, CC
BY-NC-SA 2.5) and easy to install [[3][l3]], so feel free to take a closer look.

Besides that I wanted to thank you for being an awesome upstream. Alexey and
Ryan, thanks for the great responses to my questions and bug reports."
<br/>
<br/>
<br/>
<br/>
<br/>

[l1]: https://forum.open-xchange.com/showthread.php?7571
[l2]: http://oxpedia.org/wiki/index.php?title=Grizzly
[l3]: http://oxpedia.org/wiki/index.php?title=AppSuite:Main_Page_AppSuite#quickinstall
[ox]: http://www.open-xchange.com

---

### The OpenDJ Project

[![opendj](http://opendj.forgerock.org/images/opendj-tagline-179x65.png)][dj]

"We are using Grizzly 2.3 at [ForgeRock][fr] as part of our Open Source [OpenDJ Project][dj]
(formally the Sun OpenDS project) which implements a high performance LDAP / HTTP
based Directory Server. We use the Grizzly IO Framework in our LDAP client/server
[SDK][sdk] as the asynchronous IO layer. We also use the Grizzly HTTP Servlet module in
the next version of our Directory Server as the embedded Servlet container for our
REST to LDAP service.

We are pleased with the performance and quality of the Grizzly project, and
especially the helpfulness and responsiveness of the Grizzly developers!"

[fr]: http://forgerock.com/
[dj]: http://opendj.forgerock.org/
[sdk]: http://opendj.forgerock.org/opendj-ldap-sdk/


### The dCache Project

[![dcache](http://www.dcache.org/images/dcache-banner.png)][dc]

"The [dCache][dc] project uses Grizzly-NIO in it\'s own implementation of
[ONC RPC][rpc] and [NFSv4.1][nfs]."
<br/>
<br/>
<br/>
<br/>

[dc]: http://www.dcache.org
[rpc]: https://github.com/dCache/oncrpc4j
[nfs]: https://github.com/dCache/jpnfs

---

### KakaoTalk

[![talk](images/kakaotalk.png)][talk]

I would like to share Grizzly\'s use-case of our company, Kakao Corp. in South Korea.

KakaoTalk(which is a product of Kakao company, [http://en.wikipedia.org/wiki/KakaoTalk](http://en.wikipedia.org/wiki/KakaoTalk))
is South Korea\'s No.1 mobile messenger.

As of Feb. 2013:

* Registered users: 78 million
* Daily unique visitors: 29 million
* Daily messages sent: 4.8 billion

<br/>
Grizzly 2.x has been used for all sorts of things in our company.

1. Thrift servers(more than 300 servers) based on grizzly-thrift.
Before using grizzly-thrift, servers used a thread pool model with Blocking IO.
In many connections, thread resources were wasted and scalability/throughput was somewhat poor.

2. Memcached clients(more than 300 servers) based on grizzly-memcached.
Before using grizzly-memcached, we used xmemcached or spymemcached which are clients
of memcached. Though xmemcached and spymemcached already have NIO model and various
optimization logics, there were some scalable problems in many concurrent requests
and high load of real world unfortunately.

3. AJP/HTTP servers(more than 80 servers) based on grizzly-http and grizzly-http-ajp.
Before using grizzly-http/ajp, we used ruby-on-rails for web-front. When I moved
this into grizzly, I experienced enormous performance improvement. Finally, we could reduce
expenses for web servers.

4. Custom grizzly messaging filters(more than 80 servers) for communicating our internal messaging system.

The above systems related to Grizzly project have been very stable and scalable (for over a year now)!

[talk]: http://www.kakao.com/talk/en
