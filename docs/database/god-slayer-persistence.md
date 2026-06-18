# GOD Slayer persistence and database impact

## Final schema impact

The GOD Slayer award requires no database schema change. It reuses fields that already exist on `cof_user_stats`, so no Flyway migration is required and no SQL migration file should be added for this feature.

There are no new tables, columns, indexes, constraints, or data migrations. Deploying the final Task 2 implementation against an existing supported database leaves the schema unchanged.

## Reused `cof_user_stats` fields

| Field | Meaning |
| --- | --- |
| `god_defeated_at` | Permanent GOD Slayer title marker. A non-null value records when the first qualifying win was persisted. It is not cleared when the reward is acknowledged. |
| `god_reward_game_id` | Pending reward acknowledgement marker. A non-null value identifies the qualifying game whose reward still needs acknowledgement. Task 3 will clear this field after acknowledgement. |
| `defeated_computers` | Existing JSON object containing per-computer defeat counts. The first qualifying GOD Slayer grant increments the `computer_god` entry. Non-GOD computer counting keeps its existing behavior. |

No separate acknowledgement timestamp is stored. In particular, there is no `god_reward_acknowledged_at` field in the final model or schema.

## Grant state transition

Before a player has earned GOD Slayer, both reward fields are null:

```text
god_defeated_at = null
god_reward_game_id = null
```

When `UserStatsService.recordFinishedGame` persists the first qualifying win, it performs the following transition:

```text
god_defeated_at = finished game timestamp
god_reward_game_id = qualifying game ID
defeated_computers["computer_god"] += 1
```

At the same time, the in-memory game records `godSlayerAwardWinnerId`, and the winning player receives `godSlayer = true`. If `god_defeated_at` is already non-null, Task 2 does not replace either persisted reward field, create another game-level award marker, or increment the GOD defeat count again.

The existing match-history uniqueness and `statsSaved` guards remain the persistence-level protection against repeated save or tick processing of the same game.

## Future acknowledgement transition

Task 3 will acknowledge the pending reward by clearing only `god_reward_game_id`:

```text
god_defeated_at = original first-award timestamp
god_reward_game_id = null
```

That transition means the reward has been acknowledged while the permanent title remains earned. Task 2 does not add the acknowledgement endpoint or clear the field.

The resulting states are:

| `god_defeated_at` | `god_reward_game_id` | Interpretation |
| --- | --- | --- |
| null | null | GOD Slayer has not been earned. |
| non-null | non-null | GOD Slayer is permanent and reward acknowledgement is pending. |
| non-null | null | GOD Slayer is permanent and no reward acknowledgement is pending. |

The inconsistent state `god_defeated_at = null` with a non-null `god_reward_game_id` is not produced by Task 2.

## Catalog V10 isolation

This persistence model does not affect the Catalog V10 rebuild. It does not read or modify `cof_deck`, `cof_pmv`, `cof_card`, their pending review fields, approval rules, or the renamed `old_cof_*` tables. Library IDs are evaluated from the finished game's effective settings, but no catalog row or catalog schema is changed.

## Compatibility and rollback

- Existing users remain compatible. Rows with both fields null behave as never awarded; rows with `god_defeated_at` already populated retain the permanent title and are not awarded again.
- Existing rows with non-null `god_defeated_at` and null `god_reward_game_id` are treated as having the title with no pending acknowledgement.
- Application rollback requires no data conversion because all persisted values use pre-existing columns. Older code can continue reading the same `cof_user_stats` table.
- The intermediate Task 2 commit that introduced V11 should not be deployed. The final follow-up removes both V11 files before review and deployment.
- If the removed V11 migration was already applied in an environment, that environment is not in the intended zero-schema-impact state. Coordinate a controlled database rollback: restore or reconcile Flyway history using the environment's established DBA procedure, and optionally drop the unused nullable `god_reward_acknowledged_at` column after confirming no deployed code references it. Do not deploy the final artifact while Flyway history still expects a migration that is no longer present.

For the intended deployment path, no Flyway action, schema rollback, or catalog migration is required.
