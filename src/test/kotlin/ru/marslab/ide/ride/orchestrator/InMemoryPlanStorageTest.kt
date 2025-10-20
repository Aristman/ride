package ru.marslab.ide.ride.orchestrator

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.impl.InMemoryPlanStorage

class InMemoryPlanStorageTest {

    private lateinit var storage: InMemoryPlanStorage

    @BeforeTest
    fun setUp() {
        storage = InMemoryPlanStorage()
    }

    @Test
    fun `should save and load plan correctly`() = runTest {
        // Given
        val plan = createTestPlan()

        // When
        val planId = storage.save(plan)
        val loadedPlan = storage.load(planId)

        // Then
        assertNotNull(planId)
        assertNotNull(loadedPlan)
        assertEquals(plan.id, loadedPlan?.id)
        assertEquals(plan.originalRequest, loadedPlan?.originalRequest)
        assertEquals(plan.currentState, loadedPlan?.currentState)
    }

    @Test
    fun `should return null for non-existent plan`() = runTest {
        // When
        val loadedPlan = storage.load("non-existent-id")

        // Then
        assertNull(loadedPlan)
    }

    @Test
    fun `should update existing plan`() = runTest {
        // Given
        val plan = createTestPlan()
        val planId = storage.save(plan)

        // When
        val updatedPlan = plan.copy(
            currentState = PlanState.COMPLETED,
            version = plan.version + 1
        )
        storage.update(updatedPlan)
        val loadedPlan = storage.load(planId)

        // Then
        assertNotNull(loadedPlan)
        assertEquals(PlanState.COMPLETED, loadedPlan?.currentState)
        assertEquals(plan.version + 1, loadedPlan?.version)
    }

    @Test
    fun `should create new plan when updating non-existent plan`() = runTest {
        // Given
        val plan = createTestPlan()

        // When
        storage.update(plan)
        val loadedPlan = storage.load(plan.id)

        // Then
        assertNotNull(loadedPlan)
        assertEquals(plan.id, loadedPlan?.id)
        assertEquals(1, loadedPlan?.version) // Should start with version 1
    }

    @Test
    fun `should delete plan correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        val planId = storage.save(plan)
        assertTrue(storage.exists(planId))

        // When
        storage.delete(planId)

        // Then
        assertFalse(storage.exists(planId))
        assertNull(storage.load(planId))
    }

    @Test
    fun `should check plan existence correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        val planId = storage.save(plan)

