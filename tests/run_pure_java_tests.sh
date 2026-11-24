#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/storage/AtomicFileStore.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/config/PresentationMigrations.java" \
  "$ROOT/tests/PresentationMigrationsSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/model/TexturePayload.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/diagnostics/DiagnosticsService.java" \
  "$ROOT/tests/DiagnosticsSmoke.java" \
  "$ROOT/tests/TexturePayloadSmoke.java" \
  "$ROOT/tests/AtomicFileStoreSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/relicario/RelicarioRules.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/relicario/CollectionMilestones.java" \
  "$ROOT/tests/CollectionMilestonesSmoke.java" \
  "$ROOT/tests/RelicarioRulesSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/core/CombatRules.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/core/AsterionComboRules.java" \
  "$ROOT/tests/AsterionComboSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/systems/SlayerContractRules.java" \
  "$ROOT/tests/SlayerContractSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/systems/JobsContractRules.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/systems/SupplyOrderRules.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/systems/MinerWorkshopRules.java" \
  "$ROOT/tests/MinerWorkshopSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/mining/MithrilRules.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/mining/MiningLuckRules.java" \
  "$ROOT/tests/MiningLuckSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/mining/AmethystResonanceRules.java" \
  "$ROOT/tests/AmethystResonanceSmoke.java" \
  "$ROOT/tests/MithrilRulesSmoke.java" \
  "$ROOT/tests/SupplyOrderRulesSmoke.java" \
  "$ROOT/tests/JobsContractSmoke.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/core/Cooldowns.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/cooking/CookingTiming.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/cooking/CookingBatch.java" \
  "$ROOT/src/main/java/dev/hydev/spectrecore/cooking/CookingToolActions.java" \
  "$ROOT/tests/CookingToolsSmoke.java" \
  "$ROOT/tests/CombatRulesSmoke.java" \
  "$ROOT/tests/CooldownsSmoke.java" \
  "$ROOT/tests/CookingTimingSmoke.java" \
  "$ROOT/tests/CookingBatchSmoke.java"
for cls in MiningLuckSmoke MinerWorkshopSmoke AmethystResonanceSmoke MithrilRulesSmoke SupplyOrderRulesSmoke CollectionMilestonesSmoke RelicarioRulesSmoke PresentationMigrationsSmoke DiagnosticsSmoke TexturePayloadSmoke AtomicFileStoreSmoke CombatRulesSmoke AsterionComboSmoke SlayerContractSmoke JobsContractSmoke CooldownsSmoke CookingTimingSmoke CookingToolsSmoke CookingBatchSmoke; do java -ea -cp "$TMP" "$cls"; done
