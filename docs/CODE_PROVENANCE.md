# Code provenance — NeoForge 1.21.1 reconstruction

Status: provenance correction / source map

This file records code-level provenance for the NeoForge 1.21.1 reconstruction without changing the established Git history. It complements `THIRD_PARTY_NOTICES.md`, `AUDIT_1.21.1.md`, and the retained upstream fork ancestry.

## 1. Original OffHandCombat lineage

This repository remains a Git fork of:

- source repository: `BunnyCinnamon/OffHandCombat`
- authoritative pre-port baseline: `e7df3ad2eec858407dd371cdfde574b35d0322c4`
- source Author identity at that baseline: `ArekkuusuJerii` / GitHub account `BunnyCinnamon`
- license: MIT

The NeoForge reconstruction starts at local commit:

- `aefb5e05b78e17e677bdcaf6d2f0143141f4ab14` — `Port OffHandCombat to NeoForge 1.21.1 (#1)`

Its parent is the exact upstream baseline `e7df3ad2eec858407dd371cdfde574b35d0322c4`, so original repository history remains reachable rather than being replaced by a new flattened root.

The 1.21.1 code is not a mechanical path move of the old Architectury/Forge/Fabric sources. For example, the old `common/src/main/java/cinnamon/ofc/HandPlatform.java` temporarily swapped real hand stacks and modified the live player `AttributeMap`; the replacement instead constructs a separate attribute view and redirects selected reads during one authoritative attack. `AUDIT_1.21.1.md` documents these architecture replacements.

## 2. Dual Wielding Unbound design/code adaptation

The reconstructed player attack path also has a distinct second-source relationship that should be preserved explicitly.

Source repository:

- `ekulxam/dual_wielding` (Dual Wielding Unbound)
- license: MIT
- current audited reference snapshot: `77100f9b980b1d9764ad8e71386da67b6e5bb7c7`
- relevant path at that snapshot: `src/main/java/survivalblock/dual_wielding/mixin/PlayerEntityMixin.java`
- relevant companion path: `src/main/java/survivalblock/dual_wielding/mixin/AttributeContainerMixin.java`

The core attribute-view pattern is present from the donor repository's root commit:

- donor origin commit: `235bfc36fb258acee23f0feaaa0421c41bb32a96`
- Git Author: `SkyNotTheLimit <159592458+ekulxam@users.noreply.github.com>`
- GitHub identity: `ekulxam`
- donor path: `src/main/java/survivalblock/dual_wielding/mixin/PlayerEntityMixin.java`

### Local affected path

- `src/main/java/dev/nekomario/offhandcombat/mixin/PlayerMixin.java`
- introduced in the NeoForge port commit `aefb5e05b78e17e677bdcaf6d2f0143141f4ab14`

The local path is **not byte-exact donor code**, but its attack/attribute seam is materially derived/adapted from the donor design and implementation structure. In particular, both implementations:

- intercept attribute reads inside `Player.attack`;
- maintain a separate off-hand cooldown path;
- redirect `getItemInHand` and attack-strength reads for off-hand execution;
- construct a separate default-backed attribute container/map;
- copy current attribute values into that view;
- remove hand-slot modifiers and add the opposite-slot modifiers for the off-hand interpretation.

The local reconstruction deliberately diverges in important ways:

- it does not use Dual Wielding Unbound's automatic hand-selection/double-attack policy;
- it rejects the donor's invulnerability-reset behavior;
- it keeps state in the local NeoForge attachment model;
- it narrows the copied attribute view to avoid mutating the live player attributes;
- it does not run the donor's location-based enchantment-effect mutation sequence while constructing the view;
- network authority, request/result sequencing, compatibility policy, public API, lifecycle, tests, and release harness are local reconstruction work.

Because the original `aefb5e05...` port commit mixes this adapted seam with substantial locally authored architecture and implementation, assigning the **entire commit** to `ekulxam` would be false attribution. Rewriting the commit Author is therefore not an appropriate correction. The truthful remedy is this durable component-level source map plus the existing MIT notice.

Classification for this boundary: `PROVENANCE_CORRECTION` (mixed/derived adaptation; no history rewrite).

## 3. RLOffHandCombat

`RLOffHandCombat` was used only as bug-history/regression reference according to the recorded migration audit and third-party notice. No source-code import is claimed. If future evidence shows otherwise, this boundary must be reopened before changing Git authorship.

## 4. NeoForge and build dependencies

NeoForge and other build/runtime dependencies are dependencies, not committed donor source trees. Their use does not make the NeoForge port commit an import of those repositories.

## 5. Future integration rule

For any future direct code import:

1. pin the source repository and full commit SHA before integration;
2. preserve the original Git Author/AuthorDate when the imported change is substantially unchanged and separable;
3. use a separate local adaptation commit when practical;
4. for mixed derived work that cannot truthfully carry one donor Author, keep the local Author and extend this source map with exact donor path/commit evidence rather than inventing a single attribution.

Do not use GitHub contributor UI as provenance evidence; source Git metadata and exact revision/path evidence are authoritative.
