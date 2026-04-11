#!/usr/bin/env python3
"""
PR Review Tool — Automated code review using Claude Opus 4.6.

Fetches a pull request diff from GitHub, sends it to Claude for analysis,
and posts structured review comments (inline + summary) back to the PR.

Usage:
    python scripts/pr_review.py --repo OWNER/REPO --pr 42

Environment variables:
    ANTHROPIC_API_KEY  – Required. Anthropic API key.
    GITHUB_TOKEN       – Required. GitHub token with pull-request write scope.
    GITHUB_REPOSITORY  – Fallback for --repo (auto-set in GitHub Actions).
    GITHUB_EVENT_PATH  – Fallback for --pr  (auto-set in GitHub Actions).
    GITHUB_OUTPUT      – If set, outputs verdict/risk/count for downstream steps.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import textwrap
import urllib.error
import urllib.request
from typing import Any

import anthropic

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

CLAUDE_MODEL = "claude-opus-4-6"
MAX_DIFF_CHARS = 120_000  # Truncate diffs exceeding this to fit context window
MAX_RETRIES = 2  # Retry Claude calls on transient JSON parse failures

# ---------------------------------------------------------------------------
# GitHub helpers
# ---------------------------------------------------------------------------


def _github_request(
    url: str,
    token: str,
    *,
    method: str = "GET",
    data: dict | None = None,
    accept: str = "application/vnd.github.v3+json",
) -> Any:
    """Low-level GitHub API request. Returns parsed JSON or raw bytes."""
    headers = {
        "Authorization": f"token {token}",
        "Accept": accept,
        "User-Agent": "monotrypt-pr-review-tool",
    }
    body = json.dumps(data).encode() if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    if body:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req) as resp:
            raw = resp.read()
            if "json" in accept:
                return json.loads(raw.decode())
            return raw.decode()
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode() if exc.fp else ""
        print(f"GitHub API error {exc.code}: {error_body}", file=sys.stderr)
        raise


def github_api(endpoint: str, token: str, **kwargs: Any) -> Any:
    return _github_request(f"https://api.github.com{endpoint}", token, **kwargs)


def get_pr_details(repo: str, pr_number: int, token: str) -> dict:
    """Fetch PR metadata (title, body, author, branches)."""
    return github_api(f"/repos/{repo}/pulls/{pr_number}", token)


def get_pr_diff(repo: str, pr_number: int, token: str) -> str:
    """Fetch the unified diff for the entire PR."""
    return github_api(
        f"/repos/{repo}/pulls/{pr_number}",
        token,
        accept="application/vnd.github.v3.diff",
    )


def get_pr_files(repo: str, pr_number: int, token: str) -> list[dict]:
    """Fetch changed file list (handles pagination up to 300 files)."""
    files: list[dict] = []
    page = 1
    while True:
        batch = github_api(
            f"/repos/{repo}/pulls/{pr_number}/files?per_page=100&page={page}",
            token,
        )
        if not batch:
            break
        files.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return files


# ---------------------------------------------------------------------------
# Claude review engine
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = textwrap.dedent("""\
    You are an expert code reviewer for MonoTrypT, a premium Android music player.
    The tech stack is Kotlin, Jetpack Compose, Hilt, Room, Media3/ExoPlayer, Ktor,
    C++ native DSP via JNI, and ProjectM visualizer.

    Review the pull request diff carefully. Focus on:
    1. **Bugs & Logic Errors** — incorrect behavior, null-safety, race conditions,
       off-by-one errors, missing edge-case handling.
    2. **Security** — injection, data exposure, improper encryption, unsafe JNI usage,
       hard-coded secrets.
    3. **Performance** — memory leaks, unnecessary allocations, main-thread blocking,
       inefficient DB queries, unscoped coroutines.
    4. **Android Best Practices** — lifecycle awareness, proper coroutine scoping,
       Compose stability/recomposition, correct use of remember/LaunchedEffect.
    5. **Code Quality** — readability, naming, DRY violations, missing error handling
       at system boundaries.

    Return your review as a **single JSON object** (no markdown fences, no extra
    text before or after) with this schema:

    {
      "summary": "<2-4 sentence summary of the PR and overall assessment>",
      "verdict": "APPROVE" | "REQUEST_CHANGES" | "COMMENT",
      "risk_level": "low" | "medium" | "high",
      "comments": [
        {
          "path": "<relative file path exactly as shown in the diff header>",
          "line": <positive integer — a line number from the NEW side of the diff>,
          "severity": "critical" | "warning" | "suggestion" | "nitpick",
          "body": "<explanation of the issue and a concrete suggested fix>"
        }
      ]
    }

    Guidelines for comments:
    - Only include comments that provide genuine value. Skip trivial style nits
      unless they indicate a real consistency problem.
    - The `line` field MUST reference a line that was added or modified in the diff
      (a "+" line). Use the line number shown in the @@ hunk header for the new file.
    - If an issue spans a range, use the first relevant added line.
    - For deleted-only files, omit inline comments and describe the concern in
      the summary instead.
    - Be specific: say what is wrong, why it matters, and how to fix it.
    - If nothing meaningful needs changing, return verdict "APPROVE" with an
      empty comments array.
