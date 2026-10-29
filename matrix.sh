#!/bin/bash
export JAVA_HOME=~/.jdks/ms-21.0.12.1
cd /c/Users/Alten/IdeaProjects/ColoredLight
OUT=build/matrix
mkdir -p $OUT
: > $OUT/summary.txt
for f in $(ls versions/*.properties | sort -V) $(ls versions/neoforge/*.properties | sort -V) $(ls versions/forge/*.properties | sort -V); do
  v=$(basename $f .properties)
  case $f in versions/neoforge/*) l=neoforge;; versions/forge/*) l=forge;; *) l=fabric;; esac
  if [ "$l$v" = "forge1.16.5" ]; then
    (cd legacy-forge && ./gradlew build -q) > $OUT/$l-$v.log 2>&1
  else
    ./gradlew build -q -Pminecraft_version=$v -Ploader=$l > $OUT/$l-$v.log 2>&1
  fi
  if [ $? -eq 0 ]; then echo "OK $l $v" >> $OUT/summary.txt; else echo "FAIL $l $v" >> $OUT/summary.txt; fi
done
echo DONE >> $OUT/summary.txt
