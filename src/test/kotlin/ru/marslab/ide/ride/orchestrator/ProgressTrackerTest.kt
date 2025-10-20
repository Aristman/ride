package ru.marslab.ide.ride.orchestrator

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.orchestrator.ProgressTracker

class ProgressTrackerTest {

    private lateinit var progressTracker: ProgressTracker
    private lateinit var listener: ProgressListener

    @BeforeTest
    fun setUp() {
        progressTracker = ProgressTracker()
        listener = mockk(relaxUnitFun = true)
        progressTracker.addListener(listener)
    }

    @Test
    fun `should start tracking plan correctly`() = runTest {
        // Given
        val plan = createTestPlan()

        // When
        progressTracker.startTracking(plan)
        val progress = progressTracker.getProgress(plan.id)

        // Then
        assertNotNull(progress)
        assertEquals(plan.id, progress?.planId)
        assertEquals(plan.steps.size, progress?.totalSteps)
        assertEquals(0, progress?.completedSteps)
        assertEquals(0.0, progress?.currentStepProgress)
        assertFalse(progress?.isCompleted == true)
    }

    @Test
    fun `should update step progress correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        val stepId = plan.steps.first().id

        // When
        progressTracker.updateStepProgress(plan.id, stepId, 50.0)
        val progress = progressTracker.getProgress(plan.id)

        // Then
        assertEquals(50.0, progress?.currentStepProgress)
        verify { listener.onStepProgressUpdated(plan.id, stepId, 50.0, any(), any(), any()) }
    }

    @Test
    fun `should update step progress with status`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        val stepId = plan.steps.first().id

        // When
        progressTracker.updateStepProgress(
            planId = plan.id,
            stepId = stepId,
            progress = 75.0,
            status = StepStatus.IN_PROGRESS,
            message = "Processing..."
        )

        // Then
        val history = progressTracker.getStepHistory(plan.id)
        assertTrue(history.any { it.stepId == stepId && it.status == StepStatus.IN_PROGRESS })
    }

    @Test
    fun `should complete step correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        val stepId = plan.steps.first().id

        // When
        progressTracker.completeStep(plan.id, stepId, "Step result")
        val progress = progressTracker.getProgress(plan.id)

        // Then
        assertEquals(1, progress?.completedSteps)
        assertEquals(100.0, progress?.currentStepProgress)
        verify { listener.onStepCompleted(plan.id, stepId, "Step result", any(), any()) }

