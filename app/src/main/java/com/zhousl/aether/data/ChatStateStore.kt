package com.zhousl.aether.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ChatStateStore(
    private val scope: CoroutineScope,
    private val chatRepository: ChatRepository,
) {
    private val updateLock = Any()
    private val persistMutex = Mutex()
    private val persistenceQueue = Channel<PendingPersistedChatState>(capacity = Channel.CONFLATED)
    private val _state = MutableStateFlow(PersistedChatState())
    private var localGeneration = 0L
    private var persistedGeneration = 0L
    private var latestPending: PendingPersistedChatState? = null

    val state: StateFlow<PersistedChatState> = _state.asStateFlow()

    init {
        scope.launch {
            chatRepository.chatState.collect { persisted ->
                synchronized(updateLock) {
                    if (localGeneration == persistedGeneration) {
                        _state.value = persisted
                    }
                }
            }
        }
        scope.launch {
            try {
                for (pending in persistenceQueue) {
                    persistPending(pending)
                }
            } finally {
                withContext(NonCancellable) {
                    flushLatestPending()
                }
            }
        }
    }

    suspend fun flush() {
        flushLatestPending()
    }

    suspend fun updateAndFlush(
        transform: (PersistedChatState) -> PersistedChatState,
    ): PersistedChatState {
        val updated = update(transform)
        flushLatestPending()
        return updated
    }

    private suspend fun persistPending(pending: PendingPersistedChatState) {
        persistMutex.withLock {
            if (synchronized(updateLock) { pending.generation <= persistedGeneration }) {
                return@withLock
            }

            chatRepository.updateChatState(
                sessions = pending.state.sessions,
                currentSessionId = pending.state.currentSessionId,
            )
            synchronized(updateLock) {
                if (pending.generation > persistedGeneration) {
                    persistedGeneration = pending.generation
                }
                val latest = latestPending
                if (latest != null && latest.generation <= pending.generation) {
                    latestPending = null
                }
            }
        }
    }

    private suspend fun flushLatestPending() {
        val pending = synchronized(updateLock) {
            latestPending?.takeIf { it.generation > persistedGeneration }
        } ?: return
        persistPending(pending)
    }

    private fun persistWithoutQueue(pending: PendingPersistedChatState) {
        scope.launch(Dispatchers.IO + NonCancellable) {
            runCatching {
                persistPending(pending)
            }
        }
    }

    fun update(
        transform: (PersistedChatState) -> PersistedChatState,
    ): PersistedChatState {
        val pending = synchronized(updateLock) {
            val updated = transform(_state.value)
            localGeneration += 1
            _state.value = updated
            PendingPersistedChatState(
                generation = localGeneration,
                state = updated,
            ).also {
                latestPending = it
            }
        }
        val sendResult = persistenceQueue.trySend(pending)
        if (sendResult.isFailure) {
            persistWithoutQueue(pending)
        }
        return pending.state
    }

    private data class PendingPersistedChatState(
        val generation: Long,
        val state: PersistedChatState,
    )
}
