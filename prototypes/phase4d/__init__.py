"""Finite synthetic half sequences; no game-end or league-rule adoption."""
from types import MappingProxyType

DESIGN = "phase4d-design-v0.1"
SCHEMA = "phase4d-reference-v0.1"
APPROVED_GAME_MAIN = "b9f9b5830f9247691b4d05b0f81ad8fe84c12f13"
DEPENDENCY_SOURCES = ("prototypes/__init__.py", "prototypes/phase4a/__init__.py", "prototypes/phase4a/model.py",
                      "prototypes/phase4b/__init__.py", "prototypes/phase4b/model.py",
                      "prototypes/phase4c/__init__.py", "prototypes/phase4c/model.py")
CAPABILITIES = MappingProxyType({
    "source_kind": "synthetic_fixture", "scope": "partial_half_sequence_controller",
    "connected_to_phase4a": True, "connected_to_phase4b": True, "connected_to_phase4c": True,
    "connected_to_statcast": False, "connected_to_phase3d": False, "probability_model": False,
    "game_end_rules_implemented": False, "official_stats_generated": False,
    "ability_calibrated": False, "android_integrated": False, "product_adoption": False,
})
