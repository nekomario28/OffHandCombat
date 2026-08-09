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
- Vanilla `LivingEntity.swing` drives cross-hand cooldown semantics, including empty-air off-hand swings.
- Vanilla off-hand swing animation is used for both targeted attacks and empty-air swings.
- The independent off-hand cooldown is displayed through Minecraft's configured hotbar or crosshair attack indicator.
- Use-capable items can alternate MAIN_HAND → OFF_HAND through the normal vanilla use path during a short, bounded post-release window.

## Controls

Put an eligible melee weapon in the off hand and right-click. A targeted entity receives the server-authoritative off-hand attack. Pointing at true empty air performs an off-hand swing without sending an attack request or consuming durability; the swing resets the independent off-hand cooldown and, where Minecraft exposes miss-time behavior, arms the normal short survival miss throttle before the bar recharges.

The default client mode is `USE_KEY_ALWAYS`. It converts only the `OFF_HAND` pass of NeoForge's use-input pipeline. Normal main-hand item, block and entity interactions retain priority. Holding Sneak is the default escape hatch: while crouching, `USE_KEY_ALWAYS` leaves vanilla use behavior alone instead of converting the off-hand pass into an attack.

`USE_KEY_WHEN_SNEAKING` remains available as an alternate compatibility mode that permits the right-click off-hand attack only while sneaking. The mod does not register a dedicated off-hand attack key.

For active-use items, releasing a MAIN_HAND use records a three-tick alternation window. If both hands contain use-capable items, an immediate second right-click may be replayed safely through Minecraft's normal `gameMode.useItem(..., OFF_HAND)` path. CI currently exercises shield, bow, crossbow and trident MAIN_HAND → OFF_HAND alternation and verifies that this path does not emit an Off Hand Combat attack payload.

The off-hand cooldown indicator follows Minecraft's **Attack Indicator** video setting:

- **Hotbar:** appears on the opposite side from the main-hand indicator.
- **Crosshair:** appears below the main-hand indicator and includes the vanilla-style full-charge marker.
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

Automated checks cover source invariants, protocol wire IDs, sequence replay classification, cooldown mathematics, swing-driven cross-hand cooldown behavior, empty-air reset/miss-throttle behavior, bounded active-use alternation and the independent cooldown HUD. Minecraft integration and compatibility cases are tracked in `TEST_MATRIX.md`; no release should be published until its release-gate section is complete.
