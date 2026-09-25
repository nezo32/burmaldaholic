#!/bin/bash
cd "$(dirname "$0")"
rm -f mc_*.json
for mi in 0 1 2; do
  for sd in 11 22 33 44; do ./sim $mi 250000000 $((sd+mi*100)) > mc_${mi}_${sd}.json & done
  wait
done
echo done > mc_done
