"""Adjudicated synthetic plays only; no Phase4a adapter or production engine."""
from types import MappingProxyType

DESIGN = "phase4b-design-v0.1"
SCHEMA = "phase4b-reference-v0.1"
APPROVED_GAME_MAIN = "59ae0e2f726b88d5729d026a0e293916c33a73e6"
CAPABILITIES = MappingProxyType({
    "source_kind": "synthetic_fixture", "scope": "partial_base_out_rules",
    "probability_model": False, "connected_to_phase4a": False,
    "connected_to_statcast": False, "connected_to_phase3d": False,
    "official_stats_generated": False, "ability_calibrated": False,
    "android_integrated": False, "product_adoption": False,
})
