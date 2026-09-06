# Initial desktop window placement

September 6, 2026; 1.1.3 development source, not a replacement public release.

## Reproduced failure

The full typed-channel audit reopened an intermittent native-window failure:
one modal opening reported 2,120 × 1,244 after an initial fit to a
1,920 × 1,016 work area. The trace contains the oversized initial dimensions,
the fitted dimensions, and then the old dimensions again. Waiting ten animation
pulses did not reliably fix it. The earlier successful twenty-opening run was
insufficient evidence that this was only a smoke-test timing issue.

The strengthened test also observes `WINDOW_SHOWN`, before the deferred fit.
Against unchanged production code, twenty modal repetitions plus the ordinary
and inferred-size cases fail this initial-bound check: 22 failures in 23 tests.
This isolates an oversized first request without relying on an intermittent
late acknowledgement to reproduce the problem.

## Repair

`FxWindowPlacement` now bounds the initial outer-window size before `show()` or
`showAndWait()` creates the visible native window. Explicit Stage dimensions
take precedence. Otherwise it uses the Scene preference, or the CSS-sized root
when no Scene dimensions were supplied. With no explicit Stage dimensions,
that preference is now an **outer-frame budget**: decoration space comes out of
the content area rather than being added beyond the budget. Existing responsive
layouts remain responsible for their content; this is not a scrollbar policy.

Unset positions use the owner's location, or the primary screen, to select the
initial work area. Existing overlap-based screen selection, work-area centering
and minimum-size reduction remain. The post-show fit is retained. No polling
timer, persistent maximum-size restriction or resize listener was introduced;
showing an already-visible ordinary window still preserves its placement.

JavaFX documents Stage/Window dimensions as including native decorations and
notes that the platform may ignore requested coordinates. This informs the
pre-show sizing contract; the specific late-acknowledgement diagnosis comes
from our local traces, not a claim that every platform has the same defect.
See [JavaFX Window geometry](https://openjfx.io/javadoc/21/javafx.graphics/javafx/stage/Window.html#widthProperty())
and the [OpenJFX 21 visibility/size implementation](https://github.com/openjdk/jfx/blob/jfx21/modules/javafx.graphics/src/main/java/javafx/stage/Window.java).

## Native verification details

The twenty modal repetitions retain the original one-pixel work-area assertions
after ten pulses and now also require the first shown bounds to fit. Additional
cases cover oversized minimums, inferred Scene dimensions, explicit Stage size
and later resize/re-show behavior. Pure geometry checks cover negative origins,
secondary-screen work areas and small logical high-DPI bounds.

A control using `Stage.showAndWait()` directly, without the placement helper,
confirmed that the local KDE session reports `(0,0)` after a requested move to
`(20,30)`. The resize/re-show test therefore checks that the native-accepted
position is preserved, not that the application can override the window
manager. It still requires the requested 500 × 300 size and unchanged maximum
size limits. This does not relax the first-show or settled-fit boundary checks.

Local qualification: all 222 display-enabled JavaFX tests and the retained 35
Compose tests pass with no test skips, as do shared-core checks and Linux JavaFX
staging. A separate fresh 800 × 600 Xvfb/X11 run passes all 29 window-placement
tests (24 native cases/repetitions and five geometry cases). The shared version
check remains 1.1.3 / Android 110407. These results supersede the earlier failed
window-placement run, not its recorded historical evidence.

Physical multi-monitor, Windows and macOS window-manager acceptance remains
separate from local source qualification and hosted build/package checks.
No adapter or vehicle access is involved.
