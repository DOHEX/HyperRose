# ROSE BudsFeel MK2 — Bluetooth Control Protocol

> Reverse-engineered from Wireshark capture (HCI snoop) between Xiaomi 15 and ROSE BudsFeel MK2.
> Capture date: 2026-07-04.
> Local source artifacts: `.local/research/budsfeel-mk2/` (ignored by Git).
>
> **修正记录 (2026-08-01)**: ① `0x0E` 是**游戏模式**（其效果就是降低延迟），MK2 只有游戏模式、没有独立的低延迟开关——早期版本误标为 Low Latency；② `0x2F` 属自定义 EQ 增益组，不是游戏模式。详见 §3 / §6。

## Device Info

| Field              | Value                           |
|--------------------|---------------------------------|
| Headset            | ROSE BudsFeel MK2               |
| Headset BD_ADDR    | Captured, redacted for public docs |
| Chipset            | **JieLi (杰理) AC697**            |
| SDK tag (from SPP) | `jl_sdk_ac697_publish`          |
| Phone              | Xiaomi 15 (BD_ADDR redacted)      |

## Transport Stack

```
Bluetooth Classic (BR/EDR)
  └─ L2CAP
       ├─ RFCOMM (custom binary control protocol)
       │    └─ Service UUID: 0cf12d31-fac3-4553-bd80-d6832e7b3931
       └─ SPP (Serial Port Profile) — authentication handshake
```

Two parallel RFCOMM channels:

- **SPP** — used only for initial auth handshake
- **Data** — custom binary protocol for all control commands

---

## 1. SPP Authentication Handshake

Before the data channel becomes active, a challenge-response authentication occurs over SPP:

```
   TX (phone)                  RX (headset)
   ─────────                   ────────────
1. fe dc ba c0 06 00 02 00 01    →  custom init header
2. 00 1c a1 15 85 ce 29 0b ...   →  16-byte challenge (prefixed with 0x00)
3.                                 ← 01 a1 b0 78 00 4e 51 53 ...  (prefixed with 0x01)
4. 02 70 61 73 73                →  "pass" (auth success)
5.                                 ← 00 6e 2f 1a f2 c9 b9 13 ...  (second challenge, 0x00 prefix)
6. 01 55 1a c2 4e 90 ba a9 ...   →  second response (0x01 prefix)
7.                                 ← 02 70 61 73 73  ("pass")
8. fe dc ba c0 03 00 06 1e ...   →  config/init
9.                                 ← fe dc ba 00 03 00 79 ...  (device info)
10.                                ← fe dc ba 80 c2 00 13 ...  (additional info)
```

### SPP Frame Header Pattern

Frames with prefix `fe dc ba` appear to be a custom protocol wrapper:

| Offset | Size | Description                                                          |
|--------|------|----------------------------------------------------------------------|
| 0      | 3    | Magic: `fe dc ba`                                                    |
| 3      | 1    | Direction/role: `c0` = TX init, `00` = RX response, `80` = RX notify |
| 4      | 1    | Command/sequence byte                                                |
| 5      | 2    | Payload length (big-endian)                                          |
| 7+     | N    | Payload                                                              |

### Challenge-Response (UNKNOWN ALGORITHM)

- Each challenge is 16 bytes, prefixed with `0x00`
- Each response is 16 bytes, prefixed with `0x01`
- Response algorithm is **not yet reverse-engineered**
- Likely candidates: AES with device-derived key, JieLi AC697 SDK standard handshake, or key derived
  from BD_ADDR

> **⚠️ BLOCKER**: Without the challenge-response algorithm, the data channel may reject commands.
> See §5 for workarounds.

---

## 2. Data Channel Protocol

### 2.1 Frame Format

```
┌────────┬─────┬──────────┬──────────────┬──────────┬──────┐
│ HEADER │ SEQ │ CMD/LEN  │   PAYLOAD    │ CHECKSUM │ 0xAA │
│  1B    │ 1B  │   1B     │   variable   │   1B     │  1B  │
└────────┴─────┴──────────┴──────────────┴──────────┴──────┘
```

| Field    | Description                                                                                                                |
|----------|----------------------------------------------------------------------------------------------------------------------------|
| HEADER   | `0xFF` = command (phone → headset), `0xDD` = response/notification (headset → phone)                                       |
| SEQ      | Rolling sequence number, echoed in ACK                                                                                     |
| CMD/LEN  | Command type; for SET commands = `0x02`, for capability query = `0x1E`, for capability response = `0x15`, for ACK = `0x01` |
| CHECKSUM | `(SUM of all preceding bytes) & 0xFF` — simple 8-bit sum                                                                   |
| 0xAA     | Fixed frame trailer                                                                                                        |

### 2.2 Checksum

```python
def checksum(data: bytes) -> int:
    """All bytes before the checksum position."""
    return sum(data) & 0xFF

def verify(frame: bytes) -> bool:
    return checksum(frame[:-2]) == frame[-2]

def build_frame(header: int, seq: int, cmd: int, payload: bytes) -> bytes:
    head = bytes([header, seq, cmd]) + payload
    ck = checksum(head)
    return head + bytes([ck, 0xAA])
```

