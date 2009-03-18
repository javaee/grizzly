#!/bin/bash
java -cp ./lib/framework.jar:./build/classes org.glassfish.grizzly.benchmark.TCPEchoServer $@
