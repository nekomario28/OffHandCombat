# Off Hand Combat 1.21.1 test matrix

Legend: **A** automated in CI, **G** suitable for GameTest/integration automation, **M** manual modpack test.

## Release gate

- [x] **A** Static audit rejects vanilla packet mutation, hurt-immunity reset, live inventory swapping and static UUID state.
- [x] **A** Java 21 `clean test build` succeeds against NeoForge 21.1.242.
- [x] **A** Distributable JAR contains the MIT license, NeoForge metadata, Mixin config, main class and English/Japanese language files.
- [ ] **M** Client starts and a world loads.
- [ ] **M** Dedicated server starts without client-class loading.
- [ ] **M** One real multiplayer off-hand attack succeeds and produces exactly one durability change.
- [ ] **M** Shield, bow/crossbow, food/potion, block interaction and villager trading retain priority.
- [ ] **M** Vanilla hurt immunity remains unchanged under alternating rapid input.

## Protocol and lifecycle

- [x] **A** Sequence 0/negative is invalid.
- [x] **A** Repeated latest sequence is classified duplicate.
- [x] **A** Lower sequence is stale and a higher sequence advances the window.
- [x] **A** Failure status wire IDs are unique and stable round trips.
- [ ] **G/M** Duplicate payload replays the cached result without a second attack.
- [ ] **G/M** New sequences sent in one rate-limit window are rejected without execution.
- [ ] **M** Client-only installation does not cancel input or disconnect from a vanilla server.
- [ ] **M** Server-only installation accepts vanilla clients and remains idle.
- [ ] **M** Reconnect starts a fresh replay window and has no retained UUID state.
- [ ] **M** Death/respawn starts clean transient state.
- [ ] **M** Dimension movement neither duplicates nor loses active state.
- [ ] **M** Packet spam and sequence replay cannot multiply damage/durability.

## Cross-hand cooldown

- [x] **A** Vanilla-style full cooldown ticks are calculated from attack speed.
- [x] **A** Invalid attack speed produces zero readiness/cap.
- [ ] **G/M** main → off applies configured opposite-hand cap once.
- [ ] **G/M** off → main applies configured opposite-hand cap once.
- [ ] **M** rapid clicks do not desynchronize client and server readiness.
- [ ] **M** swapping the off-hand stack resets off-hand readiness.

## Attribution and exactly-once effects

- [ ] **G/M** attack damage and attack speed are sourced from the off-hand weapon.
- [ ] **G/M** critical attribution uses the off-hand weapon and occurs once.
- [ ] **G/M** sweeping attribution, damage and hit area use the off-hand weapon and occur once.
- [ ] **G/M** durability is changed exactly once on the off-hand item.
- [ ] **G/M** damage, knockback, fire aspect and other enchantment hooks occur exactly once.
- [ ] **G/M** the main-hand stack and live attribute map are identical before/after execution.
- [ ] **G/M** vanilla invulnerability is preserved; a SUCCESS result may correctly show unchanged health.

## Input priority

- [ ] **M** dedicated key works without replacing vanilla use.
- [ ] **M** legacy use-key mode only converts the OFF_HAND pass.
- [ ] **M** shield priority.
- [ ] **M** bow, crossbow and trident use.
- [ ] **M** food and potion use.
- [ ] **M** door, button, lever, chest and other block interaction.
- [ ] **M** trading, feeding/taming, mounting and other entity interaction.
- [ ] **M** a registered input-arbitration rule can deny conversion.

## Server validation

- [ ] **G/M** invalid, removed, dead and self target IDs are rejected.
- [ ] **G/M** spectator/unavailable player is rejected.
- [ ] **G/M** target in another dimension cannot be selected by ID.
- [ ] **G/M** entity interaction range is enforced independently of block reach.
- [ ] **G/M** line of sight is enforced when enabled.
- [ ] **G/M** ineligible item and blacklisted enchantment are rejected server-side.

## Multiplayer observation

- [ ] **M** two clients observe the same one-time swing/damage result.
- [ ] **M** two players maintain independent cooldown and sequence state.
- [ ] **M** high latency/reordering produces no duplicate execution.

## Compatibility packs

- [ ] **M** Better Combat.
- [ ] **M** Combatify.
- [ ] **M** a modded weapon using vanilla attack modifiers.
- [ ] **M** a weapon with custom damage hooks.
- [ ] **M** attribute/quality/rarity mod; bonuses remain after attack and reconnect.
- [ ] **M** Curios/accessory attributes remain unchanged.
- [ ] **M** inventory/equipment event mod receives no synthetic swap events.
- [ ] **M** performance/animation Mixin mod has no startup conflict or hand tremor.

Any incompatibility discovered here must become either a tagged exclusion, a public compatibility rule/adapter, or explicit documentation before release.
