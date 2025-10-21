package ru.marslab.ide.ride.orchestrator

import org.junit.Assert.*
import org.junit.Test
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.PlanStep
import kotlin.test.fail

class DependencyGraphTest {

    @Test
    fun `test simple linear dependency`() {
        // Given: A -> B -> C
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Step A",
                description = "First step",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = emptySet()
            ),
            PlanStep(
                id = "B",
                title = "Step B",
                description = "Second step",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            ),
            PlanStep(
                id = "C",
                title = "Step C",
                description = "Third step",
                agentType = AgentType.REPORT_GENERATOR,
                dependencies = setOf("B")
            )
        )

        // When
        val graph = DependencyGraph(steps)
        val batches = graph.topologicalSort()

        // Then
        assertEquals(3, batches.size)
        assertEquals(listOf("A"), batches[0])
        assertEquals(listOf("B"), batches[1])
        assertEquals(listOf("C"), batches[2])
    }

    @Test
    fun `test parallel execution - diamond pattern`() {
        // Given: A -> B, C -> D (B and C can run in parallel)
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Scan",
                description = "Scan project",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = emptySet()
            ),
            PlanStep(
                id = "B",
                title = "Bugs",
                description = "Find bugs",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            ),
            PlanStep(
                id = "C",
                title = "Quality",
                description = "Check quality",
                agentType = AgentType.CODE_QUALITY,
                dependencies = setOf("A")
            ),
            PlanStep(
                id = "D",
                title = "Report",
                description = "Generate report",
                agentType = AgentType.REPORT_GENERATOR,
                dependencies = setOf("B", "C")
            )
        )

        // When
        val graph = DependencyGraph(steps)
        val batches = graph.topologicalSort()

        // Then
        assertEquals(3, batches.size)
        assertEquals(listOf("A"), batches[0])
        assertTrue(batches[1].containsAll(listOf("B", "C")))
        assertEquals(2, batches[1].size)
        assertEquals(listOf("D"), batches[2])
    }

    @Test
    fun `test circular dependency detection`() {
        // Given: A -> B -> C -> A (circular)
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Step A",
                description = "First",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = setOf("C") // Creates cycle
            ),
            PlanStep(
                id = "B",
                title = "Step B",
                description = "Second",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            ),
            PlanStep(
                id = "C",
                title = "Step C",
                description = "Third",
                agentType = AgentType.REPORT_GENERATOR,
                dependencies = setOf("B")
            )
        )

        // When/Then: should throw CircularDependencyException
        val graph = DependencyGraph(steps)
        try {
            graph.topologicalSort()
            fail("Expected CircularDependencyException")
        } catch (e: CircularDependencyException) {
            // Expected
            assertTrue(e.message?.contains("Circular dependency") == true)
        }
    }

    @Test
    fun `test has cycles`() {
        // Given: circular dependency
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Step A",
                description = "First",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = setOf("B")
            ),
            PlanStep(
                id = "B",
                title = "Step B",
                description = "Second",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            )
        )

        // When
        val graph = DependencyGraph(steps)

        // Then
        assertTrue(graph.hasCycles())
    }

    @Test
    fun `test get dependencies`() {
        // Given
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Step A",
                description = "First",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = emptySet()
            ),
            PlanStep(
                id = "B",
                title = "Step B",
                description = "Second",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            )
        )

        // When
        val graph = DependencyGraph(steps)

        // Then
        assertTrue(graph.getDependencies("A").isEmpty())
        assertEquals(setOf("A"), graph.getDependencies("B"))
    }

    @Test
    fun `test get dependents`() {
        // Given
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Step A",
                description = "First",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = emptySet()
            ),
            PlanStep(
                id = "B",
                title = "Step B",
                description = "Second",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            ),
            PlanStep(
                id = "C",
                title = "Step C",
                description = "Third",
                agentType = AgentType.CODE_QUALITY,
                dependencies = setOf("A")
            )
        )

        // When
        val graph = DependencyGraph(steps)

        // Then
        assertEquals(setOf("B", "C"), graph.getDependents("A"))
        assertTrue(graph.getDependents("B").isEmpty())
    }

    @Test
    fun `test can execute`() {
        // Given
        val steps = listOf(
            PlanStep(
                id = "A",
                title = "Step A",
                description = "First",
                agentType = AgentType.PROJECT_SCANNER,
                dependencies = emptySet()
            ),
            PlanStep(
                id = "B",
                title = "Step B",
                description = "Second",
                agentType = AgentType.BUG_DETECTION,
                dependencies = setOf("A")
            )
        )

        // When
        val graph = DependencyGraph(steps)

        // Then
        assertTrue(graph.canExecute("A", emptySet()))
        assertFalse(graph.canExecute("B", emptySet()))
        assertTrue(graph.canExecute("B", setOf("A")))
    }
}