### 2.3 Command Reference

#### ACK — 0x01

```
Format:  dd XX 01 fe CK aa
```

Acknowledgment of a command. `XX` matches the command's sequence number. Status byte is always
`0xFE` (observed).

**Example**: `dd 04 01 fe e0 aa` — ACK for command seq=0x04.

---

#### SET — 0x02

```
Format:  ff XX 02 [TYPE] [VALUE] CK aa
```

Set a device parameter. Fixed 6-byte frame: TYPE (1B) + VALUE (1B).

**Example**: `ff 04 02 09 04 12 aa` — Set ANC mode to Wind Noise.

---

#### Capability Query — 0x1E

```
Format:  ff XX 1E fa 01 [28 capability IDs...] CK aa
```

Queries device capability/status for 28 parameters. The payload is always:

```
fa 01 07 08 09 0c 0d 0e 12 2a 2b 2c 2d 2e 2f 31 32 33 36 37 38 39 3a 3b 3c 3d 3f 45 46 49
```

(28 capability IDs, `fa 01` is a sub-command header.)

After ACK, the headset responds with a Capability Response (0x15).

---

#### Capability Response — 0x15

```
Format:  dd XX 15 [LTV-encoded status data...] CK aa
```

Returns current values for all queried parameters, encoded as LTV (Length-Type-Value) entries. The
payload uses a 3-byte header (`01 01 01`) followed by entries:

| Format        | Description                              |
|---------------|------------------------------------------|
| `LL TT VV`    | 3 bytes: Len=2, Type, 1-byte Value       |
| `LL TT VV...` | N+2 bytes: Len>2, Type, multi-byte Value |

Len field counts **Type + Value bytes** (i.e., `Len = 1 + value_size`).

---

#### Unsolicited Notification — (headset→phone)

```
Format:  dd 00 02 [TYPE] [VALUE] CK aa
```

The headset may push parameter changes autonomously (e.g., when low-latency mode changes). Uses
seq=0x00.

---

## 3. Parameter Registry

### Type 0x09 — ANC Mode

Commands: `ff XX 02 09 VV CK aa`

| Value  | Mode                      | Chinese |
|--------|---------------------------|---------|
| `0x01` | Active Noise Cancellation | 降噪      |
| `0x02` | Normal / Off              | 普通      |
| `0x03` | Transparency / Ambient    | 通透      |
| `0x04` | Wind Noise Reduction      | 风噪      |

### Type 0x0E — Game Mode (游戏模式)

Commands: `ff XX 02 0e VV CK aa`

| Value  | State |
|--------|-------|
| `0x00` | OFF   |
| `0x01` | ON    |

> **修正 (2026-08-01)**: 0x0E 是游戏模式——游戏模式的效果就是降低延迟，MK2 只有游戏模式，**没有独立的低延迟开关**。
> The headset sends unsolicited notifications (`dd 00 02 0e VV`) when this parameter changes.

### Type 0x2F — Custom EQ Band (自定义 EQ 段位)

Commands: `ff XX 02 2f VV CK aa` — 设置自定义 EQ 2F 段的增益。

| Value  | Meaning        |
|--------|----------------|
| `0x00` | 段增益 0（默认）   |
| `0x04` | 段增益 4（观测值） |

> **修正 (2026-08-01)**: 早期版本把 `0x2F:04` 误认作游戏模式。实际 0x2F 属于自定义 EQ 增益组
> （见 §6），抓包中 `ff 0d 02 2f 04 41 aa` 是调整 EQ 段位，不是游戏模式开关。游戏模式是 0x0E。

---

## 4. Interaction Flow

### 4.1 Full Session

```
1. SPP auth handshake (§1)
2. Capability query (seq=00) → response
3. Capability query (seq=01) → response
4. Capability query (seq=02) → response
5. Capability query (seq=03) → response
6. Mode switching via SET commands (§2.3)
```

The phone sends 2–5 capability queries at session start to initialize state (count varies per session; 4 was observed in the 2026-07-04 capture).

### 4.2 Mode Switch

```
TX → ff XX 02 [TYPE] [VALUE] CK aa    (SET command)
RX ← dd XX 01 fe CK aa                (ACK, same seq)
```

If the parameter triggers autonomous headset behavior (type 0x0E), the headset additionally sends:

```
RX ← dd 00 02 [TYPE] [VALUE] CK aa    (unsolicited notification)
```

### 4.3 Observed Mode Switch Sequence

```
Seq  Type  Value  Mode
─────────────────────────────
04   0x09  0x04   风噪 (Wind Noise)
05   0x09  0x02   普通 (Normal)
06   0x09  0x03   通透 (Transparency)
07   0x09  0x01   降噪 (ANC)
08   0x0E  0x01   游戏模式 ON
09   0x0E  0x00   游戏模式 OFF
0A   0x0E  0x01   游戏模式 ON
0B   0x0E  0x00   游戏模式 OFF
0D   0x2F  0x04   自定义 EQ 2F 段 = 4
```

---

## 5. Next Steps for Third-Party Control App

