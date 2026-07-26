# OffHandCombat 1.21.1 migration audit

Date: 2026-07-27

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

**Replacement:** a non-serialized NeoForge Data Attachment lives on each Player instance. It is not copied on death, so respawn begins with clean cooldown and replay state. It disappears with the entity/session without manual UUID cleanup.

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

For branch head `333e4bd52449dfa667e081ca0e5a01dd2fa277c0`, clean CI completed:

- source SHA-256 manifest and static architecture/legal/metadata audit;
- Java 21 `clean test build` against NeoForge 21.1.242;
- dedicated server startup through `Done`;
- ten required NeoForge GameTests;
- physical Xvfb client startup;
- copied-world integrated-client load;
- GUI input suppression;
- real dedicated-key client-to-server request and server-to-client result payloads;
- duplicate sequence cached replay with no second health or durability change;
- production JAR required-entry, compatibility-metadata and test-code exclusion audit.

Generated JAR SHA-256:

`4693a43a8a1e2366fb02c295e7a7cd079b341216aa15e8498e34b69926c2c61b`

CI artifact ZIP SHA-256:

`4dab75317bd3be97b91d7a5336b217f188c7fda5e9ff6765bc00cf1f2f39aee5`

## Remaining release risks

- A remote client against a separately launched dedicated server and two-client observation remain unverified.
- Reconnect, actual respawn and actual dimension-transition lifecycle require multi-process or physical integration tests.
- Latency/reordering and packet-spam trials require controlled network conditions.
- Physical shield, ranged weapon, food/potion, block and entity interaction priority remains necessary for opt-in legacy input modes.
- Better Combat, Combatify, modded weapon hooks, accessory attributes and animation/performance Mixins need concrete adapters or representative modpack tests.
- Critical, sweep, knockback, fire-aspect and custom hook attribution require further integration tests.
