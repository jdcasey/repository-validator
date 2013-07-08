#!/bin/sh

BINDIR=`dirname $0`
ROOT=`dirname $BINDIR`

if [ -z $JAVA ]; then
  JAVA=`which java`
fi

echo "java: ${JAVA}"

$JAVA -jar $ROOT/repository-validator.jar $@
