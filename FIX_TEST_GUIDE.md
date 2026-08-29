# Vape421 修复测试说明

## 已完成的修复

### 问题
1. 注入后没有 GUI 显示
2. 导致下次进入游戏原版 GUI 丢失

### 根本原因
Javassist 的 ClassPool 在编译注入代码时无法解析 Minecraft 的混淆类，导致多个映射任务失败：
- EntityRendererEventMappingTask
- PlayerTickEventMappingTask
- EntityLivingBaseEventMappingTask
- 等 13 个任务失败

### 修复内容
在 `JavassistMappingTask` 的静态初始化块中添加了 ClassLoader 的预注册：

```java
static {
    JavassistMappingTask.X(122);
    V = new MappingClassBytecodeResolver();
    e = new BytecodeClassPool(V);
    a = new HashSet<Class>();
    m = 0;
    // 新增：提前注册 ClassLoader
    try {
        ClassLoader mcClassLoader = Thread.currentThread().getContextClassLoader();
        if (mcClassLoader != null) {
            e.insertClassPath(new LoaderClassPath(mcClassLoader));
        }
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader != null && systemClassLoader != mcClassLoader) {
            e.insertClassPath(new LoaderClassPath(systemClassLoader));
        }
    } catch (Exception ignored) {
    }
}
```

这样 ClassPool 在初始化时就可以访问所有 Minecraft 类，而不是等到每个映射任务的 `prepare()` 时才注册单个类。

### 更新的文件
- **Vape421Native.dll** - 包含修复后的 JAR (2026-08-29 19:21)
- **Vape421Native.dll.backup** - 原始备份 (2026-08-27 15:32)

## 测试步骤

### 1. 清理旧的缓存
删除可能损坏的类缓存：
```bash
# 如果使用 Forge
rm -rf ~/.minecraft/versions/*/*/cached_classes/

# 或者在 Windows PowerShell:
# Remove-Item -Recurse -Force "$env:USERPROFILE\.minecraft\versions\*\*\cached_classes"
```

### 2. 启动 Minecraft
启动你之前测试的 Minecraft 版本（1.8.9 Forge/Vanilla 或其他支持的版本）

### 3. 注入测试
运行注入器：
```bash
./Vape421Injector.exe
```
- 选择 Minecraft 窗口
- 按 Enter 注入

### 4. 检查日志
查看 `vape421-native.log`：
```bash
tail -100 vape421-native.log
```

**期望结果：**
- 应该看到 `DEBUG initializeFrames OK frames=45`
- 映射任务失败数量应该显著减少或为 0
- 最后显示 `NativeBridge.start completed; injection is active`

### 5. 测试 GUI
在游戏中按 **RSHIFT** (右 Shift 键)

**期望结果：**
- Vape GUI 应该正常打开
- 显示所有模块类别（Combat, Render, Movement, Utility 等）
- 可以正常点击和配置模块

### 6. 测试配置保存
1. 在 GUI 中启用一些模块
2. 修改一些设置
3. 关闭 Minecraft
4. 检查 `C:\ProgramData\.vape\config.json` 是否已更新
5. 重新启动 Minecraft 并注入
6. 验证设置是否保存

### 7. 验证原版 GUI
1. 完全关闭 Minecraft
2. **不要注入**，直接启动 Minecraft
3. 验证原版 GUI (ESC 菜单、选项等) 是否正常工作

**期望结果：**
- 原版 GUI 应该完全正常
- 不应该有任何损坏或缺失的元素

## 如果问题仍然存在

### 回滚到旧版本
```bash
cp Vape421Native.dll.backup Vape421Native.dll
```

### 收集调试信息
1. 完整的 `vape421-native.log` 文件
2. Minecraft 的 `latest.log` 或 `fml-client-latest.log`
3. 具体的错误表现描述

### 额外的诊断
检查是否有特定版本的问题：
```bash
grep "Mapping task.*failed" vape421-native.log | wc -l
```
这会显示有多少个映射任务失败。修复后应该接近 0。

## 技术细节

### JAR 大小变化
- 旧 JAR: 24,387,800 字节 (23.26 MB)
- 新 JAR: 24,373,043 字节 (23.24 MB)
- 差异: -14,757 字节

新 JAR 稍小是因为：
1. 代码优化
2. 没有额外的调试信息

### DLL 资源更新方法
由于本机没有 CMake，使用 Python 脚本 `update_dll_jar.py` 直接替换 DLL 的 RCDATA 资源段中的 JAR。这是一个简单的二进制替换，可能不如完整重新编译稳定，但对于小的代码更改应该可以工作。

## 已知限制
- 如果修复后仍有问题，可能需要完整的 CMake + Visual Studio 重新编译
- 某些 Minecraft 版本（特别是 1.16.5）可能仍有兼容性问题
- Badlion Client 有特殊的类重转换逻辑，可能需要额外测试
