package com.example.pixelcolor.engine

data class Achievement(val id: String, val name: String, val description: String, val icon: String, val unlocked: Boolean)

object Achievements {
    val ALL = listOf(
        // 完成数量
        Achievement("first", "初次填色", "完成第一张画", "🎨", false),
        Achievement("five", "小有成就", "完成5张画", "🖌️", false),
        Achievement("collector", "收藏家", "完成20张画", "📚", false),
        Achievement("master", "像素大师", "完成50张画", "🏆", false),
        Achievement("legend", "传奇画师", "完成100张画", "👑", false),
        // 速度
        Achievement("speed", "闪电手", "3分钟内完成一张画", "⚡", false),
        Achievement("speed2", "快速完成", "5分钟内完成一张画", "🏃", false),
        // 尺寸
        Achievement("big", "大幅作品", "完成100×100以上的画", "🖼️", false),
        Achievement("huge", "巨幅创作", "完成200×200以上的画", "🎬", false),
        // 颜色
        Achievement("colorist", "色彩大师", "完成50色以上的画", "🌈", false),
        // 连续打卡
        Achievement("streak3", "三日坚持", "连续3天完成每日挑战", "📅", false),
        Achievement("streak7", "一周达人", "连续7天完成每日挑战", "🔥", false),
        Achievement("streak30", "月度之星", "连续30天完成每日挑战", "🌟", false),
        // 累计时间
        Achievement("time1h", "沉浸其中", "累计游玩1小时", "⏰", false),
        Achievement("time10h", "废寝忘食", "累计游玩10小时", "🕐", false),
        // 累计填色方块
        Achievement("cells10k", "万格填充", "累计填色10000格", "🧱", false),
        Achievement("cells100k", "十万格成就", "累计填色100000格", "🧱", false),
        Achievement("cells1m", "百万格传说", "累计填色1000000格", "💎", false),
    )

    fun check(
        config: GameConfig,
        completedCount: Int,
        dailyStreak: Int,
        totalTimeMs: Long,
        totalFilledCells: Long,
        lastElapsedTimeMs: Long = 0L
    ): List<Achievement> {
        val unlocked = mutableListOf<Achievement>()
        // 完成数量
        if (completedCount >= 1) unlocked.add(ALL[0].copy(unlocked = true))
        if (completedCount >= 5) unlocked.add(ALL[1].copy(unlocked = true))
        if (completedCount >= 20) unlocked.add(ALL[2].copy(unlocked = true))
        if (completedCount >= 50) unlocked.add(ALL[3].copy(unlocked = true))
        if (completedCount >= 100) unlocked.add(ALL[4].copy(unlocked = true))
        // 速度
        if (lastElapsedTimeMs in 1..180_000) unlocked.add(ALL[5].copy(unlocked = true))
        if (lastElapsedTimeMs in 1..300_000) unlocked.add(ALL[6].copy(unlocked = true))
        // 尺寸
        if (config.gridWidth >= 100 || config.gridHeight >= 100) unlocked.add(ALL[7].copy(unlocked = true))
        if (config.gridWidth >= 200 || config.gridHeight >= 200) unlocked.add(ALL[8].copy(unlocked = true))
        // 颜色
        if (config.maxColors >= 50) unlocked.add(ALL[9].copy(unlocked = true))
        // 连续打卡
        if (dailyStreak >= 3) unlocked.add(ALL[10].copy(unlocked = true))
        if (dailyStreak >= 7) unlocked.add(ALL[11].copy(unlocked = true))
        if (dailyStreak >= 30) unlocked.add(ALL[12].copy(unlocked = true))
        // 累计时间
        if (totalTimeMs >= 3_600_000) unlocked.add(ALL[13].copy(unlocked = true))
        if (totalTimeMs >= 36_000_000) unlocked.add(ALL[14].copy(unlocked = true))
        // 累计填色方块
        if (totalFilledCells >= 10_000) unlocked.add(ALL[15].copy(unlocked = true))
        if (totalFilledCells >= 100_000) unlocked.add(ALL[16].copy(unlocked = true))
        if (totalFilledCells >= 1_000_000) unlocked.add(ALL[17].copy(unlocked = true))
        return unlocked
    }
}
