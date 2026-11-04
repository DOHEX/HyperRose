# 锆势（ROSE 锆势 · 芙宁娜）耳机协议接入

- 型号：弱水时砂 锆势 × 原神联名「芙宁娜·不休独舞」
- 实测蓝牙名：`Furina Endless Solo of Solitude`（联名款命名，**不带 `ROSE` 前缀**）
- 硬件：平面钛金属振膜 × HR+ 旗舰动圈、32 位 RISC-V + HiFi4 DSP、LHDC-V5 / LC3、宽频深海降噪、双设备连接、ROSELINK 3.0
- 代码：`GaoshiProfile`（`app/src/main/kotlin/com/dohex/hyperrose/profile/GaoshiProfile.kt`）
- 验证方式：实机（小米 HyperOS）连接 + 降噪 / 通透切换，均正常

## 1. 协议结论

锆势没有独立公开协议，但与同厂在售型号共用 **ROSE RFCOMM 协议族**，实机验证可直连控制：

| 项目 | 值 |
|---|---|
| 传输 | Bluetooth Classic RFCOMM |
| 控制服务 UUID | `0cf12d31-fac3-4553-bd80-d6832e7b3931`（与 BudsFeel MK2 / EarFeel i7 / Ceramics U 逐字节一致） |
| 帧格式 | 手机→耳机 `FF <seq> <len> <cmd> <payload> <CK> AA`；耳机→手机 `DD …` |
| 校验 | 帧头至 payload 末尾字节求和 `& 0xFF`，帧尾 `AA` |
| 状态查询 | `len=0x1E`（30）+ 命令 `0xFA` + `01` + 28 个状态键 |

因此 `GaoshiProfile` 复用 `TransportSpec.Rfcomm(0cf12d31-…)` + `RoseRfcommProtocol()`，不新增协议层代码。

## 2. 能力

| 能力 | 支持 | 说明 |
|---|---|---|
| 降噪 / 风噪 / 普通 / 通透 | ✅ | `0x09` = `01` / `04` / `02` / `03`，实机验证 |
| 自适应降噪 | ❌ | 琉璃 Ultra 独有的 `0x09` 值 `0x05`，本机型无 |
| 极限降噪 | ✅ | `0x09` = `06` |
| 降噪深度（轻 / 中 / 深） | ✅ | `0x2C` = `01` / `03` / `05` |
| 通透强度（标准 / 深度） | ✅ | `0x2D` = `01` / `05` |
| EQ 预设（HIFI / POP / ROCK / 经典） | ✅ | `0x2A` = `00` / `01` / `02` / `03` |
| 电量（左 / 右 / 盒） | ✅ | 状态 TLV `0x0C`，或 `len=0x04` 主动推送 |
| 游戏模式 | ✅ | `0x0E` = `01` / `00` |
| 查找耳机（左 / 右 / 停止） | ✅ | `0x2F` = `01` / `02` / `04` |
| 低延迟 | ❌ | 未发现独立命令，与游戏模式同义 |

## 3. HyperOS 集成层

控制能力有两条独立通道，症状要分开看：

| 能力 | 通道 | 依赖 LSPosed |
|---|---|---|
| 连接、降噪、通透、EQ、电量（App 内） | `StandaloneRfcommClient` 独立 RFCOMM 直连 | ❌ |
| 超级岛通知、通知点击进浮层 | `BluetoothProcessHook` → `SHOW_ISLAND` 广播 → `MiBluetoothFocusIslandHook` | ✅ |
| 融合设备中心 / 控制中心卡片 | `MiLinkProcessHook`（`com.milink.service`） | ✅ |
| 系统耳机 UI 数据注入、ANC 指令拦截 | `HeadsetServiceBinderHook`（`com.xiaomi.bluetooth`） | ✅ |

「App 内控制正常、但完全没有通知」= 独立直连通了、Hook 层没跑。排查顺序：

1. App 首页「模块状态」：`模块未激活` → LSPosed 里没启用模块；`作用域待生效` → 启用了但进程没注入。
2. 设备详情页副标题：`独立 RFCOMM 连接` = 走直连；`LSPosed 桥接模式` = Hook 已接管。
3. LSPosed 作用域三个都要勾：`com.android.bluetooth`、`com.xiaomi.bluetooth`、`com.milink.service`，
   勾完用设置页「重启作用域」（需 root）或重启手机。

`SHOW_ISLAND` 只由 hook 侧 `DeviceSession` 在解析到电量帧时发出，独立直连路径不会发岛。
超级岛是瞬时通知（30 秒超时），点击进入浮层控制面板 `QuickControlActivity`。

> 已知隐患（已修）：`MiBluetoothFocusIslandHook` 原先只在 hook 到
> `com.android.bluetooth.ble.app.MiuiBluetoothNotification` 构造函数时才注册广播接收器，
> 而部分 HyperOS 版本对非小米 TWS 不构造该对象 → 接收器永不注册 → 广播全打空。
> 现改为 `ensureReceiver(module, context)` 幂等接口，由 `HeadsetServiceBinderHook` 在
> `HeadsetService.onCreate/onBind` 拿到 Context 时提前注册。

## 4. 维护指引

- **后续联名配色改名**：联名款不保证带 `ROSE` 前缀，按实测名追加到 `GaoshiProfile.nameKeywords`；
  不要用中文昵称做关键词，避免误吞同族其它型号。
- **新增能力/命令**：在 `RoseRfcommProtocol` 加方法，`DeviceProtocol` 保留默认抛
  `UnsupportedOperationException`，不要破坏其它三款的接口。
- **抓包归档**：原始 btsnoop 与导出产物放 `.local/research/gaoshi/`，不进入 Git。
