# Contributing to RomRaider2

RomRaider2 deals with real vehicle hardware, so a small, well-tested change is
more useful than a large change with unclear side effects.

## Before reporting a bug

- Use the newest public release or identify the exact commit you built.
- Check whether the problem happens in both Editor and Logger or only one.
- Write down the operating system, interface, driver, protocol, and steps that
  reproduce it.
- Remove names, full ECU identifiers, ROMs, definitions, and vehicle data from
  screenshots and logs.
- For a crash or connection problem, include the relevant part of the local
  diagnostic log. The Logger Help menu can open its location.

Never attach a copyrighted ROM, private definition, complete vehicle log, or
owner-specific calibration to a public issue.

## Code changes

- Keep protocol, file-format, checksum, and safety behavior out of Swing event
  handlers.
- Do not add ECU write, reset, or flashing behavior without the required
  capability checks and hardware test plan.
- Add a focused regression test for a bug fix when practical.
- Run `ant unittest` with Java 21 before submitting a change.
- Check both Windows and Linux behavior for shared interface or packaging work.
- Keep unrelated formatting and generated files out of the change.

If hardware is required and you do not have it, say exactly what was tested and
what still needs a connected check.

## Pull requests

Describe the problem, the change, and the testing in plain language. Keep one
main subject per pull request. Screenshots are useful for interface changes, but
they must not expose private vehicle data.

By contributing, you agree that your work can be distributed under the
project's GPL-2.0-or-later license.
