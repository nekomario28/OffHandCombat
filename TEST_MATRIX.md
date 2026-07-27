# Off Hand Combat 1.21.1 test matrix

Legend: **A** automated in CI, **G** GameTest/integration automation, **R** researched compatibility policy, **M** manual modpack test.

## Release gate

- [x] **A** Static audit rejects vanilla packet mutation, hurt-immunity reset, live inventory swapping, custom reach approximation and static UUID state.
- [x] **A** Java 21 `clean test build` succeeds against NeoForge 21.1.242.
- [x] **A** Distributable JAR contains the MIT license, NeoForge metadata, Mixin config, main class and English/Japanese language files.
- [x] **A** Physical client reaches its first client tick under Xvfb.
- [x] **G** A copied dedicated-server world is opened through Minecraft's world-open flow on an integrated client/server.
- [x] **A** Dedicated server reaches `Done` without client-class loading or Mixin failure.
- [x] **G** GameTest Server discovers and passes ten required off-hand combat tests.
- [x] **G** A real client dedicated-key action crosses the custom-payload channel to the integrated server and produces exactly one damage/durability result.
- [x] **G** A separate Xvfb client process connects through vanilla `ConnectScreen` to a separately launched Dedicated Server, performs one dedicated-key off-hand attack and replays the same sequence without a second effect.
- [ ] **M** Shield, bow/crossbow, food/potion, block interaction and villager trading retain priority under physical input.
- [ ] **M** Vanilla hurt immunity remains unchanged under alternating rapid physical input.

## Protocol and lifecycle

- [x] **A** Sequence 0/negative is invalid.
- [x] **A** Repeated latest sequence is classified duplicate.
- [x] **A** Lower sequence is stale and a higher sequence advances the window.
- [x] **A** Failure status wire IDs are unique and stable round trips.
- [x] **G** Duplicate payload replays the cached result without a second attack or durability change.
- [x] **G** Both integrated and separate-process remote clients receive the cached result after duplicate replay while authoritative health/durability remain unchanged.
- [x] **G** A fresh sequence in one rate-limit window is rejected without execution.
- [ ] **M** Client-only installation does not cancel input or disconnect from a vanilla server.
- [ ] **M** Server-only installation accepts vanilla clients and remains idle.
- [ ] **M** Reconnect starts a fresh replay window and has no retained UUID state.
- [ ] **M** Death/respawn starts clean transient state.
- [ ] **M** Dimension movement neither duplicates nor loses active state.
- [ ] **M** Packet spam and sequence replay cannot multiply damage/durability under latency/reordering.

## Cross-hand cooldown

- [x] **A** Vanilla-style full cooldown ticks are calculated from attack speed.
- [x] **A** Invalid attack speed produces zero readiness/cap.
- [x] **G** main → off applies the configured opposite-hand cap once using off-hand attack speed.
- [x] **G** off → main applies the configured opposite-hand cap once using main-hand attack speed.
- [ ] **M** rapid physical clicks do not desynchronize client and server readiness.
- [x] **G** swapping the off-hand stack resets off-hand readiness while an unchanged stack continues charging.

## Attribution and exactly-once effects

- [x] **G** attack damage and attack speed are sourced from the off-hand weapon.
- [ ] **G/M** critical attribution uses the off-hand weapon and occurs once.
- [ ] **G/M** sweeping attribution, damage and hit area use the off-hand weapon and occur once.
- [x] **G** an accepted off-hand attack changes off-hand durability exactly once.
- [x] **G** an immediate attack rejected by vanilla hurt immunity does not consume durability again.
- [ ] **G/M** damage, knockback, fire aspect and other enchantment hooks occur exactly once.
- [x] **G** main-hand durability remains unchanged during an off-hand attack.
- [x] **G** the live AttributeMap object and main-hand attack damage/speed values are identical before and after execution; the copied off-hand view is cleared.
- [x] **G** vanilla invulnerability is preserved; a SUCCESS result may correctly show unchanged health.
- [x] **A** an After-event listener failure cannot rewrite an already-executed attack as `INTERNAL_ERROR`; the failure is logged and the executed result is preserved.

## Input priority

- [x] **G** the registered dedicated key triggers a real in-world client → server off-hand attack without replacing vanilla use.
- [x] **G** the dedicated key uses the `IN_GAME` conflict context and produces no request, result, damage or durability change while a Screen is open.
- [ ] **M** legacy use-key mode only converts the OFF_HAND pass.
- [ ] **M** shield priority.
- [ ] **M** bow, crossbow and trident use.
- [ ] **M** food and potion use.
- [ ] **M** door, button, lever, chest and other block interaction.
- [ ] **M** trading, feeding/taming, mounting and other entity interaction.
- [ ] **M** a registered input-arbitration rule can deny conversion.

## Server validation

- [x] **G** self and removed target IDs are rejected.
- [x] **G** dead target IDs are rejected.
- [x] **G** spectator/unavailable player is rejected.
- [x] **G** the public Entity API rejects null, unregistered and foreign-Level instances before resolving their Level-local numeric ID.
- [x] **G** vanilla `Player.canInteractWithEntity` entity reach is enforced.
- [x] **G** line of sight is enforced when enabled.
- [x] **G** an ineligible off-hand item is rejected server-side.
- [ ] **G/M** a blacklisted enchantment is rejected server-side.

## Multiplayer observation

- [x] **G** one separate-process remote client and Dedicated Server agree on sequence, target health and one off-hand durability change after real socket transport.
- [ ] **M** two remote clients observe the same one-time swing/damage result.
- [ ] **M** two remote players maintain independent cooldown and sequence state.
- [ ] **M** high latency/reordering produces no duplicate execution.

## Compatibility packs

- [x] **R** Better Combat is declared `discouraged`: its upstream compatibility policy identifies dual wield, reach, attack timing/cooldown and attack-key modifications as semantic conflicts.
- [x] **R** Combatify is declared `discouraged`: it replaces the vanilla combat model and has no Off Hand Combat authority adapter.
- [ ] **M** a modded weapon using vanilla attack modifiers.
- [ ] **M** a weapon with custom damage hooks.
- [ ] **M** attribute/quality/rarity mod; bonuses remain after attack and reconnect.
- [ ] **M** Curios/accessory attributes remain unchanged.
- [ ] **M** inventory/equipment event mod receives no synthetic swap events.
- [ ] **M** performance/animation Mixin mod has no startup conflict or hand tremor.

Any incompatibility discovered here must become either a tagged exclusion, a public compatibility rule/adapter, a NeoForge metadata warning/block, or explicit documentation before release.
