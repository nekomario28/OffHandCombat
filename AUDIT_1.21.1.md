# OffHandCombat 1.21.1 migration audit

Date: 2026-07-26

## Decision

Use the original MIT repository as the clean fork and preserve its history and notices. Do not mechanically update its old Architectury/Forge implementation. Rebuild authority, transport, state and integration surfaces for a single NeoForge 1.21.1 module.

## Original implementation findings

### Vanilla packet mutation

The original appends a custom boolean to `ServerboundInteractPacket` read/write paths. This requires identical mixins on both peers, changes a vanilla wire format and conflicts with other packet mixins.

**Replacement:** versioned NeoForge `CustomPacketPayload`s. The registration is optional so a client/server-only mismatch does not itself fail negotiation; the client sends only after confirming the request channel exists.

### Real stack and live attribute mutation

The original temporarily swaps the selected main-hand and off-hand stacks and removes/adds modifiers on the live player `AttributeMap`. An exception or another mod's event can observe or preserve an invalid intermediate state, misattribute durability/enchantments, or double-fire equipment hooks.

**Replacement:** real inventory is never changed. A copied attribute view swaps only modifier interpretation, while narrowly scoped mixins redirect attack reads to the off-hand stack during one authoritative `Player.attack` call.

### Static UUID state

The old approach retains player state in static maps without a complete logout/death/dimension lifecycle contract.

**Replacement:** a non-serialized NeoForge Data Attachment lives on each Player instance. It is not copied on death, so respawn begins with clean cooldown and replay state. It disappears with the entity/session without manual UUID cleanup.

### Hurt-immunity reset

RLO-style alternating attacks clear `invulnerableTime` and `lastHurt` to force a second damage application.

**Rejected:** this bypasses vanilla damage immunity, effectively doubles attack throughput, breaks PvP and modded damage balance, and risks duplicate knockback/death ordering. An off-hand request is reported as successfully executed when the vanilla attack path ran once; unchanged health during immunity remains valid vanilla behavior.

### Early right-click interception

Injecting at the head of `Minecraft.startUseItem` steals shield, bow, crossbow, food, potion, door, container, trading and mounting interactions.

**Replacement:** a dedicated key is the default. Optional legacy modes observe NeoForge's per-hand input event and only cancel after an off-hand request is actually sent. External arbitration rules can deny conversion.

### Brittle animation redirects

The original redirects numerous `LivingEntity.swing` fields by ordinal and patches renderer internals. This is highly sensitive to optimization and animation mods.

**Replacement:** the first stable port uses vanilla `swing(OFF_HAND, true)`. Independent simultaneous arm rendering is deferred until it can be isolated and tested.

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

## Residents integration decision

This fork is not a required Civitas Residents dependency. It is player-input infrastructure, while Residents owns non-player entity execution and must remain functional when this mod is absent or incompatible.

A future optional adapter may use only the public API. It must not reference Mixin classes, attachment internals or static registries of transient state.

## Resolved design requirements

- `OffhandAttackRequest`, `OffhandAttackContext`, `OffhandAttackResult` public records.
- server-side hand/target/readiness validation;
- main/off readiness query;
- eligibility and compatibility registries;
- cancellable Before and observable After events;
- item-use arbitration registry;
- item/enchantment tags;
- protocol version negotiation;
- stable failure status wire IDs;
- sequence-based at-most-once execution and duplicate result replay.

## Remaining release risks

- Exact Mixin targets must pass compile and runtime launch on NeoForge 21.1.242.
- Full combat replacements such as Better Combat and Combatify need real pack tests and likely adapters.
- Modded weapons whose damage bypasses vanilla `Player.attack` need explicit eligibility/execution adapters rather than deeper generic mixins.
- Critical, sweep and durability attribution require Minecraft integration tests, not only source inspection.
