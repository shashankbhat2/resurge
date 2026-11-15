package com.resurgeai.feature.food

import com.resurgeai.core.model.BarcodeProduct
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the log screen hands to the review screen: a prepared photo (a meal or a nutrition label),
 * a typed or spoken description, a scanned [barcode], or a [product] already found by barcode
 * (which needs no analysis).
 */
class MealDraft(
    val photo: PreparedPhoto?,
    val text: String?,
    val barcode: String? = null,
    val product: BarcodeProduct? = null,
)

/** Holds the next meal to analyse between the log and review screens (photos are too big for nav args). */
@Singleton
class MealDraftStore @Inject constructor() {
    @Volatile private var draft: MealDraft? = null

    fun put(value: MealDraft) {
        draft = value
    }

    fun take(): MealDraft? = draft.also { draft = null }
}
