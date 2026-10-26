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
    SCAN("scan"), TEXT("text"), MANUAL("manual"), BARCODE("barcode");

    companion object {
        fun fromWire(value: String?): MealSource = entries.firstOrNull { it.wire == value } ?: MANUAL
    }
}

/**
 * Calories, macros and micronutrients. Used per 100 g on items and as totals on meals. A null
 * micronutrient means the source doesn't report it (not zero); a total is null only if no food in it
 * reports that nutrient.
 */
data class Nutrition(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val micros: Map<Micro, Double> = emptyMap(),
) {
    operator fun plus(o: Nutrition) = Nutrition(
        kcal + o.kcal, protein + o.protein, carbs + o.carbs, fat + o.fat, fiber + o.fiber,
        micros = (micros.keys + o.micros.keys).associateWith { (micros[it] ?: 0.0) + (o.micros[it] ?: 0.0) },
    )

    fun scaled(factor: Double) = Nutrition(
        kcal * factor, protein * factor, carbs * factor, fat * factor, fiber * factor,
        micros = micros.mapValues { it.value * factor },
    )

    operator fun get(micro: Micro): Double? = micros[micro]

    companion object {
        val Zero = Nutrition()
    }
}

/**
 * Micronutrients tracked beyond the macros, with a daily guide for adults (ICMR-NIN 2020 dietary
 * guidelines for Indians where they set one; WHO for the limits). [limit] marks the ones to stay
 * under rather than reach. [wire] is the key in Firestore and from the analyzeMeal function.
 */
enum class Micro(val wire: String, val label: String, val unit: String, val daily: Double, val limit: Boolean = false) {
    SUGAR("sugar", "Sugar", "g", 50.0, limit = true),
    SAT_FAT("satFat", "Saturated fat", "g", 22.0, limit = true),
    SODIUM("sodium", "Sodium", "mg", 2000.0, limit = true),
    CHOLESTEROL("cholesterol", "Cholesterol", "mg", 300.0, limit = true),
    POTASSIUM("potassium", "Potassium", "mg", 3500.0),
    CALCIUM("calcium", "Calcium", "mg", 1000.0),
    IRON("iron", "Iron", "mg", 19.0),
    MAGNESIUM("magnesium", "Magnesium", "mg", 440.0),
    ZINC("zinc", "Zinc", "mg", 17.0),
    VIT_A("vitA", "Vitamin A", "µg", 1000.0),
    VIT_C("vitC", "Vitamin C", "mg", 80.0),
    VIT_D("vitD", "Vitamin D", "µg", 15.0),
    VIT_B12("vitB12", "Vitamin B12", "µg", 2.5),
    FOLATE("folate", "Folate", "µg", 300.0);

    companion object {
        fun fromWire(value: String): Micro? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One food in a meal. Nutrition is stored per 100 g so changing [grams] rescales everything on the
 * device. [confidence] (0–1) is the model's own estimate; null for items the user typed in.
 * [source] says where the numbers came from; null on meals logged before sources existed.
 */
data class FoodItem(
    val name: String,
    val grams: Double,
    val per100g: Nutrition,
    val confidence: Double? = null,
    val source: FoodSource? = null,
) {
    val nutrition: Nutrition get() = per100g.scaled(grams / 100.0)
    val lowConfidence: Boolean get() = (confidence ?: 1.0) < 0.6
    /** The numbers are an estimate, or a web page that didn't show them. */
    val unverified: Boolean get() = source?.verified == false
}

/**
 * Where a food's nutrition came from: [id] is the table entry ("ifct:A015", "fdc:171287",
 * "off:8901…", "label:…", "components", "estimate"), [name] the table ("IFCT 2017 (ICMR-NIN)") and
 * [detail] the entry's own name or the ingredients a dish was built from.
 */
data class FoodSource(val id: String, val name: String, val detail: String? = null, val verified: Boolean = true) {
    val isEstimate: Boolean get() = id == "estimate"
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

/** What the meal agent returns for a photo, description, label or barcode; the review screen edits it before logging. */
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

/** A packaged product found by barcode, with no model call. */
data class BarcodeProduct(
    val name: String,
    val per100g: Nutrition,
    val servingGrams: Double?,
    val source: FoodSource,
) {
    fun toItem(): FoodItem = FoodItem(name, servingGrams ?: 100.0, per100g, confidence = 1.0, source = source)
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
