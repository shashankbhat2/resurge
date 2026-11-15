package com.resurgeai.feature.food

import androidx.lifecycle.SavedStateHandle
import com.resurgeai.core.data.food.MealAnalysisException
import com.resurgeai.core.data.food.MealsRepository
import com.resurgeai.core.model.BarcodeProduct
import com.resurgeai.core.model.FoodItem
import com.resurgeai.core.model.FoodSource
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
        return MealReviewViewModel(repo, store, SavedStateHandle(), mockk(relaxed = true))
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
    fun `a product found by barcode goes straight to review at its serving size, with no analysis`() {
        val maggi = BarcodeProduct("Maggi 2-Minute Noodles", Nutrition(kcal = 384.0, protein = 8.2, carbs = 59.6, fat = 12.5), 70.0, FoodSource("off:8901058000290", "Open Food Facts"))
        val vm = vm(MealDraft(null, null, barcode = "8901058000290", product = maggi))
        val s = vm.state.value
        assertEquals(MealSource.BARCODE, s.source)
        assertEquals("Maggi 2-Minute Noodles", s.title)
        assertEquals(268.8, s.totals.kcal, 0.01)
        assertTrue(s.canLog)
        coVerify(exactly = 0) { repo.analyze(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a label photo for an unknown barcode is read with the barcode attached`() {
        coEvery { repo.analyze(any(), any(), any(), any(), any()) } returns MealEstimate("Aloo bhujia", listOf(rice.copy(name = "Aloo bhujia", grams = 30.0)))
        val vm = vm(MealDraft(photo, null, barcode = "8904063200011"))
        coVerify { repo.analyze(photo.jpeg, null, any(), null, "8904063200011") }
        assertEquals(MealSource.BARCODE, vm.state.value.source)
        assertEquals(MealReviewViewModel.LABEL_STEPS, MealReviewViewModel.stepsFor(vm.state.value.copy(fixes = emptyList())))
    }

    @Test
    fun `without a draft there is nothing to review`() {
        assertTrue(vm(null).state.value.error != null)
    }

    @Test
    fun `servings scale every food and carry into a re-estimate`() {
        coEvery { repo.analyze(any(), any(), any(), any()) } returns MealEstimate("Paneer curry with rice", listOf(rice, curry))
        val vm = vm(MealDraft(photo, null))
        vm.setServings(2)
        assertEquals(1180.0, vm.state.value.totals.kcal, 0.001)
        vm.setServings(1)
        assertEquals(590.0, vm.state.value.totals.kcal, 0.001)

        vm.setServings(2)
        vm.correct(listOf("No oil or ghee"))
        assertEquals(listOf(400.0, 300.0), vm.state.value.items.map { it.item.grams })
    }

    @Test
    fun `corrections are sent with the description and stack`() {
        coEvery { repo.analyze(any(), any(), any(), any()) } returns MealEstimate("Roti dal", listOf(rice))
        val vm = vm(MealDraft(null, "2 rotis and dal"))
        vm.correct(listOf("Smaller portion", " "))
        coVerify { repo.analyze(null, "2 rotis and dal\nCorrections from the person: Smaller portion.", any(), null) }
        vm.correct(listOf("No oil or ghee"))
        coVerify { repo.analyze(null, "2 rotis and dal\nCorrections from the person: Smaller portion; No oil or ghee.", any(), null) }
        assertTrue(vm.state.value.canCorrect)
    }
}
