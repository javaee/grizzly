#!/bin/bash

M2_HOME=/home/oleksiys/apps/maven/apache-maven-3.0.3
PATH=$M2_HOME/bin:$PATH
 
while [ $? -eq 0 ]
do
	mvn $* clean test 
done