        // When & Then
        assertTrue(storage.exists(planId))
        assertFalse(storage.exists("non-existent-id"))
    }

    @Test
    fun `should list active plans correctly`() = runTest {
        // Given
        val activePlan = createTestPlan(PlanState.IN_PROGRESS)
        val completedPlan = createTestPlan(PlanState.COMPLETED)
        val failedPlan = createTestPlan(PlanState.FAILED)
        val cancelledPlan = createTestPlan(PlanState.CANCELLED)

        storage.save(activePlan)
        storage.save(completedPlan)
        storage.save(failedPlan)
        storage.save(cancelledPlan)

        // When
        val activePlans = storage.listActive()

        // Then
        assertEquals(1, activePlans.size)
        assertEquals(activePlan.id, activePlans.first().id)
    }

    @Test
    fun `should list plans by state correctly`() = runTest {
        // Given
        val createdPlan = createTestPlan(PlanState.CREATED)
        val inProgressPlan = createTestPlan(PlanState.IN_PROGRESS)
        val completedPlan1 = createTestPlan(PlanState.COMPLETED)
        val completedPlan2 = createTestPlan(PlanState.COMPLETED)

        storage.save(createdPlan)
        storage.save(inProgressPlan)
        storage.save(completedPlan1)
        storage.save(completedPlan2)

        // When
        val completedPlans = storage.listByState(PlanState.COMPLETED)

        // Then
        assertEquals(2, completedPlans.size)
        assertTrue(completedPlans.any { it.id == completedPlan1.id })
        assertTrue(completedPlans.any { it.id == completedPlan2.id })
    }

    @Test
    fun `should list completed plans with pagination`() = runTest {
        // Given
        val completedPlans = (1..5).map { createTestPlan(PlanState.COMPLETED) }
        completedPlans.forEach { storage.save(it) }

        // When
        val firstPage = storage.listCompleted(limit = 2, offset = 0)
        val secondPage = storage.listCompleted(limit = 2, offset = 2)
        val thirdPage = storage.listCompleted(limit = 2, offset = 4)

        // Then
        assertEquals(2, firstPage.size)
        assertEquals(2, secondPage.size)
        assertEquals(1, thirdPage.size)

        // Verify that plans are sorted by completion time (newest first)
        val allPlans = firstPage + secondPage + thirdPage
        assertEquals(5, allPlans.size)
    }

    @Test
    fun `should list failed plans`() = runTest {
        // Given
        val failedPlan1 = createTestPlan(PlanState.FAILED)
        val failedPlan2 = createTestPlan(PlanState.FAILED)
        val completedPlan = createTestPlan(PlanState.COMPLETED)

        storage.save(failedPlan1)
        storage.save(failedPlan2)
        storage.save(completedPlan)

        // When
        val failedPlans = storage.listFailed()

        // Then
        assertEquals(2, failedPlans.size)
        assertTrue(failedPlans.any { it.id == failedPlan1.id })
        assertTrue(failedPlans.any { it.id == failedPlan2.id })
    }

    @Test
    fun `should find plans by user request`() = runTest {
        // Given
        val userRequestId = "test-user-request"
        val plan1 = createTestPlan(userRequestId = userRequestId)
        val plan2 = createTestPlan(userRequestId = userRequestId)
        val plan3 = createTestPlan(userRequestId = "other-request")

        storage.save(plan1)
        storage.save(plan2)
        storage.save(plan3)

        // When
        val foundPlans = storage.findByUserRequest(userRequestId)

        // Then
        assertEquals(2, foundPlans.size)
        assertTrue(foundPlans.any { it.id == plan1.id })
        assertTrue(foundPlans.any { it.id == plan2.id })
    }

    @Test
    fun `should search plans by content`() = runTest {
        // Given
        val plan1 = createTestPlan(originalRequest = "Analyze code for bugs")
        val plan2 = createTestPlan(originalRequest = "Refactor the architecture")
        val plan3 = createTestPlan(originalRequest = "Code review and bug analysis")

        storage.save(plan1)
        storage.save(plan2)
        storage.save(plan3)

        // When
        val bugPlans = storage.searchByContent("bug")
        val refactorPlans = storage.searchByContent("refactor")

        // Then
        assertEquals(2, bugPlans.size) // plan1 and plan3 contain "bug"
        assertEquals(1, refactorPlans.size) // only plan2 contains "refactor"
    }

    @Test
    fun `should find plans by time range`() = runTest {
        // Given
        val now = Clock.System.now()
        val oneHourAgo = now.minus(1, DateTimeUnit.HOUR)
        val twoHoursAgo = now.minus(2, DateTimeUnit.HOUR)

        val oldPlan = createTestPlan(createdAt = twoHoursAgo)
        val newPlan = createTestPlan(createdAt = oneHourAgo)
        val futurePlan = createTestPlan(createdAt = now.plus(1, DateTimeUnit.HOUR))

        storage.save(oldPlan)
        storage.save(newPlan)
        storage.save(futurePlan)

        // When
        val plansInRange = storage.findByTimeRange(twoHoursAgo.minus(1, DateTimeUnit.MINUTE), now)

        // Then
        assertEquals(2, plansInRange.size)
        assertTrue(plansInRange.any { it.id == oldPlan.id })
        assertTrue(plansInRange.any { it.id == newPlan.id })
        assertFalse(plansInRange.any { it.id == futurePlan.id })
    }

    @Test
    fun `should cleanup old plans`() = runTest {
        // Given
        val now = Clock.System.now()
        val cutoffTime = now.minus(1, DateTimeUnit.HOUR)

        val oldCompletedPlan = createTestPlan(
            state = PlanState.COMPLETED,
            createdAt = now.minus(2, DateTimeUnit.HOUR)
        )
        val oldCancelledPlan = createTestPlan(
            state = PlanState.CANCELLED,
            createdAt = now.minus(3, DateTimeUnit.HOUR)
        )
        val newActivePlan = createTestPlan(
            state = PlanState.IN_PROGRESS,
            createdAt = now.minus(30, DateTimeUnit.MINUTE)
        )
        val newCompletedPlan = createTestPlan(
            state = PlanState.COMPLETED,
            createdAt = now.minus(30, DateTimeUnit.MINUTE)
        )

        storage.save(oldCompletedPlan)
        storage.save(oldCancelledPlan)
        storage.save(newActivePlan)
        storage.save(newCompletedPlan)

        // When
        val deletedCount = storage.cleanup(
            olderThan = cutoffTime,
            states = setOf(PlanState.COMPLETED, PlanState.CANCELLED)
        )

        // Then
        assertEquals(2, deletedCount)
        assertFalse(storage.exists(oldCompletedPlan.id))
        assertFalse(storage.exists(oldCancelledPlan.id))
        assertTrue(storage.exists(newActivePlan.id))
        assertTrue(storage.exists(newCompletedPlan.id))
    }

    @Test
    fun `should provide storage stats`() = runTest {
        // Given
        val activePlan = createTestPlan(PlanState.IN_PROGRESS)
        val completedPlan = createTestPlan(PlanState.COMPLETED)
        val failedPlan = createTestPlan(PlanState.FAILED)
        val cancelledPlan = createTestPlan(PlanState.CANCELLED)

        storage.save(activePlan)
        storage.save(completedPlan)
        storage.save(failedPlan)
        storage.save(cancelledPlan)

        // When
        val stats = storage.getStorageStats()

        // Then
        assertEquals(4, stats.totalPlans)
        assertEquals(1, stats.activePlans)
        assertEquals(1, stats.completedPlans)
        assertEquals(1, stats.failedPlans)
        assertEquals(1, stats.cancelledPlans)
        assertTrue(stats.storageSizeBytes > 0)
        assertNotNull(stats.oldestPlan)
        assertNotNull(stats.newestPlan)
    }

    @Test
    fun `should return empty stats for empty storage`() = runTest {
        // When
        val stats = storage.getStorageStats()

        // Then
        assertEquals(0, stats.totalPlans)
        assertEquals(0, stats.activePlans)
        assertEquals(0, stats.completedPlans)
        assertEquals(0, stats.failedPlans)
        assertEquals(0, stats.cancelledPlans)
        assertEquals(0L, stats.storageSizeBytes)
        assertNull(stats.oldestPlan)
        assertNull(stats.newestPlan)
    }

    @Test
    fun `should not support backup and restore`() = runTest {
        // Given
        val plan = createTestPlan()
        storage.save(plan)

        // When & Then
        assertFalse(storage.backup("/tmp/backup"))
        assertFalse(storage.restore("/tmp/backup"))
    }

    @Test
    fun `should clear all plans`() = runTest {
        // Given
        val plan1 = createTestPlan()
        val plan2 = createTestPlan()
        storage.save(plan1)
        storage.save(plan2)
        assertEquals(2, storage.size())

        // When
        storage.clear()

        // Then
        assertEquals(0, storage.size())
        assertTrue(storage.isEmpty())
        assertNull(storage.load(plan1.id))
        assertNull(storage.load(plan2.id))
    }

    @Test
    fun `should provide size and empty status`() = runTest {
        // Given
        assertTrue(storage.isEmpty())
        assertEquals(0, storage.size())

        // When
        val plan = createTestPlan()
        storage.save(plan)

        // Then
        assertFalse(storage.isEmpty())
        assertEquals(1, storage.size())
    }

    @Test
    fun `should get all plans for debugging`() = runTest {
        // Given
        val plan1 = createTestPlan()
        val plan2 = createTestPlan()
        storage.save(plan1)
        storage.save(plan2)

        // When
        val allPlans = storage.getAllPlans()

        // Then
        assertEquals(2, allPlans.size)
        assertTrue(allPlans.any { it.id == plan1.id })
        assertTrue(allPlans.any { it.id == plan2.id })
    }

    private fun createTestPlan(
        state: PlanState = PlanState.CREATED,
        userRequestId: String = "test-user-request",
        originalRequest: String = "Test request",
        createdAt: Instant = Clock.System.now()
    ): ExecutionPlan {
        return ExecutionPlan(
            id = "plan-${Clock.System.now().epochSeconds}-${(0..9999).random()}",
            userRequestId = userRequestId,
            originalRequest = originalRequest,
            analysis = RequestAnalysis(
                taskType = TaskType.CODE_ANALYSIS,
                requiredTools = setOf(AgentType.PROJECT_SCANNER),
                context = ExecutionContext(),
                parameters = emptyMap(),
                requiresUserInput = false,
                estimatedComplexity = ComplexityLevel.MEDIUM,
                estimatedSteps = 3
            ),
            steps = emptyList(),
            currentState = state,
            createdAt = createdAt
        )
    }
}