        val history = progressTracker.getStepHistory(plan.id)
        assertTrue(history.any { it.stepId == stepId && it.progress == 100.0 })
    }

    @Test
    fun `should fail step correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        val stepId = plan.steps.first().id

        // When
        progressTracker.failStep(plan.id, stepId, "Step failed")
        val progress = progressTracker.getProgress(plan.id)

        // Then
        assertEquals(1, progress?.failedSteps)
        assertEquals(0.0, progress?.currentStepProgress)
        verify { listener.onStepFailed(plan.id, stepId, "Step failed", any()) }

        val history = progressTracker.getStepHistory(plan.id)
        assertTrue(history.any { it.stepId == stepId && it.status == StepStatus.FAILED })
    }

    @Test
    fun `should finish tracking successfully`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        plan.steps.forEach { step ->
            progressTracker.completeStep(plan.id, step.id)
        }

        // When
        progressTracker.finishTracking(plan.id, true)
        val progress = progressTracker.getProgress(plan.id)

        // Then
        assertTrue(progress?.isCompleted == true)
        assertTrue(progress?.success == true)
        assertTrue(progress?.actualDurationMs ?: 0 > 0)
        verify { listener.onProgressFinished(plan.id, any()) }
    }

    @Test
    fun `should finish tracking with failure`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)

        // When
        progressTracker.finishTracking(plan.id, false)
        val progress = progressTracker.getProgress(plan.id)

        // Then
        assertTrue(progress?.isCompleted == true)
        assertFalse(progress?.success == true)
        verify { listener.onProgressFinished(plan.id, any()) }
    }

    @Test
    fun `should calculate overall progress correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)

        // Complete first step
        progressTracker.completeStep(plan.id, plan.steps[0].id)
        var overallProgress = progressTracker.getProgressPercentage(plan.id)
        assertEquals(33.33, overallProgress, 0.1) // 1/3 = 33.33%

        // Update second step to 50%
        progressTracker.updateStepProgress(plan.id, plan.steps[1].id, 50.0)
        overallProgress = progressTracker.getProgressPercentage(plan.id)
        assertEquals(50.0, overallProgress, 0.1) // (1 + 0.5)/3 = 50%

        // Complete second step
        progressTracker.completeStep(plan.id, plan.steps[1].id)
        overallProgress = progressTracker.getProgressPercentage(plan.id)
        assertEquals(66.67, overallProgress, 0.1) // 2/3 = 66.67%
    }

    @Test
    fun `should calculate ETA correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)

        // Initially ETA should be estimated duration
        var eta = progressTracker.getETA(plan.id)
        assertTrue(eta > 0)

        // Complete first step
        progressTracker.completeStep(plan.id, plan.steps[0].id)

        // ETA should be recalculated based on remaining steps
        eta = progressTracker.getETA(plan.id)
        assertTrue(eta > 0)

        // Complete all steps
        progressTracker.completeStep(plan.id, plan.steps[1].id)
        progressTracker.completeStep(plan.id, plan.steps[2].id)

        // ETA should be smaller when fewer steps remaining
        eta = progressTracker.getETA(plan.id)
        assertTrue(eta > 0)
    }

    @Test
    fun `should maintain step history`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        val stepId = plan.steps.first().id

        // When
        progressTracker.updateStepProgress(plan.id, stepId, 25.0)
        progressTracker.updateStepProgress(plan.id, stepId, 50.0)
        progressTracker.updateStepProgress(plan.id, stepId, 75.0)
        progressTracker.completeStep(plan.id, stepId)

        // Then
        val history = progressTracker.getStepHistory(plan.id)
        assertEquals(4, history.filter { it.stepId == stepId }.size)
        assertTrue(history.any { it.progress == 25.0 })
        assertTrue(history.any { it.progress == 50.0 })
        assertTrue(history.any { it.progress == 75.0 })
        assertTrue(history.any { it.progress == 100.0 })
    }

    @Test
    fun `should clear tracking data`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)
        progressTracker.updateStepProgress(plan.id, plan.steps.first().id, 50.0)

        // When
        progressTracker.clearTracking(plan.id)

        // Then
        assertNull(progressTracker.getProgress(plan.id))
        assertTrue(progressTracker.getStepHistory(plan.id).isEmpty())
    }

    @Test
    fun `should handle multiple plans tracking`() = runTest {
        // Given
        val plan1 = createTestPlan("plan1")
        val plan2 = createTestPlan("plan2")

        // When
        progressTracker.startTracking(plan1)
        progressTracker.startTracking(plan2)

        progressTracker.updateStepProgress(plan1.id, plan1.steps.first().id, 50.0)
        progressTracker.completeStep(plan2.id, plan2.steps.first().id)

        // Then
        val progress1 = progressTracker.getProgress(plan1.id)
        val progress2 = progressTracker.getProgress(plan2.id)

        assertEquals(50.0, progress1?.currentStepProgress)
        assertEquals(1, progress2?.completedSteps)
        assertEquals(0, progress1?.completedSteps)
    }

    @Test
    fun `should handle invalid plan IDs gracefully`() = runTest {
        // When & Then - should not throw exceptions
        progressTracker.updateStepProgress("invalid", "step", 50.0)
        progressTracker.completeStep("invalid", "step")
        progressTracker.failStep("invalid", "step", "error")
        progressTracker.finishTracking("invalid", true)

        assertNull(progressTracker.getProgress("invalid"))
        assertTrue(progressTracker.getStepHistory("invalid").isEmpty())
        assertEquals(-1L, progressTracker.getETA("invalid"))
        assertEquals(0.0, progressTracker.getProgressPercentage("invalid"))
    }

    @Test
    fun `should estimate duration based on complexity`() = runTest {
        // Given
        val lowComplexityPlan = createTestPlan(complexity = ComplexityLevel.LOW)
        val highComplexityPlan = createTestPlan(complexity = ComplexityLevel.HIGH)

        // When
        progressTracker.startTracking(lowComplexityPlan)
        progressTracker.startTracking(highComplexityPlan)

        // Then
        val lowProgress = progressTracker.getProgress(lowComplexityPlan.id)
        val highProgress = progressTracker.getProgress(highComplexityPlan.id)

        assertTrue(highProgress?.estimatedDurationMs ?: 0 > lowProgress?.estimatedDurationMs ?: 0)
    }

    @Test
    fun `should handle listener exceptions gracefully`() = runTest {
        // Given
        val failingListener = object : ProgressListener {
            override fun onStepProgressUpdated(
                planId: String,
                stepId: String,
                stepProgress: Double,
                overallProgress: Double,
                eta: Long,
                message: String?
            ) {
                throw RuntimeException("Listener error")
            }
        }

        progressTracker.addListener(failingListener)
        val plan = createTestPlan()
        progressTracker.startTracking(plan)

        // When & Then - should not throw exception
        progressTracker.updateStepProgress(plan.id, plan.steps.first().id, 50.0)
    }

    @Test
    fun `should remove listeners correctly`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)

        // When
        progressTracker.removeListener(listener)
        progressTracker.updateStepProgress(plan.id, plan.steps.first().id, 50.0)

        // Then
        verify(exactly = 0) { listener.onStepProgressUpdated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should handle progress with failed steps`() = runTest {
        // Given
        val plan = createTestPlan()
        progressTracker.startTracking(plan)

        // When
        progressTracker.failStep(plan.id, plan.steps[0].id, "Step failed")
        progressTracker.completeStep(plan.id, plan.steps[1].id)

        // Then
        val progress = progressTracker.getProgress(plan.id)
        assertEquals(1, progress?.completedSteps)
        assertEquals(1, progress?.failedSteps)

        // Overall progress should account for failed steps
        val overallProgress = progressTracker.getProgressPercentage(plan.id)
        assertEquals(66.67, overallProgress, 0.1) // (1 completed + 1 failed) / 3 = 66.67%
    }

    private fun createTestPlan(
        planId: String = "test-plan-${Clock.System.now().epochSeconds}",
        complexity: ComplexityLevel = ComplexityLevel.MEDIUM
    ): ExecutionPlan {
        val steps = listOf(
            PlanStep(
                id = "step1",
                title = "Step 1",
                description = "First step",
                agentType = AgentType.PROJECT_SCANNER,
                estimatedDurationMs = 30_000L
            ),
            PlanStep(
                id = "step2",
                title = "Step 2",
                description = "Second step",
                agentType = AgentType.BUG_DETECTION,
                estimatedDurationMs = 60_000L
            ),
            PlanStep(
                id = "step3",
                title = "Step 3",
                description = "Third step",
                agentType = AgentType.CODE_FIXER,
                estimatedDurationMs = 45_000L
            )
        )

        return ExecutionPlan(
            id = planId,
            userRequestId = "test-request",
            originalRequest = "Test request",
            analysis = RequestAnalysis(
                taskType = TaskType.CODE_ANALYSIS,
                requiredTools = setOf(AgentType.PROJECT_SCANNER),
                context = ExecutionContext(),
                parameters = emptyMap(),
                requiresUserInput = false,
                estimatedComplexity = complexity,
                estimatedSteps = 3
            ),
            steps = steps,
            currentState = PlanState.CREATED
        )
    }
}