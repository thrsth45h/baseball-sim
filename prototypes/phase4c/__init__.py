"""Explicit synthetic orchestration; existing count/half rule contracts stay frozen."""
from types import MappingProxyType

DESIGN = "phase4c-design-v0.1"
SCHEMA = "phase4c-reference-v0.1"
APPROVED_GAME_MAIN = "06256f8a4f0f8795709f56365f1ea212e2090092"
DEPENDENCY_SOURCES = (
    "prototypes/__init__.py", "prototypes/phase4a/__init__.py", "prototypes/phase4a/model.py",
    "prototypes/phase4b/__init__.py", "prototypes/phase4b/model.py",
)
CAPABILITIES = MappingProxyType({
    "source_kind": "synthetic_fixture", "scope": "partial_pitch_half_orchestration",
    "connected_to_phase4a": True, "connected_to_phase4b": True,
    "connected_to_statcast": False, "connected_to_phase3d": False, "probability_model": False,
    "official_stats_generated": False, "ability_calibrated": False,
    "android_integrated": False, "product_adoption": False,
})
