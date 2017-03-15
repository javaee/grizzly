The HTTP/2 sample server.

Requirements:
Oracle JDK 1.8.0_121 (if using ALPN).


How to run:

1) Compile the project using Maven (3.0.3+)
   mvn clean install

2) Run the HTTP/2 server
   mvn exec:exec -DmainClass="org.glassfish.grizzly.samples.http2.Http2Server"

3) Test the HTTP/2 server
   3.1: HTTP/2 without push
        https://localhost:8080/getsmileys?size=16&push=false
   3.2: HTTP/2 with push
        https://localhost:8080/getsmileys?size=16&push=true
or/and

4) Test HTTPS server
   https://localhost:8081/getsmileys?size=16
