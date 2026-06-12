# Aurelia Client 项目交接

项目目录：`/Users/benjamin/aurelia-client`

## 项目定位

Aurelia 是适配 Minecraft Java 1.21.1 的 Fabric 客户端模组，暂时只有视觉功能，计划增加攻击功能（例如Killaura和CrystalAura）,代码参考可以参考开源客户端https://github.com/MeteorDevelopment/meteor-client

## 构建与启动

```bash
cd /Users/benjamin/aurelia-client
./gradlew clean build
./gradlew runClient --args='--quickPlaySingleplayer "New World"'
```

构建后的 JAR：

`/Users/benjamin/aurelia-client/build/libs/aurelia-client-0.1.0.jar`

## 操作按键

- Right Shift：打开 ClickGUI
- Z：按住显示战斗建议
- H：生成或移除测试假人

## 当前功能

- Crystal Risk 水晶伤害与剩余血量预测
- 考虑护甲、抗性效果、距离和方块遮挡
- 致死水晶提示与屏幕红色警告边框
- 水晶立体危险范围、伤害标签和目标框
- Threat Intelligence 玩家信息面板
- ClickGUI、HUD、Watermark、Status
- 测试假人
- Scaffold Guide，仅提供视觉提示
- Killaura（Combat 分类）：自动攻击附近生物（玩家/怪物/测试假人），可配置距离（3.0-6.0m），攻击时可见旋转锁定 + 红色目标框高亮。使用现有模块开关与设置循环系统。

## UI 实现重点

主要文件：

- src/client/java/dev/aurelia/client/ui/AureliaScreen.java
- src/client/java/dev/aurelia/client/ui/UiTheme.java
- src/client/java/dev/aurelia/client/ui/SdfRoundedRectRenderer.java
- src/client/java/dev/aurelia/client/ui/HudRenderer.java
- src/client/java/dev/aurelia/client/ui/WorldOverlayRenderer.java

圆角已改为 SDF 片元着色器实时绘制，不再使用方格圆角贴图：

- assets/aurelia/shaders/core/sdf_rounded_rect.vsh
- assets/aurelia/shaders/core/sdf_rounded_rect.fsh
- assets/aurelia/shaders/core/sdf_rounded_rect.json

动画使用基于真实帧间隔的指数插值。已移除会造成字体和边缘抖动的整窗缩放动画。

Aurelia 自身 UI 使用 Rubik 字体：

- assets/aurelia/font/rubik_regular.ttf
- assets/aurelia/font/ui.json
- RUBIK_LICENSE.txt

Minecraft 原版菜单仍然使用原版字体。

## 许可与注意事项

项目使用 GPL-3.0-only，分发修改版本时必须公开对应源代码并保留许可证。

LiquidBounce 仅作为视觉渲染实现参考；当前 SDF 渲染代码是独立实现，没有直接复制 LiquidBounce 代码。

当前工作区存在大量尚未提交的项目初始化与功能修改，接手后应先执行：

```
git status
git diff --check
./gradlew clean build
```

最后一次干净构建已成功，SDF 着色器也已在游戏内完成运行验证
