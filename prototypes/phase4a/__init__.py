"""Partial count rules for adjudicated synthetic pitch events."""
from types import MappingProxyType

DESIGN = "phase4a-design-v0.1"
SCHEMA = "phase4a-count-reference-v1"
APPROVED_GAME_MAIN = "1c5b738671a452305a4be874c57f77cd8ad5db10"
RESEARCH_MAIN_UNCHANGED = "cd8217a44788cf830c5554b71b04a27a98ce620c"
CAPABILITIES = MappingProxyType({
    "source_kind": "synthetic_fixture", "scope": "partial_count_rules",
    "probability_model": False, "connected_to_statcast": False,
    "connected_to_phase3d": False, "official_stats_generated": False,
    "ability_calibrated": False, "android_integrated": False,
    "product_adoption": False,
})
