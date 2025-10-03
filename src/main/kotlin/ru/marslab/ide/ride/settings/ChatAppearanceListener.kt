package ru.marslab.ide.ride.settings

import com.intellij.util.messages.Topic

fun interface ChatAppearanceListener {
    fun onChatAppearanceChanged()

    companion object {
        val TOPIC: Topic<ChatAppearanceListener> = Topic.create(
            "RideChatAppearance",
            ChatAppearanceListener::class.java
        )
    }
}
