# OffHandCombat 1.21.1 migration audit

Date: 2026-07-28

## Decision

Use the original MIT repository as the clean fork and preserve its history and notices. Do not mechanically update its old Architectury/Forge implementation. Rebuild authority, transport, state and integration surfaces for a single NeoForge 1.21.1 module.

## Original implementation findings

### Vanilla packet mutation

The original appends a custom boolean to `ServerboundInteractPacket` read/write paths. This requires identical mixins on both peers, changes a vanilla wire format and conflicts with other packet mixins.

**Replacement:** versioned NeoForge `CustomPacketPayload`s. The registration is optional so a client/server-only mismatch does not itself fail negotiation; the client sends only after confirming the request channel exists.

### Real stack and live attribute mutation

The original temporarily swaps the selected main-hand and off-hand stacks and removes/adds modifiers on the live player `AttributeMap`. An exception or another mod's event can observe or preserve an invalid intermediate state, misattribute durability/enchantments, or double-fire equipment hooks.

**Replacement:** real inventory is never changed. A copied attribute view swaps only modifier interpretation, while narrowly scoped mixins redirect attack reads to the off-hand stack during one authoritative `Player.attack` call. GameTest verifies that the live `AttributeMap` object and main-hand attack damage/speed values are unchanged after execution and that temporary copied state is cleared.

### Static UUID state

The old approach retains player state in static maps without a complete logout/death/dimension lifecycle contract.

**Replacement:** a non-serialized NeoForge Data Attachment lives on each Player instance. Reconnect and death/respawn create fresh transient state and restart client/server network sequences at `1`. A client-side dimension clone copies only the next request sequence and last client result to the replacement `LocalPlayer`; server state remains attached to the continuing `ServerPlayer`. This preserves the active replay anchor across dimension movement without carrying state through logout or death.

### Hurt-immunity reset

RLO-style alternating attacks clear `invulnerableTime` and `lastHurt` to force a second damage application.

**Rejected:** this bypasses vanilla damage immunity, effectively doubles attack throughput, breaks PvP and modded damage balance, and risks duplicate knockback/death ordering. An off-hand request is reported as successfully executed when the vanilla attack path ran once; unchanged health during immunity remains valid vanilla behavior.

### Early right-click interception

Injecting at the head of `Minecraft.startUseItem` steals shield, bow, crossbow, food, potion, door, container, trading and mounting interactions.

**Replacement:** a dedicated key is the default. It is restricted to `KeyConflictContext.IN_GAME`, and the request path explicitly rejects input while a `Screen` is open. Optional legacy modes observe NeoForge's per-hand input event and only cancel after an off-hand request is actually sent. External arbitration rules can deny conversion.

### Brittle animation redirects

The original redirects numerous `LivingEntity.swing` fields by ordinal and patches renderer internals. This is highly sensitive to optimization and animation mods.

**Replacement:** the first stable port uses vanilla `swing(OFF_HAND, true)`. Independent simultaneous arm rendering is deferred until it can be isolated and tested.

## Server authority findings

Reach validation delegates to vanilla `Player.canInteractWithEntity` rather than maintaining a second center-distance approximation. Optional line-of-sight validation is enforced separately.

The public `request(ServerPlayer, Entity)` API validates the exact Entity instance in the player's current `Level` before using its Level-local numeric ID. Null, unregistered and foreign-Level instances return `INVALID_TARGET`, preventing accidental resolution of an unrelated local entity with the same ID.

A failure in `OffhandAttackEvent.After` occurs after attack side effects. It is logged, but the truthful executed `SUCCESS` result is retained so callers do not retry an attack that already happened. Before-event failures still prevent execution and are handled as errors.

## RLOffHandCombat findings

RLO commit history provides valuable regression categories:

- client/server desynchronization;
- off-hand critical attribution;
- off-hand sweeping attribution and sweep area;
- Mixin compatibility;
- item/enchantment blacklist;
- cross-hand cooldown settings.

These categories are incorporated into `TEST_MATRIX.md`.

RLO source is not copied because:

- it retains real ItemStack and live modifier swapping;
- it uses static UUID state;
- it mutates vanilla packet serialization;
- it clears hurt immunity;
- it intercepts use input too early;
- its item validator queries the enchantment registry;
- repository/license metadata conflict between MIT and All Rights Reserved.

## Similar implementation review

