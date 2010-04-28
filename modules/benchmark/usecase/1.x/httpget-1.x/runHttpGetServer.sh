#!/bin/bash
java -cp ../lib/http.jar:../lib/framework.jar:../lib/utils.jar:./build/classes com.sun.grizzly.benchmark.HttpGetServer $@

#java -cp ../lib/http.jar:../lib/framework.jar:../lib/utils.jar:./build/classes -Djava.util.logging.config.file=./logging.properties com.sun.grizzly.benchmark.HttpGetServer $@

