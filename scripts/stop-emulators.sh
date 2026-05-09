#!/usr/bin/env bash
if [ -f /tmp/emulator.pid ]; then
    kill $(cat /tmp/emulator.pid) 2>/dev/null || true
    rm /tmp/emulator.pid
fi