### 5.1 Try Bypassing SPP Auth First

Connect directly to the RFCOMM data channel and send a capability query:

```python
# Pseudocode
sock = connect_rfcomm(bdaddr, uuid="0cf12d31-fac3-4553-bd80-d6832e7b3931")
query = bytes.fromhex("ff001efa010708090c0d0e122a2b2c2d2e2f313233363738393a3b3c3d3f454649e9aa")
sock.send(query)
response = sock.recv(1024)
# If response starts with dd 00 01 fe → auth is NOT required, you're in.
```

If the headset responds to commands without the SPP handshake, the rest of the protocol Just Works™.

### 5.2 If Auth IS Required

Options, in order of practicality:

1. **Decompile the companion app's APK** — look for native `.so` libraries handling SPP auth,
   especially around the `fe dc ba` protocol header
2. **Search for JieLi AC697 SDK** — the handshake may be a standard SDK routine (
   `jl_sdk_ac697_publish` is a strong clue)
3. **Frida hook** — hook the phone's Bluetooth stack to capture or replay auth tokens
4. **Brute-force analysis** — collect many challenge-response pairs to identify the algorithm (
   AES-128? CMAC? Custom?)

### 5.3 Building the Control Library

Once auth is solved, the data-channel protocol is fully characterized. A Python reference
implementation skeleton:

```python
class BudsFeelMK2:
    ANC_WIND     = 0x04
    ANC_NORMAL   = 0x02
    ANC_TRANSP   = 0x03
    ANC_ANC      = 0x01
    GAME_MODE    = 0x0E

    def set_anc_mode(self, mode: int) -> None:
        self._send_set(0x09, mode)

    def set_game_mode(self, on: bool) -> None:
        self._send_set(0x0E, 0x01 if on else 0x00)

    def set_eq_band(self, band: int, gain: int) -> None:
        # 自定义 EQ 段位: band ∈ {0x2A, 0x2B, ..., 0x2F, 0x32}, 0x2A 为双字节值
        self._send_set(band, gain)

    def query_capabilities(self) -> bytes:
        payload = bytes.fromhex("fa010708090c0d0e122a2b2c2d2e2f"
                                "313233363738393a3b3c3d3f454649")
        return self._send_cmd(0x1E, payload)

    def _send_set(self, ptype: int, value: int) -> None:
        self._send_cmd(0x02, bytes([ptype, value]))

    def _send_cmd(self, cmd: int, payload: bytes) -> bytes:
        head = bytes([0xFF, self._seq, cmd]) + payload
        ck = sum(head) & 0xFF
        frame = head + bytes([ck, 0xAA])
        self._seq = (self._seq + 1) & 0xFF
        self._sock.send(frame)
        return self._sock.recv(1024)
```

---

## 6. Capability Query Parameter IDs

The 28 parameters queried by `cmd=0x1E`:

```
07, 08, 09, 0c, 0d, 0e, 12,
2a, 2b, 2c, 2d, 2e, 2f,
31, 32, 33,
36, 37, 38, 39, 3a, 3b, 3c, 3d, 3f,
45, 46, 49
```

Known mappings:

| ID     | Name            | Observed Values |
|--------|-----------------|-----------------|
| `0x09` | ANC Mode        | 01, 02, 03, 04  |
| `0x0E` | Game Mode       | 00, 01          |
| `0x2A` | Custom EQ band (2 字节) | `05 02`  |
| `0x2B` | Custom EQ band  | 00              |
| `0x2C` | Custom EQ band  | 03              |
| `0x2D` | Custom EQ band  | 03              |
| `0x2E` | Custom EQ band  | 01              |
| `0x2F` | Custom EQ band  | 00, 04          |
| `0x32` | Custom EQ band  | 00, 01          |

> **修正 (2026-08-01)**: `0x2A–0x2F`/`0x32` 是自定义 EQ 增益组（约 8 段，`0x2A` 为双字节值），
> 不是独立功能开关。`0x0E` 才是游戏模式。设备支持自定义 EQ 已由厂商 App 界面确认。

---

## Appendix A: Full Hex Command Reference

```
# Capability Query
ff 00 1e fa 01 07 08 09 0c 0d 0e 12 2a 2b 2c 2d 2e 2f 31 32 33 36 37 38 39 3a 3b 3c 3d 3f 45 46 49 e9 aa

# ANC: Wind Noise
ff 04 02 09 04 12 aa

# ANC: Normal
ff 05 02 09 02 11 aa

# ANC: Transparency
ff 06 02 09 03 13 aa

# ANC: Active Noise Cancellation
ff 07 02 09 01 12 aa

# Game Mode: ON
ff 08 02 0e 01 18 aa

# Game Mode: OFF
ff 09 02 0e 00 18 aa

# Custom EQ: band 0x2F = 4
ff 0d 02 2f 04 41 aa
```

Seq numbers and checksums must be recalculated for your own sessions.

## Appendix B: SPP Device Info Raw

Frame 1276 payload contains readable ASCII fragments:

```
hE9yfseX6UdK7rFh
jl_sdk_ac697_publish
```
