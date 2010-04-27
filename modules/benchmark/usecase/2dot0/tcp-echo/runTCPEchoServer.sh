#!/bin/bash
java -cp ./lib/framework.jar:./build/classes com.sun.grizzly.benchmark.TCPEchoServer $@
