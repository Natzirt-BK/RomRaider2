#!/usr/bin/env bash
# Native window tests need both an isolated display and a real EWMH window manager.
set -euo pipefail

if [[ ${1:-} != --private-xvfb ]]; then
    exec xvfb-run -a -s '-screen 0 1280x1024x24' bash "$0" --private-xvfb "$@"
fi
shift
rr2_window_test_dir=$(mktemp -d -t rr2-window-test.XXXXXX)
rr2_window_manager=${RR2_TEST_WINDOW_MANAGER:-openbox}
rr2_window_manager_pid=
cleanup() {
    if [[ -n $rr2_window_manager_pid ]]; then
        kill "$rr2_window_manager_pid" 2>/dev/null || true
        wait "$rr2_window_manager_pid" 2>/dev/null || true
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# No autostart/session scripts or user configuration. An optional private library
# directory is used only by the test WM, never by the application under test.
rr2_window_test_config=$(realpath "$(dirname "$0")/fixtures/window-test-rc.xml")
if [[ -n ${RR2_TEST_WM_LIB_DIR:-} ]]; then
    env LD_LIBRARY_PATH="$RR2_TEST_WM_LIB_DIR" XDG_DATA_DIRS="${RR2_TEST_WM_DATA_DIR:-/usr/local/share:/usr/share}" "$rr2_window_manager" --sm-disable --config-file "$rr2_window_test_config" >"$rr2_window_test_dir/wm.log" 2>&1 &
else
    "$rr2_window_manager" --sm-disable --config-file "$rr2_window_test_config" >"$rr2_window_test_dir/wm.log" 2>&1 &
fi
rr2_window_manager_pid=$!
rr2_window_manager_ready=0
for ((rr2_poll=0; rr2_poll<50; rr2_poll++)); do
    if ! kill -0 "$rr2_window_manager_pid" 2>/dev/null; then break; fi
    if xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q 'window id # 0x[1-9a-fA-F]'; then
        rr2_window_manager_ready=1
        break
    fi
    sleep 0.1
done
if [[ $rr2_window_manager_ready != 1 ]]; then
    echo "Test window manager did not start; diagnostics: $rr2_window_test_dir/wm.log" >&2
    sed -n '1,80p' "$rr2_window_test_dir/wm.log" >&2
    exit 1
fi
"$@"
