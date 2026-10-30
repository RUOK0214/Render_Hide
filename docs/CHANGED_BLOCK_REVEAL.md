# Changed-block reveal (2.6.3)

H → Changed blocks: enable temporary opaque rendering and choose a hold time
from 0.1 to 60 seconds (default: disabled, 2 seconds). Done applies and saves;
Cancel/Escape discards edits. Settings persist in renderhide-changed-blocks.json.

Block-state changes inside enabled regions restart a per-position timer. This
includes redstone power changes, lever/repeater states, placement and piston
movement. When the timer expires, the configured opacity/filter rules apply
again. Air is never drawn. Entity motion and NBT-only changes (for example an
inventory content change without a block-state update) are not triggers.

Timers use client game ticks (20 per second), pause with a paused game, and are
cleared on world change/disconnect/chunk unload. Changing the feature settings
clears current timers. Expiring and newly visible sections and neighboring faces
are marked dirty once per client tick; no full renderer reload per block change.
Render workers read concurrent immutable position entries, not live entities.

Automated timer/lifecycle tests use API stubs; the Gradle build checks API
compatibility. In-game visual testing with vanilla/Sodium/Iris remains necessary.
