#!/usr/bin/env bash
# Run an unmodified native launcher only in a disposable image/private display.
set -euo pipefail
[[ ${RR2_PRIVATE_WINDOW_TEST:-} == 1 ]] || {
    echo "Use packaging/run-desktop-window-tests.sh for an isolated display." >&2
    exit 2
}
rr2_launch_image=$(realpath "${1:?Supply a disposable Linux app image}")
rr2_launch_output=${2:?Supply a fresh absolute output directory}
[[ $rr2_launch_output == /* && ! -e $rr2_launch_output ]]
[[ -x "$rr2_launch_image/bin/RomRaider2" ]]
mkdir -p "$rr2_launch_output"
rr2_child_pid=
cleanup() {
    if [[ -n $rr2_child_pid ]]; then
        kill "$rr2_child_pid" 2>/dev/null || true
        wait "$rr2_child_pid" 2>/dev/null || true
    fi
}
trap cleanup EXIT
env JAVA_TOOL_OPTIONS="-Duser.home=$rr2_launch_output" "$rr2_launch_image/bin/RomRaider2" > "$rr2_launch_output/launcher.log" 2>&1 &
rr2_child_pid=$!
rr2_launch_window=
for ((rr2_poll=0; rr2_poll<200; rr2_poll++)); do
    kill -0 "$rr2_child_pid" 2>/dev/null || break
    rr2_launch_window=$(xdotool search --onlyvisible --all --pid "$rr2_child_pid" --name 'RomRaider2' 2>/dev/null | head -1 || true)
    [[ -n $rr2_launch_window ]] && break
    sleep 0.1
done
[[ -n $rr2_launch_window ]] || {
    echo "The native launcher did not show its application window." >&2
    exit 3
}
rr2_window_title=$(xdotool getwindowname "$rr2_launch_window")
printf '%s\n' "$rr2_window_title"
rr2_window_version=${rr2_window_title#RomRaider2 }
[[ $rr2_window_version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    echo "Unexpected clean-start window title: $rr2_window_title" >&2
    exit 5
}
grep -Fq "RomRaider2 $rr2_window_version Build: $rr2_window_version" "$rr2_launch_output/launcher.log" || {
    echo "Startup log and application window disagree on the build version." >&2
    exit 5
}
# A clean profile opens an owned definitions dialog after the main stage.
# Dismiss that known first-run dialog before closing the editor itself.
rr2_definitions_window=
for ((rr2_poll=0; rr2_poll<100; rr2_poll++)); do
    rr2_definitions_window=$(xdotool search --onlyvisible --all --pid "$rr2_child_pid" --name '^ECU Definitions Manager$' 2>/dev/null | head -1 || true)
    [[ -n $rr2_definitions_window ]] && break
    sleep 0.1
done
if [[ -n $rr2_definitions_window ]]; then
    xdotool windowactivate --sync "$rr2_definitions_window"
    xdotool key --clearmodifiers alt+F4
    for ((rr2_poll=0; rr2_poll<100; rr2_poll++)); do
        xdotool search --onlyvisible --all --pid "$rr2_child_pid" --name '^ECU Definitions Manager$' >/dev/null 2>&1 || break
        sleep 0.1
    done
fi
xdotool windowactivate --sync "$rr2_launch_window"
xdotool key --clearmodifiers alt+F4
for ((rr2_poll=0; rr2_poll<200; rr2_poll++)); do
    kill -0 "$rr2_child_pid" 2>/dev/null || break
    sleep 0.1
done
if kill -0 "$rr2_child_pid" 2>/dev/null; then
    echo "The native application did not finish after closing its window." >&2
    exit 4
fi
wait "$rr2_child_pid"
rr2_child_pid=
echo "PACKAGED_LINUX_LAUNCH_CLOSE_PASS"
