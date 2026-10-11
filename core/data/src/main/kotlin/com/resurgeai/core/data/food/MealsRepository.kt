package com.resurgeai.core.data.food

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.resurgeai.core.data.listen
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.Meal
import com.resurgeai.core.model.MealEstimate
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.MealType
import com.resurgeai.core.model.Nutrition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown by [MealsRepository.analyze] with a message ready to show. */
class MealAnalysisException(message: String) : Exception(message)

/**
 * Food log:
 *   users/{uid}/meals/{id}        type, loggedAtMs, localDate, source, title, items[] (nutrition per 100 g),
 *                                 thumb (small JPEG), aiTotals (the model's original totals)
 *   users/{uid}/waterDays/{date}  { date, ml }
 * Photos are analysed by the analyzeMeal function; only a thumbnail is stored.
 */
@Singleton
class MealsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
) {
    private fun user() = auth.currentUser?.uid?.let { firestore.collection("users").document(it) }

    fun meals(since: LocalDate): Flow<List<Meal>> {
        val ref = user() ?: return flowOf(emptyList())
        return listen(ref.collection("meals").whereGreaterThanOrEqualTo("localDate", since.toString())) { docs ->
            docs.mapNotNull(::toMeal).sortedByDescending { it.loggedAt }
        }
    }

    suspend fun meal(id: String): Meal? = user()?.collection("meals")?.document(id)?.get()?.await()?.let(::toMeal)

    /** Water drunk per day, in ml. */
    fun water(since: LocalDate): Flow<Map<LocalDate, Int>> {
        val ref = user() ?: return flowOf(emptyMap())
        return listen(ref.collection("waterDays").whereGreaterThanOrEqualTo("date", since.toString())) { docs ->
            docs.mapNotNull { d ->
                val date = runCatching { LocalDate.parse(d.id) }.getOrNull() ?: return@mapNotNull null
                date to (d.getLong("ml") ?: 0L).toInt()
            }.toMap()
        }
    }

    fun addWater(date: LocalDate, ml: Int) {
        user()?.collection("waterDays")?.document(date.toString())?.set(
            mapOf("date" to date.toString(), "ml" to FieldValue.increment(ml.toLong()), "updatedAt" to FieldValue.serverTimestamp()),
            SetOptions.merge(),
        )
    }

    /** Whether any meal of [type] is logged on [date] (cache first), for the meal reminder. */
    suspend fun hasMeal(date: LocalDate, type: MealType): Boolean {
        val ref = user() ?: return false
        val docs = ref.collection("meals").whereEqualTo("localDate", date.toString()).get().await().documents
        return docs.any { it.getString("type") == type.wire }
    }

    /** Saves a new meal (id null) or replaces an existing one. */
    fun save(
        id: String?,
        type: MealType,
        loggedAt: Instant,
        source: MealSource,
        title: String,
        items: List<FoodItem>,
        thumbnail: ByteArray?,
        aiEstimate: MealEstimate?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String? {
        val col = user()?.collection("meals") ?: return null
        val doc = if (id == null) col.document() else col.document(id)
        val data = buildMap<String, Any?> {
            put("type", type.wire)
            put("loggedAtMs", loggedAt.toEpochMilli())
            put("localDate", loggedAt.atZone(zone).toLocalDate().toString())
            put("source", source.wire)
            put("title", title.trim().ifEmpty { items.firstOrNull()?.name ?: "Meal" }.take(80))
            put("items", items.take(MAX_ITEMS).map { it.toMap() })
            thumbnail?.let { put("thumb", Blob.fromBytes(it)) }
            aiEstimate?.let { e -> put("aiTotals", e.items.fold(Nutrition.Zero) { acc, i -> acc + i.nutrition }.toMap()) }
            put("updatedAt", FieldValue.serverTimestamp())
        }
        doc.set(data, if (id == null) SetOptions.merge() else SetOptions.mergeFields(data.keys.toList()))
        return doc.id
    }

    fun delete(id: String) {
        user()?.collection("meals")?.document(id)?.delete()
    }

    /** Restores a deleted meal as it was. */
    fun restore(meal: Meal) {
        save(meal.id, meal.type, meal.loggedAt, meal.source, meal.title, meal.items, meal.thumbnail, null)
    }

    /**
     * Asks the coach model to identify foods and estimate portions from a photo (JPEG) and/or a
     * description. [answer] replies to a previous clarifying question.
     */
    suspend fun analyze(photo: ByteArray?, text: String?, mealType: MealType, answer: String? = null): MealEstimate {
        require(photo != null || !text.isNullOrBlank()) { "Nothing to analyse" }
        val data = buildMap<String, Any?> {
            photo?.let { put("image", Base64.encodeToString(it, Base64.NO_WRAP)) }
            text?.trim()?.takeIf { it.isNotEmpty() }?.let { put("text", it) }
            answer?.let { put("answer", it) }
            put("mealType", mealType.wire)
            put("localTime", LocalTime.now().withNano(0).toString())
        }
        val result = try {
            functions.getHttpsCallable("analyzeMeal").withTimeout(90, TimeUnit.SECONDS).call(data).await()
        } catch (e: FirebaseFunctionsException) {
            throw MealAnalysisException(
                when (e.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> e.message ?: "Daily scan limit reached."
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT -> e.message ?: "Couldn't read that. Try another photo."
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
                    else -> "Couldn't analyse the meal. Try again."
                },
            )
        } catch (e: Exception) {
            throw MealAnalysisException("Couldn't reach the server. Check your connection and try again.")
        }
        return parseEstimate(result.getData() as? Map<*, *> ?: throw MealAnalysisException("Couldn't analyse the meal. Try again."))
    }

    private fun toMeal(d: DocumentSnapshot): Meal? {
        val loggedAt = d.getLong("loggedAtMs")?.let(Instant::ofEpochMilli) ?: return null
        @Suppress("UNCHECKED_CAST")
        val items = (d.get("items") as? List<Map<String, Any?>>).orEmpty().mapNotNull(::itemFrom)
        return Meal(
            id = d.id,
            type = MealType.fromWire(d.getString("type")),
            loggedAt = loggedAt,
            date = d.getString("localDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: loggedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            source = MealSource.fromWire(d.getString("source")),
            title = d.getString("title") ?: "Meal",
            items = items,
            thumbnail = d.getBlob("thumb")?.toBytes(),
        )
    }

    companion object {
        const val MAX_ITEMS = 20

        fun FoodItem.toMap(): Map<String, Any?> = mapOf(
            "name" to name.take(60),
            "grams" to grams,
            "kcal" to per100g.kcal,
            "protein" to per100g.protein,
            "carbs" to per100g.carbs,
            "fat" to per100g.fat,
            "fiber" to per100g.fiber,
            "confidence" to confidence,
        )

        private fun Nutrition.toMap() = mapOf("kcal" to kcal, "protein" to protein, "carbs" to carbs, "fat" to fat, "fiber" to fiber)

        fun itemFrom(m: Map<*, *>): FoodItem? {
            val name = m["name"] as? String ?: return null
            fun num(key: String) = (m[key] as? Number)?.toDouble() ?: 0.0
            return FoodItem(
                name = name,
                grams = num("grams"),
                per100g = Nutrition(num("kcal"), num("protein"), num("carbs"), num("fat"), num("fiber")),
                confidence = (m["confidence"] as? Number)?.toDouble(),
            )
        }

        /** Parses the analyzeMeal response; item nutrition arrives per 100 g. */
        fun parseEstimate(map: Map<*, *>): MealEstimate {
            val items = (map["items"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::itemFrom) }
            if (items.isEmpty()) throw MealAnalysisException("No food found. Try a clearer photo or describe the meal.")
            return MealEstimate(
                title = map["title"] as? String ?: items.first().name,
                items = items,
                question = (map["question"] as? String)?.takeIf { it.isNotBlank() },
                options = (map["options"] as? List<*>).orEmpty().filterIsInstance<String>(),
                notes = (map["notes"] as? String)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
