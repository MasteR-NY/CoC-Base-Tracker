package com.villageclock.app

import android.content.Context

/** Display metadata only; never applies another multiplier to exported work. */
object ActiveEffects {
    class Effect(@JvmField val key: String, @JvmField val name: String, @JvmField val rate: String,
                 @JvmField val endsAt: Long, @JvmField val icon: String,
                 @JvmField val builderBase: Boolean, @JvmField val event: Boolean)
    @JvmStatic fun effect(key: String, endsAt: Long): Effect? = when (key) {
        "builder_boost" -> Effect(key, "Builder Potion", "10×", endsAt, "effect_builder_potion", false, false)
        "builder_consumable" -> Effect(key, "Builder Bite", "2×", endsAt, "effect_builder_bite", false, false)
        "lab_boost" -> Effect(key, "Research Potion", "24×", endsAt, "effect_research_potion", false, false)
        "pet_boost" -> Effect(key, "Pet Potion", "24×", endsAt, "effect_pet_potion", false, false)
        "lab_consumable" -> Effect(key, "Study Soup", "+3×", endsAt, "effect_study_soup", false, false)
        "clocktower_boost" -> Effect(key, "Clock Tower", "10×", endsAt, "effect_clock_tower", true, false)
        else -> null
    }
    @JvmStatic fun fromJobs(jobs: List<Village.Upgrade>): List<Effect> {
        val result = linkedMapOf<String, Effect>()
        for (job in jobs) for (boost in job.timing?.boosts.orEmpty()) {
            val effect = effect(boost.family, job.snapshotAt + boost.remainingSeconds * 1000) ?: continue
            if ((result[effect.key]?.endsAt ?: Long.MIN_VALUE) < effect.endsAt) result[effect.key] = effect
        }
        return result.values.toList()
    }
    @JvmStatic fun active(village: Village?, now: Long): List<Effect> {
        if (village == null) return emptyList()
        val result = village.effects.filter { it.endsAt > now }.toMutableList()
        if (village.townHall >= 3 && FastEvents.hammerJam(now)) result.add(Effect("hammer-jam-2026", "Hammer Jam", "50% off", FastEvents.JAM_END, "effect_hammer_jam", false, true))
        return result.sortedBy { it.endsAt }
    }
    @JvmStatic fun icon(c: Context, effect: Effect): Int = c.resources.getIdentifier(effect.icon, "drawable", c.packageName).takeIf { it != 0 } ?: R.drawable.ic_clock
    @JvmStatic fun nextChange(village: Village?, now: Long): Long = active(village, now).minOfOrNull { it.endsAt } ?: Long.MAX_VALUE
}
