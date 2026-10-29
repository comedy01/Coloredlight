#!/bin/bash
# Runs the dev self-test on the major versions; results in build/selftests/summary.txt
export JAVA_HOME=~/.jdks/ms-21.0.12.1
cd /c/Users/Alten/IdeaProjects/ColoredLight
OUT=build/selftests
mkdir -p $OUT
: > $OUT/summary.txt
TARGETS=${TARGETS:-"fabric:1.18.2 fabric:1.20.1 fabric:1.21.1 fabric:26.1.2 neoforge:1.21.1 neoforge:26.3 forge:1.18.2 forge:1.20.1"}
for t in $TARGETS; do
  l=${t%%:*}; v=${t#*:}
  log=$OUT/$l-$v.log
  timeout 1200 ./gradlew runSelftest -Pminecraft_version=$v -Ploader=$l > $log 2>&1
  rc=$?
  r=$(grep -o "red/blue[^,]*" $log | tail -1)
  if grep -q "ALL CHECKS PASSED" $log; then echo "PASS $l $v $r" >> $OUT/summary.txt
  elif [ $rc -eq 124 ]; then echo "TIMEOUT $l $v" >> $OUT/summary.txt
  else echo "FAIL $l $v (rc=$rc) $({ grep -aF -m1 '[coloredlight-selftest] FAILED' $log || grep -a -m1 -E 'FAIL|Exception|error:' $log; } | cut -c1-150)" >> $OUT/summary.txt; fi
done
echo DONE >> $OUT/summary.txt
