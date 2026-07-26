# Network protocol v2

## Registration

Both play-phase payloads use NeoForge's `PayloadRegistrar` under protocol version `2` and are optional.

- C2S `offhandcombat:offhand_attack_request`
- S2C `offhandcombat:offhand_attack_result`

The client checks the negotiated request channel before sending. Thus a client-only installation leaves vanilla input untouched on a server without this mod.

## Request

- positive monotonically increasing `sequence` (VarLong)
- target entity ID (VarInt)

The server classifies the sequence before validation:

- latest repeated value: return cached result, never execute again;
- lower value: `STALE_SEQUENCE`;
- zero/negative: `INVALID_SEQUENCE`;
- higher value: one new validation/execution attempt.

A newly accepted sequence is at-most-once even when validation fails or it is rate-limited. Its result is cached for duplicate replay.

## Result

The result includes sequence, target ID, stable status wire ID, target health before/after, off-hand durability before/after and server game time.

`SUCCESS` means the authoritative vanilla attack function executed once. It does not promise health loss: vanilla invulnerability or another mod may correctly reject damage.

## Versioning

Status values use explicit numeric wire IDs rather than enum ordinals. Any incompatible field or semantic change must increment `PROTOCOL_VERSION`.
