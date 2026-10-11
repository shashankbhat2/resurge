package com.resurgeai.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

enum class MealType(val wire: String, val label: String) {
    BREAKFAST("breakfast", "Breakfast"),
    LUNCH("lunch", "Lunch"),
    DINNER("dinner", "Dinner"),
    SNACK("snack", "Snacks");

    companion object {
        fun fromWire(value: String?): MealType = entries.firstOrNull { it.wire == value } ?: SNACK

        /** Best guess from the time of day. */
        fun forTime(time: LocalTime): MealType = when (time.hour) {
            in 5..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 18..22 -> DINNER
            else -> SNACK
        }
    }
}

enum class MealSource(val wire: String) {
    SCAN("scan"), TEXT("text"), MANUAL("manual");

    companion object {
        fun fromWire(value: String?): MealSource = entries.firstOrNull { it.wire == value } ?: MANUAL
    }
}

/** Calories and macros. Used per 100 g on items and as totals on meals. */
data class Nutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
) {
    operator fun plus(o: Nutrition) = Nutrition(kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat, fiber + o.fiber)
    fun scaled(factor: Double) = Nutrition(kcal * factor, protein * factor, carbs * factor, fat * factor, fiber * factor)

    companion object {
        val Zero = Nutrition()
    }
}

/**
 * One food in a meal. Nutrition is stored per 100 g so changing [grams] rescales everything on the
 * device. [confidence] (0–1) is the model's own estimate; null for items the user typed in.
 */
data class FoodItem(
    val name: String,
    val grams: Double,
    val per100g: Nutrition,
    val confidence: Double? = null,
) {
    val nutrition: Nutrition get() = per100g.scaled(grams / 100.0)
    val lowConfidence: Boolean get() = (confidence ?: 1.0) < 0.6
}

data class Meal(
    val id: String,
    val type: MealType,
    val loggedAt: Instant,
    val date: LocalDate,
    val source: MealSource,
    val title: String,
    val items: List<FoodItem>,
    /** Small JPEG of the photo, for scanned meals. */
    val thumbnail: ByteArray? = null,
) {
    val totals: Nutrition get() = items.fold(Nutrition.Zero) { acc, i -> acc + i.nutrition }

    override fun equals(other: Any?): Boolean = other is Meal && other.id == id && other.type == type &&
        other.loggedAt == loggedAt && other.title == title && other.items == items &&
        java.util.Arrays.equals(other.thumbnail, thumbnail)

    override fun hashCode(): Int = id.hashCode()
}

/** What the model returns for a photo or description; the review screen edits it before logging. */
data class MealEstimate(
    val title: String,
    val items: List<FoodItem>,
    /** One clarifying question when an answer would change the estimate a lot. */
    val question: String? = null,
    val options: List<String> = emptyList(),
    val notes: String? = null,
)

/** Daily macro targets derived from the calorie target (30% protein, 40% carbs, 30% fat). */
data class MacroTargets(val protein: Int, val carbs: Int, val fat: Int) {
    companion object {
        fun from(calories: Int) = MacroTargets(
            protein = (calories * 0.30 / 4).roundToInt(),
            carbs = (calories * 0.40 / 4).roundToInt(),
            fat = (calories * 0.30 / 9).roundToInt(),
        )
    }
}

/** Breakfast, lunch and dinner reminders; each can be turned off. */
data class MealReminders(
    val breakfast: LocalTime? = LocalTime.of(8, 30),
    val lunch: LocalTime? = LocalTime.of(13, 0),
    val dinner: LocalTime? = LocalTime.of(20, 0),
) {
    fun time(type: MealType): LocalTime? = when (type) {
        MealType.BREAKFAST -> breakfast
        MealType.LUNCH -> lunch
        MealType.DINNER -> dinner
        MealType.SNACK -> null
    }

    fun with(type: MealType, time: LocalTime?): MealReminders = when (type) {
        MealType.BREAKFAST -> copy(breakfast = time)
        MealType.LUNCH -> copy(lunch = time)
        MealType.DINNER -> copy(dinner = time)
        MealType.SNACK -> this
    }

    companion object {
        val DEFAULT_TIMES = mapOf(
            MealType.BREAKFAST to LocalTime.of(8, 30),
            MealType.LUNCH to LocalTime.of(13, 0),
            MealType.DINNER to LocalTime.of(20, 0),
        )
    }
}
