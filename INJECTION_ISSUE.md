# Injection Issue Analysis

## Problem
After injection with Vape421Native.dll (Aug 27 build):
1. Vape GUI does not appear (RSHIFT keybind doesn't work)
2. Subsequent Minecraft launches have corrupted vanilla GUI

## Root Cause
The DLL contains an outdated JAR from Aug 27 15:32. Multiple mapping tasks fail during bytecode transformation:

```
Mapping task 4 failed: EntityRendererEventMappingTask
Mapping task 5 failed: PlayerTickEventMappingTask  
Mapping task 6 failed: EntityLivingBaseEventMappingTask
Mapping task 7 failed: LegacyWorldEntityJoinEventMappingTask
Mapping task 8 failed: EntityPlayerSPEventMappingTask
Mapping task 10 failed: RenderPlayerEventMappingTask
Mapping task 11 failed: EntityRenderStateMappingTask
Mapping task 12 failed: PlayerControllerMPEventMappingTask
Mapping task 15 failed: RenderManagerEntityMappingTask
Mapping task 18 failed: NetworkPacketEventMappingTask
Mapping task 21 failed: PlayerTabOverlayDisplayNameLegacyMappingTask
Mapping task 22 failed: ItemStackTooltipMappingTask
Mapping task 25 failed: LivingSpecialsRenderMappingTask
```

All failures have the same pattern:
```
java.lang.RuntimeException: cannot find net.minecraft.entity.Entity: pk found in net/minecraft/entity/Entity.class
```

This means Javassist's ClassPool cannot resolve obfuscated Minecraft classes when compiling injected bytecode.

## Why This Happens
The BytecodeClassPool is initialized once statically but needs access to ALL Minecraft classes. When each mapping task registers its target class's ClassLoader via `LoaderClassPath`, it only registers that specific class, not the full classpath.

When Javassist tries to compile injected code like:
```java
EventPlayerTick event = new EventPlayerTick(player);
```

It needs to resolve `EntityPlayer` and all its dependencies. But if those classes haven't been "prepared" yet, the ClassPool can't find them.

## Temporary Workaround
**Delete Minecraft's transformer cache before each launch:**

For Forge 1.8.9:
```bash
rm -rf ~/.minecraft/versions/1.8.9-forge*/1.8.9-forge*/cached_classes/
```

This prevents corrupted transformed classes from being reused.

## Proper Fix
Rebuild the DLL with the updated JAR:

```powershell
# Requires CMake and Visual Studio 2022
./gradlew prepareInjectionBundle -PtargetRelease=8 `
  -PnativeJavaHome="C:\Program Files\Java\jdk1.8.0_301"
```

The issue is likely fixed in the current JAR (Aug 29 19:17) but hasn't been embedded into the DLL yet.

## Alternative: ClassPool Initialization Fix
If the issue persists even with a fresh build, the root problem is in how the ClassPool gets access to Minecraft's classloader. A potential fix:

In `JavassistMappingTask` static initializer, explicitly add the game's classloader:

```java
static {
    JavassistMappingTask.X(122);
    V = new MappingClassBytecodeResolver();
    e = new BytecodeClassPool(V);
    a = new HashSet<Class>();
    m = 0;
    
    // ADD THIS: Register Minecraft's classloader upfront
    try {
        ClassLoader mcClassLoader = Class.forName("net.minecraft.client.Minecraft").getClassLoader();
        e.insertClassPath(new LoaderClassPath(mcClassLoader));
    } catch (Exception ignored) {}
}
```

This would make ALL Minecraft classes available to Javassist from the start, not just the ones being transformed.
