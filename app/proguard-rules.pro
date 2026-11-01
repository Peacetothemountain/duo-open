# Services and the wallpaper are referenced from the manifest; R8 keeps those.
# AGSL sources live in res/raw and are read by name, so nothing else to keep.

# CoverHold reaches Shizuku.newProcess reflectively (private in API 13).
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
