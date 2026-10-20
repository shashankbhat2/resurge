package com.resurgeai.core.data.weight

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/** Told whenever this phone adds, edits or removes a weigh-in, e.g. to refresh the home screen widget. */
fun interface WeightChangeListener {
    fun onWeightsChanged()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WeightChangeModule {
    @Multibinds
    abstract fun weightChangeListeners(): Set<WeightChangeListener>
}
