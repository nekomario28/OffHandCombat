# Off Hand Combat — NeoForge 1.21.1

A safety-first NeoForge 1.21.1 continuation of BunnyCinnamon/OffHandCombat.

This fork preserves the original MIT attribution while redesigning networking, authority and player state for NeoForge 1.21.1. It is not a mechanical source-level port of the old Architectury implementation.

## Core design

- Java 21, NeoForge 21.1.242 and ModDevGradle 2.0.142.
- Dedicated, versioned and optional NeoForge custom payloads; vanilla packet formats are untouched.
- Server-authoritative target, reach, line-of-sight, readiness and weapon validation.
- Per-player non-persistent NeoForge Data Attachment for cooldown, sequence and transient execution state.
- No static UUID maps, real inventory swaps, live attribute-map mutation, or invulnerability-frame clearing.
- A copied attack attribute view is active only while the authoritative vanilla `Player.attack` path executes.
- Stable request sequence IDs, duplicate result replay and explicit failure statuses.
- Right-click is the default off-hand attack input. The dedicated `V` key remains an optional compatibility fallback.
- Vanilla off-hand swing animation is used; the old ordinal-heavy parallel animation redirects are not retained.

## Controls

Put an eligible melee weapon in the off hand, aim at an entity and right-click.

The default client mode is `USE_KEY_ALWAYS`. It converts only the `OFF_HAND` pass of NeoForge's use-input pipeline and cancels vanilla use only after an off-hand attack request is actually sent. Normal main-hand item and entity interactions therefore retain priority.

Alternative client modes are:

- `USE_KEY_WHEN_SNEAKING`: right-click attacks from the off hand only while sneaking.
- `DEDICATED_KEY`: uses the dedicated `V` key instead of converting right-click.

## Compatibility registration

Data packs may use:

- `offhandcombat:offhand_attack_blacklist` (item tag)
- `offhandcombat:offhand_attack_weapons` (explicit item allow tag)
- `offhandcombat:offhand_attack_blacklist` (enchantment tag)

Other mods may register eligibility and input-arbitration rules through the public registries documented in `docs/PUBLIC_API.md`.

## Build

```bash
gradle clean test build
```

GitHub Actions pins Java 21 and Gradle 9.2.1. Output JARs are placed in `build/libs/`.

## Test status

Automated checks cover source invariants, protocol wire IDs, sequence replay classification and cooldown mathematics. Minecraft integration and compatibility cases are tracked in `TEST_MATRIX.md`; no release should be published until its release-gate section is complete.
