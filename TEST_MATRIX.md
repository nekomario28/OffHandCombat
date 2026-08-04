# Off Hand Combat 1.21.1 test matrix

Legend: **A** automated in CI, **G** GameTest/integration automation, **R** researched compatibility policy, **M** manual modpack test.

## Release gate

- [x] **A** Static audit rejects vanilla packet mutation, hurt-immunity reset, live inventory swapping, custom reach approximation and static UUID state.
- [x] **A** Java 21 `clean test build` succeeds against NeoForge 21.1.242.
- [x] **A** Distributable JAR contains the MIT license, NeoForge metadata, Mixin config, main class, selected project icon and English/Japanese language files.
- [x] **A** Physical client reaches its first client tick under Xvfb.
- [x] **G** A copied dedicated-server world is opened through Minecraft's world-open flow on an integrated client/server.
- [x] **A** Dedicated server reaches `Done` without client-class loading or Mixin failure.
- [x] **G** GameTest Server discovers and passes ten required off-hand combat tests.
- [x] **G** A real client right-click/use-key action crosses the custom-payload channel to the integrated server and produces exactly one damage/durability result.
- [x] **G** A real client right-click on a true empty-air `MISS` plays the OFF_HAND swing while sequence, durability, authoritative result and cooldown remain unchanged.
- [x] **G** Two separate Xvfb client processes connect through vanilla `ConnectScreen` to one separately launched Dedicated Server; each performs an independent sequence-1 right-click/use-key attack and duplicate replay without a second effect.
- [x] **G** One physical Xvfb client completes an actual disconnect/reconnect, death/respawn and Overworld-to-Nether transition against a separate Dedicated Server while replay and durability state follow the lifecycle contract.
- [x] **G** A physical Xvfb client and separate Dedicated Server complete invalid-sequence, duplicate-flood, reordered-sequence and unique-sequence burst trials over loopback with `netem` delay `120ms ± 20ms`; execution remains exactly once and recovers after the burst.
- [x] **G** The default `USE_KEY_ALWAYS` mode performs one right-click off-hand attack against a targeted entity.
- [x] **G** Shield, bow, food, potion, button, door, chest and villager trading retain priority under physical right-click/vanilla interaction input; no custom sequence or off-hand durability is consumed.
- [ ] **M** Vanilla hurt immunity remains unchanged under alternating rapid physical input.

## Protocol and lifecycle

- [x] **A** Sequence 0/negative is invalid.
- [x] **A** Repeated latest sequence is classified duplicate.
- [x] **A** Lower sequence is stale and a higher sequence advances the window.
- [x] **A** Failure status wire IDs are unique and stable round trips.
- [x] **G** Duplicate payload replays the cached result without a second attack or durability change.
- [x] **G** Integrated and both separate-process clients receive their own cached result after duplicate replay while authoritative health/durability remain unchanged.
- [x] **G** A fresh sequence in one rate-limit window is rejected without execution.
- [x] **G** A NeoForge client with Off Hand Combat connects through vanilla `ConnectScreen` to the Mojang vanilla 1.21.1 server; the request channel remains absent, the vanilla OFF_HAND use pass is not canceled, right-click creates no custom sequence or result, and the connection remains stable.
- [x] **G** A completely vanilla Mojang 1.21.1 client joins a NeoForge Dedicated Server with Off Hand Combat installed, remains connected, and requires no client mod or custom channel.
- [x] **G** An actual disconnect followed by vanilla `ConnectScreen` reconnect creates fresh server/client transient state; the next accepted attack starts at sequence `1` with durability `0 → 1`.
- [x] **G** The reconnect harness waits for the authoritative initial result to settle on the server before disconnecting, preventing a client/server phase race without replacing the real disconnect/reconnect path.
- [x] **G** An actual `/kill`, client death screen and vanilla respawn request create fresh client/server transient state; the next accepted attack starts at sequence `1` with durability `0 → 1`.
- [x] **G** An actual Overworld-to-Nether transition preserves the active client/server replay anchor and off-hand stack state; the next attack advances to sequence `2` with durability `1 → 2` without duplication or loss.
- [x] **G** Under `120ms ± 20ms` loopback delay, 64 duplicate sequence-1 requests replay one cached result, sequence `3` executes before delayed sequence `2`, sequence `2` is stale, a unique burst `5–68` is rate-limited without extra damage/durability, and sequence `69` subsequently executes once.

## Cross-hand cooldown

- [x] **A** Vanilla-style full cooldown ticks are calculated from attack speed.
- [x] **A** Invalid attack speed produces zero readiness/cap.
- [x] **A** A client cooldown reset sequence is applied once; duplicate SUCCESS replay cannot restart the bar.
- [x] **G** main → off applies the configured opposite-hand cap once using off-hand attack speed.
- [x] **G** off → main applies the configured opposite-hand cap once using main-hand attack speed.
- [x] **G** A unique authoritative SUCCESS resets the client off-hand cooldown ticker; duplicate cached results retain the same reset anchor across dimension clone.
- [x] **G** Two same-tick use-key input-path requests are emitted through the production client sender; the first executes once, the second returns `RATE_LIMITED`, and health/durability remain synchronized.
- [x] **G** swapping the off-hand stack resets off-hand readiness while an unchanged stack continues charging.
- [x] **A** The off-hand cooldown HUD uses Minecraft's configured HOTBAR or CROSSHAIR indicator position, hides when the setting is OFF and is absent when the server has no Off Hand Combat channel.

