"""
Temp script to test the capture_source feature from PR #5156.
Logs traces to the PR-5156 dev environment.

Usage:
    OPIK_URL_OVERRIDE=https://pr-5156.dev.comet.com/ python scripts/log_pr5156_source_code.py
"""

import os
import opik

os.environ.setdefault("OPIK_URL_OVERRIDE", "https://pr-5156.dev.comet.com/")

opik.configure(use_local=True, url="https://pr-5156.dev.comet.com/")


@opik.track(capture_source=True)
def add_numbers(x: int, y: int) -> int:
    """Simple addition to test source capture on a root trace."""
    return x + y


@opik.track(capture_source=True)
def inner_step(value: int) -> str:
    """Inner function — tests capture_source on a child span."""
    return f"processed: {value * 2}"


@opik.track(capture_source=True)
def pipeline(a: int, b: int) -> str:
    """Top-level pipeline — source should appear as a trace comment."""
    total = add_numbers(a, b)
    result = inner_step(total)
    return result


if __name__ == "__main__":
    print("Logging to:", os.environ["OPIK_URL_OVERRIDE"])

    # Simple single-function trace
    out1 = add_numbers(3, 7)
    print(f"add_numbers(3, 7) = {out1}")

    # Nested trace (root trace + child spans all with capture_source)
    out2 = pipeline(10, 20)
    print(f"pipeline(10, 20) = {out2}")

    opik.flush_tracker()
    print("Done — check the UI for source code comments on the traces.")
