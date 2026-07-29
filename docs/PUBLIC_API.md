# Public API

Use `OffhandCombatApi.get()`; do not access Mixins or attachment classes.

## Requests and results

- `OffhandAttackRequest`
- `OffhandAttackContext`
- `OffhandAttackResult`
- `OffhandAttackStatus`
- `OffhandCombatApi#request`

Requests are executed on the logical server. The service performs the same eligibility, target, reach, line-of-sight and readiness validation used by the network path.

The `request(ServerPlayer, Entity)` overload accepts only the exact Entity instance currently registered in the player's `Level`. A null, removed, unregistered or foreign-Level Entity returns `INVALID_TARGET`. Entity IDs are Level-local and must never be used to transfer an Entity reference across dimensions.

## Readiness

`OffhandCombatApi#getReadiness` returns `HandReadiness(mainHand, offHand)`.

## Events

- `OffhandAttackEvent.Before`: cancellable after validation, before execution. An exception prevents execution and is reported as `INTERNAL_ERROR`.
- `OffhandAttackEvent.After`: emitted after one successful authoritative attack-path execution. If an After listener throws after side effects occurred, the failure is logged and the executed `SUCCESS` result is preserved so callers do not retry an already-applied attack.

## Weapon compatibility

`OffhandCompatibilityRegistry.register(id, priority, rule)` may ALLOW or DENY a stack before default modifier-based detection.

Prefer tags for static data-driven integration and rules for dynamic/mod-specific behavior.

## Input arbitration

`OffhandInputArbitrationRegistry.register(id, priority, rule)` can deny a client conversion for dedicated-key or legacy use-key input. This registry is not a security boundary; the server always validates again.

## Residents adapter boundary

A future Civitas Residents adapter may call the server API and observe events. It must remain optional and must not use player-input classes, network payloads, Mixin interfaces, or attachment state directly.
