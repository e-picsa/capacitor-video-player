# Agent instructions

## Pull requests

This repo (`e-picsa/capacitor-video-player`) was forked from
`harmonwood/capacitor-video-player`, whose upstream is abandoned. All work
targets **this repo only** — never open PRs against the upstream parent.

GitHub still records the fork relationship, so `gh pr create` (and the web
compare flow) defaults the base to the upstream repo. Always be explicit:

```
gh pr create --repo e-picsa/capacitor-video-player --base main --head <branch>
```

After creating a PR, verify with `gh pr view --json url,headRepository,baseRefName`
that the base is `e-picsa/capacitor-video-player:main`. If a PR lands on the
wrong repo, close it immediately and re-open it here.

<!-- repo-templates:repo-audit:start -->
### Repo Audit Skill (Structured Codebase Audits)

- For a careful, evidence-backed repo audit, read `.agents/skills/repo-audit/SKILL.md` first and follow it end to end.
- Start from `.agents/skills/repo-audit/repo-context.md` when it exists — repo-owned archetypes, focus areas, audit file location, second-opinion preference, and `## Overrides` win over generic defaults. If it is missing or TODO, infer from README/docs/code and flag assumptions.
- If a previous `AUDIT.md` exists, read it as the baseline and reconcile prior findings as Fixed / Still open / Invalid / Superseded, plus newly identified issues. Timestamp every audit with ISO date and base commit; a single audit file always reflects the latest run.
- Confirm the one-shot cross-model reviewer with the user in advance (Step 1); run it once after the draft to spot-check top claims and hunt gaps, with no back-and-forth. Human resolves conflicts.
- Report with `path:line` citations, `P0`/`P1`/`P2` severity, `CONFIRMED` / `LIKELY` / `SUSPECTED` tiers, value H/M/L and effort S/M/L (files impacted, never days) per finding, and a fixed-schema roadmap. Keep a file ledger at `.agents/tmp/audit-ledger.md` (git-ignored, deleted afterwards); synthesis reads the ledger. Do not fix code during the audit.
<!-- repo-templates:repo-audit:end -->
