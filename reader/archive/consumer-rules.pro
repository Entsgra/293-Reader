# commons-compress references codecs and tooling it only loads when present. None ship here:
# zstd and brotli entries are rejected as unsupported, and the OSGi/ASM paths are never reached.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
