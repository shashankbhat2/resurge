package com.resurgeai.feature.food

import javax.inject.Inject
import javax.inject.Singleton

/** What the log screen hands to the review screen: a prepared photo and/or a typed description. */
class MealDraft(val photo: PreparedPhoto?, val text: String?)

/** Holds the next meal to analyse between the log and review screens (photos are too big for nav args). */
@Singleton
class MealDraftStore @Inject constructor() {
    @Volatile private var draft: MealDraft? = null

    fun put(value: MealDraft) {
        draft = value
    }

    fun take(): MealDraft? = draft.also { draft = null }
}
