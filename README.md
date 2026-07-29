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
- Dedicated off-hand attack key by default (`V`). Legacy use-key conversion is an explicit client opt-in.
- Vanilla off-hand swing animation is used; the old ordinal-heavy parallel animation redirects are not retained.

## Controls

Put an eligible melee weapon in the off hand, aim at an entity and press `V`.

The client config supports two optional legacy modes:

- `USE_KEY_WHEN_SNEAKING`
- `USE_KEY_ALWAYS`

They convert only the OFF_HAND pass of NeoForge's use-input pipeline. Main-hand item and entity interactions retain priority. The dedicated key remains the recommended mode.

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
