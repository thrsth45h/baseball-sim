"""Uncalibrated single-game integration prototype, not product baseball rules."""
from types import MappingProxyType

DESIGN="phase4f-design-v0.1"
SCHEMA="phase4f-game-v0.1"
APPROVED_GAME_MAIN="409bbf6d40ee04a52b6e91546892a532d7c3d2d2"
DEPENDENCY_SOURCES=("prototypes/__init__.py", *["prototypes/phase4"+p+"/"+f for p in "abcde" for f in ("__init__.py","model.py")])
CAPABILITIES=MappingProxyType({"source_kind":"synthetic_toy_policy","scope":"single_game_integration_prototype",
    **{"connected_to_phase4"+p:True for p in "abcde"},"probability_model":True,
    "probability_model_kind":"unvalidated_toy_pitch_event_weights","game_end_rules_implemented":True,
    **{key:False for key in ("upstream_pitch_generation","perception_model","batted_ball_physics","research_model_connected",
        "mlb_calibrated","ability_calibrated","official_stats_generated","savant_metrics_generated","android_integrated","product_adoption")}})
