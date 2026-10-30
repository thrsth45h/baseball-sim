"""Externally adjudicated safe hits; no hit-value or runner decision model."""
from types import MappingProxyType

DESIGN = "phase4e-design-v0.1"
SCHEMA = "phase4e-reference-v0.1"
APPROVED_GAME_MAIN = "e1af536153c36ab34be8199a3707ff0c1c9ba0fb"
DEPENDENCY_SOURCES = ("prototypes/__init__.py", "prototypes/phase4a/__init__.py", "prototypes/phase4a/model.py",
    "prototypes/phase4b/__init__.py", "prototypes/phase4b/model.py", "prototypes/phase4c/__init__.py", "prototypes/phase4c/model.py",
    "prototypes/phase4d/__init__.py", "prototypes/phase4d/model.py")
CAPABILITIES = MappingProxyType({"source_kind":"synthetic_fixture", "scope":"partial_completed_safe_hit_projection",
    "connected_to_phase4a":True, "connected_to_phase4b":True, "connected_to_phase4c":True, "connected_to_phase4d":True,
    "connected_to_statcast":False, "connected_to_phase3d":False, "probability_model":False,
    "game_end_rules_implemented":False, "official_stats_generated":False, "ability_calibrated":False,
    "android_integrated":False, "product_adoption":False})
