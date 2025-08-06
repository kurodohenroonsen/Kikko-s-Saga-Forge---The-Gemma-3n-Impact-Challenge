package be.heyman.android.ai.kikko.pollen.vision

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Un exécuteur qui peut être fermé.
 * Lorsque l'exécuteur est fermé, les tâches en attente ne seront pas exécutées.
 */
class ScopedExecutor(private val executor: Executor) : Executor {
    private val shutdown = AtomicBoolean()

    override fun execute(command: Runnable) {
        // N'exécute la commande que si le garde du corps n'a pas signalé l'arrêt.
        if (!shutdown.get()) {
            executor.execute(command)
        }
    }

    /**
     * Signale à l'exécuteur d'arrêter d'accepter de nouvelles tâches.
     */
    fun shutdown() {
        shutdown.set(true)
    }
}