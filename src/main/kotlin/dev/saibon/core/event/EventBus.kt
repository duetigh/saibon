package dev.saibon.core.event

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

fun interface EventHandler<T> {
    fun handle(event: T)
}

class Subscription internal constructor(private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
}

/**
 * Internal pub/sub bus. Feature modules subscribe to event types here instead
 * of holding direct references to whichever system posts them. Subscribing
 * returns a [Subscription] so a disabled feature can unregister just its own
 * handlers, per the "disabled modules cost zero at runtime" rule.
 */
class EventBus {
    private val subscribers = mutableMapOf<KClass<*>, CopyOnWriteArrayList<EventHandler<Any>>>()

    fun <T : Any> subscribe(type: KClass<T>, handler: EventHandler<T>): Subscription {
        val list = subscribers.getOrPut(type) { CopyOnWriteArrayList() }
        @Suppress("UNCHECKED_CAST")
        val erased = handler as EventHandler<Any>
        list.add(erased)
        return Subscription { list.remove(erased) }
    }

    inline fun <reified T : Any> subscribe(noinline handler: (T) -> Unit): Subscription =
        subscribe(T::class, EventHandler { handler(it) })

    fun post(event: Any) {
        subscribers[event::class]?.forEach { it.handle(event) }
    }
}
