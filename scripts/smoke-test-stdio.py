#!/usr/bin/env python3
"""Exercise concurrent MCP responses in a built JAR without network access.

Usage: python3 scripts/smoke-test-stdio.py [--jar path/to/lit-mcp.jar]
"""
import argparse
import json
import queue
import subprocess
import threading
import time
import tempfile
from pathlib import Path


def check(jar, batches, batch_size):
    with tempfile.TemporaryFile(mode="w+t") as stderr:
        process = subprocess.Popen(
            ["java", "-jar", str(jar), "--transport=stdio"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=stderr,
            text=True,
        )
        incoming = queue.Queue()

        def read_responses():
            for line in process.stdout:
                try:
                    incoming.put(json.loads(line))
                except ValueError:
                    incoming.put(RuntimeError(f"Non-JSON stdout: {line!r}"))
            incoming.put(EOFError("MCP stdout closed"))

        threading.Thread(target=read_responses, daemon=True).start()

        def send(method, request_id=None, params=None):
            message = {"jsonrpc": "2.0", "method": method}
            if request_id is not None:
                message["id"] = request_id
            if params is not None:
                message["params"] = params
            process.stdin.write(json.dumps(message) + "\n")
            process.stdin.flush()

        def receive(expected_ids, timeout=10):
            pending = set(expected_ids)
            responses = {}
            deadline = time.monotonic() + timeout
            while pending:
                try:
                    response = incoming.get(timeout=max(0, deadline - time.monotonic()))
                except queue.Empty:
                    raise AssertionError(f"Missing {len(pending)} responses: {sorted(pending)[:10]}") from None
                if isinstance(response, Exception):
                    raise response
                request_id = response.get("id")
                if request_id is None:
                    continue  # Notifications do not acknowledge requests.
                assert request_id in pending, f"Unexpected/duplicate response: {response}"
                assert "error" not in response, response
                responses[request_id] = response["result"]
                pending.remove(request_id)
            return responses

        try:
            send("initialize", 1, {
                "protocolVersion": "2024-11-05", "capabilities": {},
                "clientInfo": {"name": "stdio-concurrency-regression", "version": "1"},
            })
            receive({1}, timeout=30)
            send("notifications/initialized")
            send("tools/list", 2)
            tools = receive({2})[2]["tools"]
            assert any(tool["name"] == "flibustaSearchBooksByName" for tool in tools)
            next_id = 3
            for batch in range(batches):
                ids = set(range(next_id, next_id + batch_size))
                for request_id in sorted(ids):
                    send("tools/call", request_id, {
                        "name": "flibustaSearchBooksByName",
                        "arguments": {"bookName": "", "limit": 1},
                    })
                for result in receive(ids).values():
                    assert not result.get("isError", False), result
                    body = json.loads(result["content"][0]["text"])
                    assert body["errors"] == ["Error: Book name must not be blank"], body
                next_id += batch_size
                print(f"Batch {batch + 1}: {batch_size}/{batch_size} responses", flush=True)
            send("ping", next_id)
            receive({next_id})
            assert process.poll() is None, "Server exited after concurrent requests"
            print("PASS: handshake, concurrent responses, subsequent ping; no network requests")
        except BaseException:
            stderr.seek(0)
            print(stderr.read()[-12000:])
            raise
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            process.stdin.close()
            process.stdout.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, default=Path(__file__).resolve().parents[1] / "build/libs/lit-mcp-1.0.jar")
    parser.add_argument("--batches", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=100)
    args = parser.parse_args()
    if args.batches < 1 or args.batch_size < 1:
        parser.error("batches and batch-size must be positive")
    check(args.jar, args.batches, args.batch_size)
