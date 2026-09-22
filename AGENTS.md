# Agent instructions

- Product work: read [the system plan](docs/projektplan.md), [requirements](docs/requirements.md), and [glossary](GLOSSARY.md). Update the glossary in the same commit when a term changes meaning or is added.
- Technical choices: read the relevant record in [decisions](docs/decisions/). New evidence may overturn a decision, but the record and plan must change with it.
- Customer source: keep `docs/projektplan-original.md` and `docs/projektplan-original.en.md` frozen.
- Documentation changes: run `nix shell nixpkgs#mermaid-cli --command uv run python docs/check.py --require-mermaid`.
