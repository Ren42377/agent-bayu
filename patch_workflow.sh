#!/bin/bash
awk '
/Restore release signing/ {
    in_restore = 1
    buffer = ""
}
in_restore {
    buffer = buffer $0 "\n"
    if ($0 ~ /> .signing\/keystore.properties/) {
        in_restore = 0
        restore_block = buffer
    }
    next
}
{
    lines[NR] = $0
}
END {
    for (i = 1; i <= NR; i++) {
        if (lines[i] != "") {
            print lines[i]
            if (lines[i] ~ /cache-read-only: false/) {
                print ""
                print restore_block
            }
        }
    }
}
' .github/workflows/android.yml > .github/workflows/android.yml.tmp && mv .github/workflows/android.yml.tmp .github/workflows/android.yml
