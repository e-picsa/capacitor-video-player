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