Dual Wielding Unbound (MIT) demonstrates useful concepts: a per-player off-hand ticker, an attack-context flag, selective reads inside `Player.attack`, and a copied attribute map. This fork independently adapts those architectural ideas while excluding double-attack and hurt-immunity-reset behavior. Its notice is retained in `THIRD_PARTY_NOTICES.md`.

## Compatibility decision

Better Combat and Combatify are not claimed compatible. Both overlap with combat authority owned by this mod. NeoForge metadata declares both `discouraged` with user-facing reasons, and `docs/COMPATIBILITY.md` records the adapter boundary. Startup coexistence alone is not evidence of gameplay compatibility.

## Residents integration decision

This fork is not a required Civitas Residents dependency. It is player-input infrastructure, while Residents owns non-player entity execution and must remain functional when this mod is absent or incompatible.

A future optional adapter may use only the public API. It must not reference Mixin classes, attachment internals or static registries of transient state.

## Automated evidence

Clean CI run `30293620154` for branch head `7dac3305a67f25adcbda1da7ef3985fa758e7c8e` and PR merge ref `bb9fd2316432f953f10e944fba9ad526825a1b61` completed:

- source SHA-256 manifest and static architecture/legal/metadata audit;
- Java 21 `clean test build` against NeoForge 21.1.242;
- dedicated server startup through `Done`;
- ten required NeoForge GameTests;
- physical Xvfb client startup;
- copied-world integrated-client load;
- GUI input suppression with a test target isolated from random world damage before the authoritative attack;
- a registered highest-priority input-arbitration `DENY` rule suppressed a real dedicated-key action without request, result, sequence advance, target-health change or durability use;
- replacing the same arbitration rule ID with `PASS` restored the normal real dedicated-key client-to-integrated-server request, authoritative result and duplicate replay;
- one separately launched Dedicated Server and two simultaneous Xvfb client processes connected through vanilla `ConnectScreen` over `127.0.0.1:25565` with distinct usernames and game directories;
- client A sent sequence `1`, reduced its target from `10.0` to `4.0`, consumed one off-hand durability and completed duplicate replay while client B remained at sequence `0`, no cached result and durability `0`;
- only after client A's replay remained stable did the server arm client B; client B then independently began at sequence `1`, reduced its own target from `10.0` to `4.0` and consumed one off-hand durability;
- both clients observed both final target-health values through world synchronization while retaining only their own result payload;
- both per-player Data Attachment instances remained distinct and both duplicate replays caused no second health or durability change;
- a separate physical Xvfb client connected to a separate Dedicated Server over `127.0.0.1:25566`, attacked at sequence `1`, disconnected and reconnected through vanilla `ConnectScreen`, then attacked from fresh state at sequence `1` and durability `0 → 1`;
- the same client underwent an actual `/kill`, displayed the death screen, sent the vanilla respawn command, received fresh client/server transient state and attacked at sequence `1` and durability `0 → 1`;
- an actual Overworld-to-Nether transition preserved the active server replay result and copied only the client sequence/result anchor to the replacement `LocalPlayer`; the next attack completed at sequence `2` and durability `1 → 2`;
- a third physical Xvfb client connected to a separate Dedicated Server over `127.0.0.1:25567` while loopback `netem` applied `120ms ± 20ms` delay;
- sequence `0` and `-1` were rejected, sequence `1` executed once, and a 64-request duplicate sequence-1 flood replayed the cached result without another health or durability change;
- sequence `3` executed before intentionally delayed sequence `2`; sequence `2` was stale and duplicate sequence `3` returned the cached result;
- invalid-target sequence `4` was rejected, unique sequences `5–68` were processed as a rate-limited burst without an extra effect, and sequence `69` subsequently executed exactly once with durability advancing from `2` to `3`;
- production JAR required-entry, compatibility-metadata and all test-code exclusion audit;
- no fatal Mixin, mod-loading or out-of-memory signatures in the audited server/client, multiplayer, lifecycle or network-stress logs.

Generated JAR SHA-256:

`9ae7332e8a6d5ecf0728fcd2b8050bf93d23237f926b5899d1957bafb8aa248d`

CI evidence artifact ZIP SHA-256:

`412f79a02e0b9e35d5ff790c6c307c375bdf5f17cd21ca914729e44abf8290ec`

## Remaining release risks

- Physical shield, ranged weapon, food/potion, block and entity interaction priority remains necessary for opt-in legacy input modes.
- Better Combat, Combatify, modded weapon hooks, accessory attributes and animation/performance Mixins need concrete adapters or representative modpack tests.
- Critical, sweep, knockback, fire-aspect and custom hook attribution require further integration tests.
