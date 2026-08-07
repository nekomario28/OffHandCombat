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
- Right-click is the off-hand attack input; no separate attack key is registered.
- Vanilla off-hand swing animation is used for both targeted attacks and empty-air swings.
- The independent off-hand cooldown is displayed through Minecraft's configured hotbar or crosshair attack indicator.

## Controls

Put an eligible melee weapon in the off hand and right-click. A targeted entity receives the server-authoritative off-hand attack; pointing at empty air plays the off-hand swing animation without sending an attack request, consuming durability or resetting cooldown.

The default client mode is `USE_KEY_ALWAYS`. It converts only the `OFF_HAND` pass of NeoForge's use-input pipeline. Normal main-hand item, block and entity interactions retain priority. A targeted attack is canceled from vanilla use only after an off-hand request is sent, while a true empty-air miss is consumed after the off-hand swing begins.

`USE_KEY_WHEN_SNEAKING` is available as a compatibility mode that permits the same right-click attack only while sneaking. The mod does not register a dedicated off-hand attack key.

The off-hand cooldown indicator follows Minecraft's **Attack Indicator** video setting:

- **Hotbar:** appears on the opposite side from the main-hand indicator.
- **Crosshair:** appears below the main-hand indicator.
- **Off:** neither indicator is shown.

The indicator is shown only for an eligible off-hand weapon while connected to a server that supports Off Hand Combat.

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

Automated checks cover source invariants, protocol wire IDs, sequence replay classification, cooldown mathematics, empty-air input behavior and the independent cooldown reset window. Minecraft integration and compatibility cases are tracked in `TEST_MATRIX.md`; no release should be published until its release-gate section is complete.
