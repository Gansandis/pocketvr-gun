#!/bin/sh
# Rules + hand-logic tests: need only a JDK. Run from the project root:  sh test/run.sh
set -e
OUT=/tmp/nvgun-test; rm -rf $OUT; mkdir -p $OUT
SRC="app/src/main/java/com/pocketvr/app/NvGunCore.java app/src/main/java/com/pocketvr/app/NvGunHands.java test/NvGunCoreTest.java test/NvGunHandsTest.java"
javac -d $OUT $SRC 2>/dev/null || java -m jdk.compiler/com.sun.tools.javac.Main -d $OUT $SRC
java -cp $OUT NvGunCoreTest
java -cp $OUT NvGunHandsTest
