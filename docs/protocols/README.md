# Protocol Documentation

协议文档记录已逆向确认的 ROSE 耳机控制协议。完整抓包、JSON 导出和反编译产物保存在本地的 `.local/research/`，不进入 Git。

| Device | Transport | Implementation | Document | Status |
|---|---|---|---|---|
| ROSE BudsFeel MK2 | Bluetooth Classic RFCOMM + SPP auth | `BudsFeelMk2Profile` / shared ROSE RFCOMM protocol | [budsfeel-mk2.md](budsfeel-mk2.md) | Core RFCOMM controls documented; SPP auth algorithm unresolved |
| ROSE EARFREE i7 | Bluetooth Classic RFCOMM | `EarFeelI7Profile` / shared ROSE RFCOMM protocol | [earfree-i7.md](earfree-i7.md) | Core controls documented; advanced controls need follow-up captures |
| ROSE Ceramics U | Bluetooth Classic RFCOMM | `CeramicsUProfile` / shared ROSE RFCOMM protocol | [ceramics-u.md](ceramics-u.md) | Extended controls documented and parser differences noted |

## Maintenance Notes

- Keep device addresses and other unique identifiers redacted in committed docs.
- Prefer stable protocol conclusions over raw capture timelines.
- When raw artifacts are needed, reference the corresponding `.local/research/<device>/` path instead of moving artifacts back under `docs/`.