## Attribution and exactly-once effects

- [x] **G** attack damage and attack speed are sourced from the off-hand weapon.
- [x] **G** a grounded normal off-hand sword hit is compared with an airborne critical; the critical applies the vanilla approximately `1.5×` multiplier exactly once, consumes off-hand durability once and leaves main-hand durability unchanged.
- [x] **G** with an axe in the main hand and a sword in the off hand, the off-hand attack applies primary sword damage, one vanilla sweep hit to a nearby entity, no hit outside the sweep area and one off-hand durability use.
- [x] **G** an accepted off-hand attack changes off-hand durability exactly once.
- [x] **G** an immediate attack rejected by vanilla hurt immunity does not consume durability again.
- [x] **G** Fire Aspect I and Knockback I on the off-hand sword apply one vanilla fire duration and one knockback impulse while the main-hand axe contributes neither hook nor durability loss.
- [x] **G** a test-only NeoForge `AttackEntityEvent` hook fires exactly once for an accepted off-hand attack and `getWeaponItem()` exposes the exact off-hand `ItemStack` while the hook runs.
- [x] **G** main-hand durability remains unchanged during an off-hand attack.
- [x] **G** the live AttributeMap object and main-hand attack damage/speed values are identical before and after execution; the copied off-hand view is cleared.
- [x] **G** vanilla invulnerability is preserved; a SUCCESS result may correctly show unchanged health.
- [x] **A** an After-event listener failure cannot rewrite an already-executed attack as `INTERNAL_ERROR`; the failure is logged and the executed result is preserved.

## Input priority

- [x] **A** the client config defaults to `USE_KEY_ALWAYS`, making right-click the standard off-hand attack input.
- [x] **G** the right-click mode converts only the `OFF_HAND` pass and cancels only when an off-hand request is sent or a true empty-air off-hand swing starts.
- [x] **A** no dedicated off-hand attack key is registered.
- [x] **G** a right-click/use-key action produces no request, result, damage or durability change while a Screen is open.
- [x] **G** a true empty-air `MISS` produces an OFF_HAND animation without a request, result, durability use or cooldown reset.
- [x] **G** shield priority.
- [x] **G** bow use priority.
- [ ] **M** crossbow and trident use.
- [x] **G** food and potion use.
- [x] **G** door, button and chest interaction.
- [ ] **M** lever and other block interaction.
- [x] **G** villager trading interaction.
- [ ] **M** feeding/taming, mounting and other entity interaction.
- [x] **G** a registered input-arbitration rule can deny a real right-click conversion without advancing sequence state or changing target health/durability; replacing the same rule ID with `PASS` restores normal attack execution.

## Server validation

- [x] **G** self and removed target IDs are rejected.
- [x] **G** dead target IDs are rejected.
- [x] **G** spectator/unavailable player is rejected.
- [x] **G** the public Entity API rejects null, unregistered and foreign-Level instances before resolving their Level-local numeric ID.
- [x] **G** vanilla `Player.canInteractWithEntity` entity reach is enforced.
- [x] **G** line of sight is enforced when enabled.
- [x] **G** an ineligible off-hand item is rejected server-side.
- [x] **G** a GameTest-only datapack places Sharpness in the enchantment blacklist; the authoritative service returns `INELIGIBLE_WEAPON` with no damage or durability use, while the production blacklist remains empty in the distributable JAR.

## Multiplayer observation

- [x] **G** two separate-process remote clients join one Dedicated Server concurrently over real socket transport with distinct usernames and isolated game directories.
- [x] **G** both clients observe both one-time target-health results through world synchronization while each client retains only its own result payload.
- [x] **G** client A's duplicate replay leaves client B at sequence `0`, no cached result and durability `0`; client B then begins independently at sequence `1`, and both per-player Data Attachment objects remain distinct.
- [x] **G** controlled loopback latency/reordering and burst traffic produce no duplicate execution: target health and off-hand durability remain unchanged through duplicate, stale and rate-limited requests.

## Compatibility packs

- [x] **R** Better Combat is declared `discouraged`: its upstream compatibility policy identifies dual wield, reach, attack timing/cooldown and attack-key modifications as semantic conflicts.
- [x] **R** Combatify is declared `discouraged`: it replaces the vanilla combat model and has no Off Hand Combat authority adapter.
- [ ] **M** a modded weapon using vanilla attack modifiers.
- [ ] **M** a representative third-party weapon with custom damage hooks.
- [ ] **M** attribute/quality/rarity mod; bonuses remain after attack and reconnect.
- [ ] **M** Curios/accessory attributes remain unchanged.
- [x] **G** a NeoForge `LivingEquipmentChangeEvent` observer receives zero notifications during the accepted off-hand attack, and both real hand-stack identities remain in their original slots afterward.
- [ ] **M** a representative inventory/equipment event mod receives no synthetic swap events.
- [ ] **M** performance/animation Mixin mod has no startup conflict or hand tremor.

Any incompatibility discovered here must become either a tagged exclusion, a public compatibility rule/adapter, a NeoForge metadata warning/block, or explicit documentation before release.
