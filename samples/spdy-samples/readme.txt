The SPDY sample server.

Requirements:
OpenJDK 1.7.0-u14+ (required by NPN)


How to run:

1) Compile the project using Maven (3.0.3+)
   mvn clean install

2) Run the SPDY server
   mvn exec:exec -DmainClass="org.glassfish.grizzly.samples.spdy.SpdyServer"

3) Test SPDY server
   3.1: Normal SPDY mode
        https://localhost:8080/getsmileys?size=16&push=false
   3.2: SPDY Server-Push mode
        https://localhost:8080/getsmileys?size=16&push=true
or/and

4) Run HTTPS server
   mvn exec:exec -DmainClass="org.glassfish.grizzly.samples.spdy.HttpsOnlyServer"

5) Test HTTPS server
   https://localhost:8081/getsmileys?size=16
