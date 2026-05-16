# Haptic fires on Catch; stroke count advances on Finish

The haptic cue fires at the **Catch** phase (blade enters water) because that is the moment paddlers need to synchronise — it cues the movement. The training stroke counter increments at the **Finish** phase (blade exits water) because a stroke is not complete until then.

These two events are deliberately decoupled. Firing the haptic at Finish would cue too late; counting on Catch would count incomplete strokes. A future change that moves either trigger should treat them independently.
