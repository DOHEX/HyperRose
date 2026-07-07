<div align="center">

# HyperRose

**让弱水时砂耳机融入 HyperOS 生态**

[![GitHub Release](https://img.shields.io/github/v/release/DOHEX/HyperRose?style=flat-square&logo=github&color=black)](https://github.com/DOHEX/HyperRose/releases)
![Downloads](https://img.shields.io/github/downloads/DOHEX/HyperRose/total?style=flat-square)
[![Platform](https://img.shields.io/badge/Android-15+-green?style=flat-square&logo=android)](https://android.com)
[![LSPosed](https://img.shields.io/badge/LSPosed-API≥101-blueviolet?style=flat-square)](https://github.com/LSPosed/LSPosed)
[![HyperOS](https://img.shields.io/badge/HyperOS-澎湃OS-orange?style=flat-square)](https://hyperos.mi.com)
[![License](https://img.shields.io/github/license/DOHEX/HyperRose?style=flat-square)](LICENSE)

</div>

HyperRose 是一个 Xposed 模块，为小米 HyperOS 设备提供 **弱水时砂**
耳机的系统级控制能力。通过 Hook 系统蓝牙服务，让弱水时砂耳机获得与小米原生 TWS 耳机一致的 HyperOS
深度集成体验。

---

## 设备支持

| 能力                  | ROSESELSA EARFREE i5 | ROSE BudsFeel MK2 |
|---------------------|:--------------------:|:-----------------:|
| 连接方式                |       BLE GATT       |  Classic RFCOMM   |
| 降噪模式 (降噪/风噪/普通/通透)  |          ✓           |         ✓         |
| 降噪深度 (轻/中/深)        |          ✓           |         —         |
| 通透强度 (舒适/人声/标准)     |          ✓           |         —         |
| EQ 调音 (经典/日系/乐器/清新) |          ✓           |         —         |
| 电量显示 (左耳/右耳/充电盒)    |          ✓           |         ✓         |
| 游戏模式                |          ✓           |         ✓         |
| 查找耳机                |          ✓           |         —         |

---

## 功能特性

### 耳机控制

| 功能        | 说明                         |
|-----------|----------------------------|
| **降噪控制**  | 关闭 / 降噪 / 风噪 / 通透 四种模式一键切换 |
| **降噪深度**  | 轻度 / 中度 / 深度 三档可调          |
| **通透模式**  | 舒适通透 / 人声通透 / 标准通透         |
| **EQ 调音** | 弱水经典 / 日系柔情 / 乐器大师 / 清新空灵  |
| **电量显示**  | 实时显示左耳、右耳、充电盒电量            |
| **游戏模式**  | 低延迟模式切换                    |
| **查找耳机**  | 左耳 / 右耳 / 停止查找             |

### HyperOS 深度集成

- **超级岛** — 耳机电量实时显示在 HyperOS 灵动岛式通知中
- **融合设备中心** — 耳机以原生 TWS 设备身份出现在系统设备中心
- **控制中心快捷操作** — 点击控制中心耳机电量卡片，弹出浮窗快速控制降噪与 EQ
- **设置页跳转** — 系统「更多设置」按钮直接跳转至 HyperRose 控制面板

### 模块特性

- **双模式连接** — 支持独立 BLE 直连模式（无需 Xposed）和 Hook 桥接模式
- **主题跟随** — 深色 / 浅色模式跟随系统自动切换，支持毛玻璃效果
- **设备选择** — 自动发现已配对的 ROSE EARFREE 设备

---

## 工作原理

HyperRose 通过 Hook 三个系统进程实现深度集成：

```
com.android.bluetooth   ─→  检测耳机连接、管理 GATT 通信、注入耳机 UI 数据
com.xiaomi.bluetooth    ─→  超级岛通知、系统耳机 UI 数据注入
com.milink.service      ─→  设备身份伪装、音频切换、拦截系统 ANC 指令
```

---

## 系统要求

| 项目 | 要求                                       |
|----|------------------------------------------|
| 设备 | 小米设备（运行 HyperOS）                         |
| 系统 | Android 15+                              |
| 框架 | LSPosed（API ≥ 101）                       |
| 耳机 | ROSESELSA EARFREE i5 / ROSE BudsFeel MK2 |

---

## 安装使用

1. 从 [Releases](https://github.com/DOHEX/HyperRose/releases) 下载最新 APK 并安装
2. 打开 LSPosed，启用 HyperRose 模块并勾选推荐作用域
3. 进入 HyperRose 设置页面，点击右上角 **重启作用域**
4. 通过蓝牙连接你的弱水时砂耳机

> **提示：** 即使不安装 LSPosed，HyperRose 也可作为独立 App 使用，通过 BLE 直连耳机进行基本控制。

---

## 未来展望

HyperRose 专注于弱水时砂耳机生态，目前已支持两款型号。新增型号需实现 `DeviceProfile` 接口（含
`TransportSpec`、`DeviceCapabilities`、`DeviceProtocol` 指令/解析），并在 `DeviceProfileRegistry`
中注册即可接入。

如果你希望 HyperRose 支持你的耳机型号，欢迎提交 [Issue](https://github.com/DOHEX/HyperRose/issues)
并提供以下信息：

- 耳机品牌与型号
- 蓝牙通信方式（BLE GATT 或 Classic RFCOMM）及抓包数据（服务 UUID、特征值、通信帧格式）
- 期望支持的功能（降噪、EQ、电量等）

---

## 致谢

- [HyperPods](https://github.com/Art-Chen/HyperPods)
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods)
- [OppoPods-Enhanced](https://github.com/1812z/OppoPods)
- [HyperOriG](https://github.com/KiriChen-Wind/HyperOriG/)
- [Miuix](https://github.com/YuKongA/miuix)
- [LibXposed API](https://github.com/libxposed/api)

---

## 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证开源。
