package ru.marslab.ide.ride.service.mcp

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.model.mcp.MCPMethod
import ru.marslab.ide.ride.model.mcp.MCPServerStatus

/**
 * Состояние для хранения статусов MCP серверов
 */
data class MCPStatusState(
    var servers: MutableMap<String, ServerStatusData> = mutableMapOf()
)

/**
 * Данные статуса сервера для сериализации
 */
data class ServerStatusData(
    var name: String = "",
    var connected: Boolean = false,
    var error: String? = null,
    var methods: List<MethodData> = emptyList(),
    var lastConnected: Long? = null,
    var lastError: Long? = null
) {
    fun toMCPServerStatus(): MCPServerStatus {
        return MCPServerStatus(
            name = name,
            connected = connected,
            error = error,
            methods = methods.map { it.toMCPMethod() },
            lastConnected = lastConnected,
            lastError = lastError
        )
    }

    companion object {
        fun fromMCPServerStatus(status: MCPServerStatus): ServerStatusData {
            return ServerStatusData(
                name = status.name,
                connected = status.connected,
                error = status.error,
                methods = status.methods.map { MethodData.fromMCPMethod(it) },
                lastConnected = status.lastConnected,
                lastError = status.lastError
            )
        }
    }
}

/**
 * Данные метода для сериализации
 */
data class MethodData(
    var name: String = "",
    var description: String? = null
) {
    fun toMCPMethod(): MCPMethod {
        return MCPMethod(
            name = name,
            description = description
        )
    }

    companion object {
        fun fromMCPMethod(method: MCPMethod): MethodData {
            return MethodData(
                name = method.name,
                description = method.description
            )
        }
    }
}

/**
 * Сервис для хранения статусов MCP серверов в базе данных
 */
@Service(Service.Level.PROJECT)
@State(
    name = "MCPServerStatuses",
    storages = [Storage("mcp-server-statuses.xml")]
)
class MCPStatusPersistenceService : PersistentStateComponent<MCPStatusState> {

    private var state = MCPStatusState()

    override fun getState(): MCPStatusState {
        return state
    }

    override fun loadState(state: MCPStatusState) {
        this.state = state
    }

    /**
     * Сохраняет статус сервера
     */
    fun saveStatus(status: MCPServerStatus) {
        state.servers[status.name] = ServerStatusData.fromMCPServerStatus(status)
    }

    /**
     * Получает статус сервера
     */
    fun getStatus(serverName: String): MCPServerStatus? {
        return state.servers[serverName]?.toMCPServerStatus()
    }

    /**
     * Получает все статусы серверов
     */
    fun getAllStatuses(): List<MCPServerStatus> {
        return state.servers.values.map { it.toMCPServerStatus() }
    }

    /**
     * Удаляет статус сервера
     */
    fun removeStatus(serverName: String) {
        state.servers.remove(serverName)
    }

    /**
     * Очищает все статусы
     */
    fun clearAll() {
        state.servers.clear()
    }

    companion object {
        fun getInstance(project: Project): MCPStatusPersistenceService {
            return project.getService(MCPStatusPersistenceService::class.java)
        }
    }
}