""")


def _build_user_message(diff: str, pr_details: dict, files: list[dict]) -> str:
    """Assemble the user-message content for Claude."""
    file_summary = "\n".join(
        f"  - `{f['filename']}` (+{f['additions']}, -{f['deletions']}) [{f['status']}]"
        for f in files
    )

    truncated_note = ""
    if len(diff) > MAX_DIFF_CHARS:
        diff = diff[:MAX_DIFF_CHARS]
        truncated_note = (
            "\n\n> **Note:** The diff was truncated to fit the context window. "
            "Focus your review on the visible portion."
        )

    body = pr_details.get("body") or "No description provided."

    return (
        f"## Pull Request #{pr_details['number']}: {pr_details['title']}\n\n"
        f"**Author:** {pr_details['user']['login']}\n"
        f"**Branch:** `{pr_details['head']['ref']}` -> `{pr_details['base']['ref']}`\n\n"
        f"### Description\n{body}\n\n"
        f"### Changed Files ({len(files)})\n{file_summary}\n\n"
        f"### Diff\n```diff\n{diff}\n```{truncated_note}"
    )


def review_with_claude(
    client: anthropic.Anthropic,
    diff: str,
    pr_details: dict,
    files: list[dict],
) -> dict:
    """Call Claude Opus 4.6 and return the structured review dict."""
    user_message = _build_user_message(diff, pr_details, files)

    for attempt in range(1, MAX_RETRIES + 1):
        response = client.messages.create(
            model=CLAUDE_MODEL,
            max_tokens=16384,
            system=[
                {
                    "type": "text",
                    "text": SYSTEM_PROMPT,
                    "cache_control": {"type": "ephemeral"},
                }
            ],
            messages=[{"role": "user", "content": user_message}],
        )

        content = response.content[0].text.strip()

        # Strip accidental markdown fences
        if content.startswith("```"):
            content = content.split("\n", 1)[1]
        if content.endswith("```"):
            content = content.rsplit("```", 1)[0]
        content = content.strip()

        try:
            review = json.loads(content)
        except json.JSONDecodeError:
            if attempt < MAX_RETRIES:
                print(
                    f"  ⚠ JSON parse failed (attempt {attempt}), retrying...",
                    file=sys.stderr,
                )
                continue
            print(
                "Error: Claude returned non-JSON response after retries.",
                file=sys.stderr,
            )
            print(f"Raw output:\n{content[:2000]}", file=sys.stderr)
            sys.exit(1)

        # Validate required keys
        review.setdefault("summary", "No summary provided.")
        review.setdefault("verdict", "COMMENT")
        review.setdefault("risk_level", "medium")
        review.setdefault("comments", [])

        if review["verdict"] not in ("APPROVE", "REQUEST_CHANGES", "COMMENT"):
            review["verdict"] = "COMMENT"

        return review

    # Unreachable, but keeps type checkers happy
    sys.exit(1)


# ---------------------------------------------------------------------------
# Post review to GitHub
# ---------------------------------------------------------------------------

SEVERITY_EMOJI = {
    "critical": "\U0001f534",   # red circle
    "warning": "\U0001f7e1",    # yellow circle
    "suggestion": "\U0001f535", # blue circle
    "nitpick": "\u26aa",        # white circle
}

RISK_EMOJI = {"low": "\U0001f7e2", "medium": "\U0001f7e1", "high": "\U0001f534"}


def post_review(
    repo: str,
    pr_number: int,
    token: str,
    review: dict,
    files: list[dict],
) -> None:
    """Submit a pull-request review via the GitHub API."""
    valid_paths = {f["filename"] for f in files}

    inline_comments: list[dict] = []
    overflow_comments: list[str] = []

    for c in review.get("comments", []):
        path = c.get("path", "")
        line = c.get("line")
        severity = c.get("severity", "suggestion")
        emoji = SEVERITY_EMOJI.get(severity, "\U0001f535")
        formatted = f"{emoji} **{severity.upper()}**: {c['body']}"

        if path in valid_paths and isinstance(line, int) and line > 0:
            inline_comments.append(
                {"path": path, "line": line, "side": "RIGHT", "body": formatted}
            )
        else:
            overflow_comments.append(f"- {formatted} (`{path}`)")

    risk = review.get("risk_level", "unknown")
    risk_icon = RISK_EMOJI.get(risk, "\U0001f7e1")

    body_parts = [
        "## Claude Opus 4.6 Code Review\n",
        f"**Risk Level:** {risk_icon} {risk.upper()}\n",
        "### Summary\n",
        review.get("summary", "No summary provided."),
    ]

    if overflow_comments:
        body_parts += ["\n\n### General Comments\n"] + overflow_comments

    body = "\n".join(body_parts)

    event_map = {
        "APPROVE": "APPROVE",
        "REQUEST_CHANGES": "REQUEST_CHANGES",
        "COMMENT": "COMMENT",
    }
    event = event_map.get(review["verdict"], "COMMENT")

    payload: dict[str, Any] = {"body": body, "event": event}
    if inline_comments:
        payload["comments"] = inline_comments

    try:
        github_api(
            f"/repos/{repo}/pulls/{pr_number}/reviews",
            token,
            method="POST",
            data=payload,
        )
    except urllib.error.HTTPError:
        # If inline comments fail (e.g., stale diff positions), retry without them
        if inline_comments:
            print(
                "  ⚠ Inline comments rejected — reposting as summary-only review.",
                file=sys.stderr,
            )
            # Fold inline comments into body
            folded = "\n\n### Inline Comments (could not attach to diff)\n"
            for ic in inline_comments:
                folded += f"\n**`{ic['path']}` L{ic['line']}**\n{ic['body']}\n"
            payload["body"] = body + folded
            del payload["comments"]
            github_api(
                f"/repos/{repo}/pulls/{pr_number}/reviews",
                token,
                method="POST",
                data=payload,
            )
        else:
            raise


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------


def _resolve_args() -> tuple[str, int]:
    """Parse CLI args and environment to get (repo, pr_number)."""
    parser = argparse.ArgumentParser(
        description="Automated PR review using Claude Opus 4.6",
    )
    parser.add_argument(
        "--repo",
        default=os.environ.get("GITHUB_REPOSITORY"),
        help="GitHub repository (OWNER/REPO). Falls back to $GITHUB_REPOSITORY.",
    )
    parser.add_argument(
        "--pr",
        type=int,
        default=None,
        help="Pull request number. Auto-detected in GitHub Actions if omitted.",
    )
    args = parser.parse_args()

    pr_number = args.pr

    if not pr_number:
        event_path = os.environ.get("GITHUB_EVENT_PATH")
        if event_path and os.path.exists(event_path):
            with open(event_path) as f:
                event = json.load(f)
            pr_number = (event.get("pull_request") or {}).get("number") or event.get(
                "number"
            )

    if not args.repo or not pr_number:
        parser.error(
            "--repo and --pr are required "
            "(or set GITHUB_REPOSITORY / trigger from a pull_request event)."
        )

    return args.repo, int(pr_number)


def main() -> None:
    repo, pr_number = _resolve_args()

    github_token = os.environ.get("GITHUB_TOKEN")
    if not github_token:
        print("Error: GITHUB_TOKEN environment variable is required.", file=sys.stderr)
        sys.exit(1)

    if not os.environ.get("ANTHROPIC_API_KEY"):
        print(
            "Error: ANTHROPIC_API_KEY environment variable is required.",
            file=sys.stderr,
        )
        sys.exit(1)

    print(f"Reviewing PR #{pr_number} in {repo} ...")

    # 1. Fetch PR data from GitHub
    pr_details = get_pr_details(repo, pr_number, github_token)
    diff = get_pr_diff(repo, pr_number, github_token)
    files = get_pr_files(repo, pr_number, github_token)

    print(f"  Title:   {pr_details['title']}")
    print(f"  Files:   {len(files)}")
    print(f"  Diff:    {len(diff):,} chars")

    # 2. Run Claude review
    print(f"  Analyzing with {CLAUDE_MODEL} ...")
    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY from env
    review = review_with_claude(client, diff, pr_details, files)

    print(f"  Verdict: {review['verdict']}")
    print(f"  Risk:    {review['risk_level']}")
    print(f"  Issues:  {len(review['comments'])}")

    # 3. Post review to GitHub
    print("  Posting review ...")
    post_review(repo, pr_number, github_token, review, files)
    print("  Done.")

    # 4. Write outputs for GitHub Actions
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as f:
            f.write(f"verdict={review['verdict']}\n")
            f.write(f"risk_level={review['risk_level']}\n")
            f.write(f"comment_count={len(review['comments'])}\n")


if __name__ == "__main__":
    main()
