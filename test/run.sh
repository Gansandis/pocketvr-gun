#!/bin/sh
# Core rules test: needs only a JDK. Run from the project root:  sh test/run.sh
set -e
OUT=/tmp/nvgun-test; rm -rf $OUT; mkdir -p $OUT
javac -d $OUT app/src/main/java/com/pocketvr/app/NvGunCore.java test/NvGunCoreTest.java 2>/dev/null \
  || java -m jdk.compiler/com.sun.tools.javac.Main -d $OUT app/src/main/java/com/pocketvr/app/NvGunCore.java test/NvGunCoreTest.java
java -cp $OUT NvGunCoreTest
