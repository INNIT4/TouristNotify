#!/usr/bin/env bash
set -e

if ! command -v firebase &> /dev/null; then
    npm install -g firebase-tools@13
fi

firebase emulators:start \
  --only firestore,auth \
  --project demo-lupita \
  --import ./emulator-data \
  --export-on-exit ./emulator-data &

EMULATOR_PID=$!
echo $EMULATOR_PID > /tmp/emulator.pid

echo "Waiting for Firestore emulator..."
for i in $(seq 1 30); do
    if curl -s "http://localhost:8080" > /dev/null 2>&1; then
        echo "Firestore emulator ready after ${i}s"
        exit 0
    fi
    sleep 2
done

echo "ERROR: Firestore emulator did not start in 60 seconds" >&2
exit 1
