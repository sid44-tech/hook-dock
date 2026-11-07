#!/usr/bin/env python3
"""
Sends a series of test webhooks to a HookDock receive URL and reports which
were captured and which were rejected (rate limited, not found, too large...).

Uses only the Python standard library, so it runs on macOS, Linux and Windows
with any Python 3.8+.

    python3 scripts/load_test.py http://localhost:8080/in/<token> --count 50 --delay 0.1

On Windows use "py" or "python" instead of "python3".
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

LABELS = {
    200: "CAPTURED",
    404: "NOT FOUND",
    413: "TOO LARGE",
    429: "RATE LIMITED",
}


def parse_args():
    parser = argparse.ArgumentParser(description="Send repeated test webhooks to a HookDock endpoint.")
    parser.add_argument("url", help="receive URL, e.g. http://localhost:8080/in/<token>")
    parser.add_argument("-n", "--count", type=int, default=10,
                        help="total number of requests to send (default: 10)")
    parser.add_argument("-d", "--delay", type=float, default=0.5,
                        help="seconds to wait between requests, 0 for none (default: 0.5)")
    parser.add_argument("-t", "--timeout", type=float, default=10.0,
                        help="per-request timeout in seconds (default: 10)")
    args = parser.parse_args()
    if args.count < 1:
        parser.error("--count must be at least 1")
    if args.delay < 0:
        parser.error("--delay cannot be negative")
    if not args.url.startswith(("http://", "https://")):
        parser.error("url must start with http:// or https://")
    return args


def send(url, number, timeout):
    """Returns (status, retry_after, body_text, elapsed_ms). status is None on connection errors."""
    payload = {
        "test": "load_test",
        "sequence": number,
        "sent_at": datetime.now(timezone.utc).isoformat(),
    }
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Content-Type": "application/json",
            "User-Agent": "hookdock-load-test",
            "X-Load-Test-Sequence": str(number),
        },
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read().decode("utf-8", errors="replace")
            return response.status, response.headers.get("Retry-After"), body, elapsed_ms(started)
    except urllib.error.HTTPError as e:
        # 4xx/5xx still carry a status and body we want to report.
        body = e.read().decode("utf-8", errors="replace")
        return e.code, e.headers.get("Retry-After"), body, elapsed_ms(started)
    except (urllib.error.URLError, OSError) as e:
        reason = getattr(e, "reason", e)
        return None, None, str(reason), elapsed_ms(started)


def elapsed_ms(started):
    return int((time.perf_counter() - started) * 1000)


def describe(status, body):
    if status is None:
        return "ERROR"
    label = LABELS.get(status, "HTTP %d" % status)
    if status == 200:
        try:
            event_id = json.loads(body).get("id")
            if event_id:
                label += "  id=" + event_id
        except (ValueError, AttributeError):
            pass
    return label


def main():
    args = parse_args()
    print("Sending %d request(s) to %s with %.2fs delay\n" % (args.count, args.url, args.delay))
    print("%5s  %6s  %8s  %s" % ("#", "status", "time", "result"))

    counts = {}
    run_started = time.perf_counter()
    try:
        for number in range(1, args.count + 1):
            status, retry_after, body, ms = send(args.url, number, args.timeout)
            result = describe(status, body)
            if retry_after:
                result += "  (retry after %ss)" % retry_after
            if status is None:
                result += "  " + body
            print("%5d  %6s  %6dms  %s" % (number, status if status is not None else "-", ms, result))

            key = LABELS.get(status, "ERROR" if status is None else "HTTP %d" % status)
            counts[key] = counts.get(key, 0) + 1

            if number < args.count and args.delay > 0:
                time.sleep(args.delay)
    except KeyboardInterrupt:
        print("\nStopped early.")

    total = sum(counts.values())
    duration = time.perf_counter() - run_started
    print("\nSummary: %d request(s) in %.1fs" % (total, duration))
    for key in sorted(counts, key=lambda k: -counts[k]):
        print("  %-14s %d" % (key, counts[key]))

    return 0 if counts.get("ERROR", 0) == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
