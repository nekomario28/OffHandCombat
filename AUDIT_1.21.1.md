# OffHandCombat 1.21.1 migration audit

Date: 2026-08-05

## Decision

Use the original MIT repository as the clean fork and preserve its history and notices. Do not mechanically update its old Architectury/Forge implementation. Rebuild authority, transport, state and integration surfaces for a single NeoForge 1.21.1 module.

## Original implementation findings

### Vanilla packet mutation

The original appends a custom boolean to `ServerboundInteractPacket` read/write paths. This requires identical mixins on both peers, changes a vanilla wire format and conflicts with other packet mixins.

**Replacement:** versioned NeoForge `CustomPacketPayload`s. Registration is optional, so a client/server-only mismatch does not itself fail negotiation; the client sends only after confirming the request channel exists.

### Real stack and live attribute mutation

The original temporarily swaps the selected main-hand and off-hand stacks and removes/adds modifiers on the live player `AttributeMap`. An exception or another mod's event can observe or preserve an invalid intermediate state, misattribute durability/enchantments or double-fire equipment hooks.

**Replacement:** real inventory is never changed. A copied attribute view swaps only modifier interpretation, while narrowly scoped mixins redirect attack reads to the off-hand stack during one authoritative `Player.attack` call. GameTests verify that the live `AttributeMap`, hand-stack identities and main-hand attack values are unchanged after execution.

### Static UUID state

The old approach retains player state in static maps without a complete logout, death and dimension lifecycle contract.

**Replacement:** a non-serialized NeoForge Data Attachment lives on each Player instance. Reconnect and death/respawn create fresh transient state. A client dimension clone copies only the active sequence/result and cooldown-reset anchors to the replacement `LocalPlayer`; server state remains attached to the continuing `ServerPlayer`.

### Hurt-immunity reset

Some dual-wield implementations clear `invulnerableTime` and `lastHurt` to force a second damage application.

**Rejected:** this bypasses vanilla damage immunity, changes combat throughput and risks duplicate knockback/death ordering. Off-hand execution calls the vanilla attack path once and preserves vanilla immunity.

### Early right-click interception

Injecting at the head of `Minecraft.startUseItem` steals shield, bow, crossbow, food, potion, block, container, trading and mounting interactions.

**Replacement:** right-click enters through NeoForge's per-hand interaction-key event. Targeted conversion occurs only on the `OFF_HAND` pass and cancels vanilla use only after a request is sent. A true empty-air `MISS` is consumed only after starting the vanilla `OFF_HAND` swing. Block, entity and active-item interactions retain priority. No custom attack key is registered.

### Missing empty-air feedback

The initial NeoForge port processed only `EntityHitResult`, so an eligible off-hand weapon did not visibly swing when the player pointed at empty air.

**Replacement:** an eligible true `MISS` calls `player.swing(InteractionHand.OFF_HAND)` locally. It sends no attack payload, creates no authoritative result, consumes no durability and does not reset the off-hand cooldown. A physical integrated-client E2E verifies all four invariants.

### Missing independent cooldown feedback

The initial port maintained an off-hand attack-strength ticker but neither reset the client copy from authoritative attack results nor rendered it.

**Replacement:** only a newer authoritative `SUCCESS` sequence resets the client ticker. Duplicate or stale delayed results cannot rewind the bar. A client GUI layer renders the independent cooldown with Minecraft's configured attack-indicator mode:

- `HOTBAR`: opposite the main-hand indicator;
- `CROSSHAIR`: below the main-hand indicator;
- `OFF`: hidden.

The layer is also hidden when the server does not advertise the Off Hand Combat request channel or the off-hand stack is ineligible. Integrated-client evidence requires the real render path to emit its one-time render marker. Pixel-perfect placement at every GUI scale remains a visual/manual check rather than an automated claim.

## Server authority findings

Reach validation delegates to vanilla `Player.canInteractWithEntity` instead of maintaining a second center-distance approximation. Optional line-of-sight validation is enforced separately.

The public `request(ServerPlayer, Entity)` API validates the exact Entity instance in the player's current `Level` before using its Level-local numeric ID. Null, unregistered and foreign-Level instances return `INVALID_TARGET`.

A failure in `OffhandAttackEvent.After` occurs after attack side effects. It is logged, but the truthful executed result is retained so callers do not retry an attack that already happened. Before-event failures prevent execution.

## Compatibility decision

Better Combat and Combatify are not claimed compatible. Both overlap with combat authority owned by this mod. NeoForge metadata declares both `discouraged` with user-facing reasons, and `docs/COMPATIBILITY.md` records the adapter boundary. Startup coexistence alone is not gameplay-compatibility evidence.

The mod remains optional on either peer. Automated gates cover a modded NeoForge client against a Mojang vanilla server and a completely vanilla Mojang client against a NeoForge server with Off Hand Combat installed.

## Automated release evidence

The release suite covers:

- SHA-256 source manifest and static architecture, legal and metadata audit;
- Java 21 unit tests and build against NeoForge 21.1.242;
- dedicated-server startup and ten required GameTests;
- physical Xvfb client startup;
- integrated right-click attack, authoritative result and duplicate replay;
- true-MISS `OFF_HAND` air swing with unchanged sequence, durability, result and cooldown;
- actual off-hand cooldown GUI-layer rendering;
- shield, bow, food, potion, button, door, chest and villager-trading priority;
- same-tick request rate limiting;
- both vanilla compatibility directions;
- two remote clients with isolated per-player state;
- reconnect, death/respawn and dimension lifecycle;
- delayed, reordered, duplicate-flood and burst network traffic;
- distributable-JAR required entries and exclusion of all test code/resources.

Focused verification for the user-reported regressions passed before the final complete release run: the physical air-swing marker reported `animation=OFF_HAND, sequence unchanged, durability unchanged, cooldown unchanged`, and the integrated GUI layer emitted the actual cooldown-render marker.

## Remaining release risks

- Pixel-perfect HOTBAR/CROSSHAIR placement still needs visual inspection at representative GUI scales, resolutions and left-handed main-arm settings.
- Crossbow, trident, lever, feeding/taming, mounting and uncommon interaction paths remain representative manual checks.
- Better Combat, Combatify, third-party weapon hooks, accessory attributes and animation/performance Mixins require explicit adapters or representative modpack tests before compatibility can be claimed.
- Vanilla hurt immunity is preserved by design and covered structurally/GameTest-wise; alternating high-speed physical-input feel remains a manual gameplay observation.
