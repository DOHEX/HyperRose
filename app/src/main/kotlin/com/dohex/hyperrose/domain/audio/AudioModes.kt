package com.dohex.hyperrose.domain.audio

/** ANC 主模式 */
enum class AncMode(val label: String) {
    NOISE_CANCEL("降噪"),
    WIND_NOISE("风噪"),
    NORMAL("普通"),
    TRANSPARENT("通透")
}

/** 降噪深度（仅 ANC=降噪 时有效） */
enum class AncDepth(val label: String) {
    LIGHT("轻度降噪"),
    MEDIUM("中度降噪"),
    DEEP("深度降噪")
}

/** 通透强度（仅 ANC=通透 时有效） */
enum class TransparencyLevel(val label: String) {
    COMFORTABLE("舒适通透"),
    VOCAL("人声通透"),
    STANDARD("标准通透")
}

/** EQ 调音预设 */
enum class EqPreset(val label: String) {
    CLASSIC("弱水经典"),
    JAPANESE("日系柔情"),
    INSTRUMENT("乐器大师"),
    FRESH("清新空灵")
}
