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

**Replacement:** right-click is the default through NeoForge's per-hand input event rather than an early `Minecraft.startUseItem` injection. Conversion occurs only on the `OFF_HAND` pass and cancels vanilla use only after an off-hand request is actually sent, so normal main-hand item and entity interactions retain priority. The dedicated `V` key remains an optional `KeyConflictContext.IN_GAME` fallback, and the request path rejects input while a `Screen` is open. External arbitration rules can deny conversion.

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

Clean CI run `30362851049` (`Build and audit` run #360) for branch head `4e52aace2a30dffb06c974af79bdd65d0edbffdb` and PR merge ref `f0990a5bac0a28d2364030ed05931d01dc3884b6` completed:

- source SHA-256 manifest and static architecture/legal/metadata audit;
- Java 21 `clean test build` against NeoForge 21.1.242;
- dedicated server startup through `Done`;
- ten required NeoForge GameTests;
- a GameTest-only Sharpness blacklist datapack causes the authoritative service to reject the enchanted off-hand sword with `INELIGIBLE_WEAPON`, without target damage or durability use;
- the production enchantment blacklist remains empty and all GameTest resources/classes remain absent from the distributable JAR;
- grounded normal and airborne critical off-hand sword attacks are compared: the critical applies the vanilla approximately `1.5×` multiplier once, consumes one off-hand durability and leaves main-hand durability unchanged;
- with an axe in the main hand and a sword in the off hand, the primary target receives off-hand sword damage, one nearby target receives one vanilla sweep hit, an out-of-area target remains unchanged and only the off-hand sword loses durability;
- Fire Aspect I and Knockback I attached only to the off-hand sword apply one vanilla fire duration and one knockback impulse while the main-hand axe contributes neither hook nor durability loss;
- a test-only NeoForge `AttackEntityEvent` hook fires exactly once for the accepted off-hand attack and its `getWeaponItem()` call observes the exact off-hand `ItemStack`;
- a test-only `LivingEquipmentChangeEvent` observer receives zero notifications during attack execution, while the real main-hand and off-hand stack identities remain in their original slots;
- physical Xvfb client startup;
- copied-world integrated-client load;
- GUI input suppression with a test target isolated from random world damage before the authoritative attack;
- a registered highest-priority input-arbitration `DENY` rule suppresses a real dedicated-key action without request, result, sequence advance, target-health change or durability use;
- replacing the same arbitration rule ID with `PASS` restores the normal real dedicated-key client-to-integrated-server request, authoritative result and duplicate replay;
- the CI harness downloads the Mojang vanilla 1.21.1 server through Mojang's version manifest, verifies the manifest-provided server SHA-1, and keeps the server JAR outside the repository and uploaded evidence artifact;
- a NeoForge client with Off Hand Combat connects through vanilla `ConnectScreen` to that Mojang vanilla server over `127.0.0.1:25568` as `OHCVanillaPeer`;
- the vanilla peer advertises no off-hand request channel, the real OFF_HAND vanilla-use pass remains uncanceled, the dedicated off-hand key sends no payload and advances no client sequence, no result is received, and the connection remains stable for the observation window;
- one separately launched Dedicated Server and two simultaneous Xvfb client processes connect through vanilla `ConnectScreen` over `127.0.0.1:25565` with distinct usernames and game directories;
- client A sends sequence `1`, reduces its target from `10.0` to `4.0`, consumes one off-hand durability and completes duplicate replay while client B remains at sequence `0`, no cached result and durability `0`;
- only after client A's replay remains stable does the server arm client B; client B then independently begins at sequence `1`, reduces its own target from `10.0` to `4.0` and consumes one off-hand durability;
- both clients observe both final target-health values through world synchronization while retaining only their own result payload;
- both per-player Data Attachment instances remain distinct and both duplicate replays cause no second health or durability change;
- the lifecycle client waits twenty client ticks after receiving the initial authoritative result so the server harness records its next phase before the real disconnect, removing a test-phase race without replacing the disconnect/reconnect path;
- a separate physical Xvfb client connects to a separate Dedicated Server over `127.0.0.1:25566`, attacks at sequence `1`, disconnects and reconnects through vanilla `ConnectScreen`, then attacks from fresh state at sequence `1` and durability `0 → 1`;
- the same client undergoes an actual `/kill`, displays the death screen, sends the vanilla respawn command, receives fresh client/server transient state and attacks at sequence `1` and durability `0 → 1`;
- an actual Overworld-to-Nether transition preserves the active server replay result and copies only the client sequence/result anchor to the replacement `LocalPlayer`; the next attack completes at sequence `2` and durability `1 → 2`;
- a third physical Xvfb client connects to a separate Dedicated Server over `127.0.0.1:25567` while loopback `netem` applies `120ms ± 20ms` delay;
- sequence `0` and `-1` are rejected, sequence `1` executes once, and a 64-request duplicate sequence-1 flood replays the cached result without another health or durability change;
- sequence `3` executes before intentionally delayed sequence `2`; sequence `2` is stale and duplicate sequence `3` returns the cached result;
- invalid-target sequence `4` is rejected, unique sequences `5–68` are processed as a rate-limited burst without an extra effect, and sequence `69` subsequently executes exactly once with durability advancing from `2` to `3`;
- production JAR required-entry, compatibility-metadata and all test-code exclusion audit;
- no fatal Mixin, mod-loading or out-of-memory signatures in the audited server/client, vanilla-peer, multiplayer, lifecycle or network-stress logs.

Generated JAR SHA-256:

`9ae7332e8a6d5ecf0728fcd2b8050bf93d23237f926b5899d1957bafb8aa248d`

CI evidence artifact ZIP SHA-256:

`9d15329f31186df5bb78617ad8d73c1940bbb1a5963fc3ee75102a5c58c019aa`

## Remaining release risks

- Server-only installation still needs an actual vanilla client connection test; a NeoForge peer without Off Hand Combat may be used as an additional mismatch test but must not be mislabeled as the vanilla-client gate.
- Physical right-click input still needs default-mode verification for shield, ranged weapon, food/potion, block and entity interaction priority; the optional sneaking and dedicated-key alternatives need representative checks as well.
- Better Combat, Combatify, representative modded weapons, accessory attributes and animation/performance Mixins need concrete adapters or representative modpack tests.
- Standard NeoForge attack-hook visibility and zero synthetic equipment-change events are automated, but actual third-party weapon and equipment-observer mods still require external fixtures before compatibility can be claimed.
