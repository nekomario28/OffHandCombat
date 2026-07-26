# Off Hand Combat compatibility policy

Date: 2026-07-27

## Status vocabulary

- **Supported**: covered by an automated or recorded manual compatibility test.
- **Discouraged**: NeoForge may load both mods, but the combination has overlapping authority or unverified semantics and produces a user-facing warning.
- **Incompatible**: NeoForge is instructed not to load the combination.
- **Unknown**: not enough evidence exists to make a stronger statement.

A startup success is not evidence of gameplay compatibility.

## Better Combat

**Status: discouraged; no adapter is currently provided.**

Better Combat includes dual-wield attacks and identifies dual-wield implementations, attack-range changes, attack timing/cooldown changes, attack/mining key-handler changes and player-model changes as semantic incompatibilities.

Off Hand Combat owns a dedicated attack key, off-hand attack timing, cross-hand cooldown state, off-hand attribute attribution and off-hand swing selection. Running both without an adapter would leave more than one combat authority responsible for the same action.

The metadata warning is intentionally not a hard incompatibility. A future adapter may disable Off Hand Combat input/execution while delegating weapon eligibility or API events to Better Combat, but that behavior does not exist yet.

## Combatify

**Status: discouraged; no adapter is currently provided.**

Combatify is a replacement combat model based on Combat Test Snapshot 8c. Off Hand Combat intentionally executes one vanilla `Player.attack` call and derives readiness/cross-hand cooldown from the vanilla 1.21.1 model. Those assumptions are not sufficient to claim correctness under Combatify's changed attack and cooldown rules.

The metadata warning remains soft until a concrete 1.21.1 adapter and regression suite define which mod owns readiness, reach, damage attribution, sweeping, critical hits and animation.

## Ordinary weapon and attribute mods

Weapons that expose normal main-hand attack modifiers are eligible by default. That only establishes discovery, not complete compatibility.

Still required before release:

- custom damage-hook exactly-once behavior;
- enchantment attribution for knockback, fire aspect and sweeping;
- attribute/quality/rarity bonuses before and after an attack and reconnect;
- Curios/accessory-derived attributes;
- absence of synthetic equipment-swap observations;
- animation and performance Mixin coexistence.

Compatibility findings must become a public adapter/rule, a tag exclusion, a metadata warning/block, or an explicit documented limitation.
