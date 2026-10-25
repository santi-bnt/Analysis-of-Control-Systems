# Meta Spatial SDK registers systems/components by class and uses reflection.
# R8 class merging or obfuscation can cause duplicate-system registration errors.
-keep class com.meta.spatial.** { *; }
-keep interface com.meta.spatial.** { *; }
-dontwarn com.meta.spatial.**

# PanelBus opens the 3D board by class name.
-keep class com.sesgaro.control_angulos.GameActivity3D { *; }
