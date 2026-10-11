package com.resurgeai.feature.food

import androidx.lifecycle.SavedStateHandle
import com.resurgeai.core.data.food.MealAnalysisException
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.MealEstimate
import com.resurgeai.core.model.MealSource
import com.resurgeai.core.model.Nutrition
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MealReviewViewModelTest {
    private val repo = mockk<MealsRepository>(relaxed = true)
    private val photo = PreparedPhoto(byteArrayOf(1, 2, 3), byteArrayOf(9))
    private val rice = FoodItem("Rice", 200.0, Nutrition(kcal = 130.0, protein = 2.7, carbs = 28.0, fat = 0.3), 0.9)
    private val curry = FoodItem("Paneer curry", 150.0, Nutrition(kcal = 220.0, protein = 9.0, carbs = 8.0, fat = 17.0), 0.7)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(draft: MealDraft?): MealReviewViewModel {
        val store = MealDraftStore().apply { draft?.let(::put) }
        return MealReviewViewModel(repo, store, SavedStateHandle())
    }

    @Test
    fun `a scanned photo is analysed, portions rescale totals, and logging saves the reviewed items`() {
        coEvery { repo.analyze(any(), any(), any(), any()) } returns MealEstimate("Paneer curry with rice", listOf(rice, curry), "Cooked in ghee?", listOf("Yes", "No"))
        val vm = vm(MealDraft(photo, null))
        val s = vm.state.value
        assertNull(s.analyzing)
        assertEquals(MealSource.SCAN, s.source)
        assertEquals(590.0, s.totals.kcal, 0.001)
        assertEquals("Cooked in ghee?", s.question)

        vm.setGrams(s.items.first().key, 100.0)
        assertEquals(460.0, vm.state.value.totals.kcal, 0.001)

        vm.log()
        verify {
            repo.save(null, any(), any(), MealSource.SCAN, "Paneer curry with rice", listOf(rice.copy(grams = 100.0), curry), photo.thumbnail, any())
        }
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `answering the question re-runs the estimate with the answer`() {
        coEvery { repo.analyze(any(), any(), any(), null) } returns MealEstimate("Curry", listOf(curry), "Fried?", listOf("Yes", "No"))
        coEvery { repo.analyze(any(), any(), any(), "Yes") } returns MealEstimate("Fried curry", listOf(curry.copy(name = "Fried paneer")))
        val vm = vm(MealDraft(photo, null))
        vm.answer("Yes")
        coVerify { repo.analyze(photo.jpeg, null, any(), "Yes") }
        assertEquals("Fried paneer", vm.state.value.items.single().item.name)
        assertNull(vm.state.value.question)
    }

    @Test
    fun `analysis errors are shown and can be retried`() {
        coEvery { repo.analyze(any(), any(), any(), any()) } throws MealAnalysisException("Daily scan limit reached.")
        val vm = vm(MealDraft(null, "2 rotis and dal"))
        assertEquals("Daily scan limit reached.", vm.state.value.error)
        assertEquals(MealSource.TEXT, vm.state.value.source)

        coEvery { repo.analyze(any(), any(), any(), any()) } returns MealEstimate("Roti dal", listOf(rice))
        vm.retry()
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun `without a draft there is nothing to review`() {
        assertTrue(vm(null).state.value.error != null)
    }
}
