---
name: playbook-manager
description: "Create or update operational runbooks and playbooks. Use when: writing runbooks, documenting chaos scenarios, creating troubleshooting guides, writing deployment or incident response procedures."
allowed-tools: Read Write
---

# Playbook Skill

## Rules

- Markdown only. Imperative mood for titles ("Restart X", not "Restarting X").
- Commands in fenced `bash` blocks — never inline in prose.
- State expected outcome after each step using blockquotes (`>`), include timing if >5 s.
- 1-3 sentence explanations max, then show the command.
- Tables for config values, ports, env vars. Relative links for related docs.
- Include failure modes and rollback steps.

## Procedure

1. Use the [runbook template](./references/template.md) as the skeleton for every new doc.
2. If the runbook is a chaos experiment runbook, use [chaos conventions](./references/chaos-conventions.md).
3. If it uses shell scripts, use [script doc conventions](./references/script-docs.md).
