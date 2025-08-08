package be.heyman.android.ai.kikko.util

import java.util.concurrent.atomic.AtomicBoolean

/**
 * BOURDON'S STRATEGIC ADDITION:
 * Un singleton qui agit comme un verrou pour la ressource partagée de l'IA (la "Reine" / ForgeLlmHelper).
 * Cela empêche le ForgeWorker en arrière-plan de s'exécuter et de potentiellement causer un crash
 * pendant qu'une activité au premier plan (comme RoyalAudienceActivity) utilise activement le modèle LLM.
 */
object QueenResourceLock {

    private val isLocked = AtomicBoolean(false)

    /**
     * Tente d'acquérir le verrou. Appelé par les activités dans `onResume`.
     * @return `true` si le verrou a été acquis avec succès, `false` sinon.
     */
    fun acquire(): Boolean {
        return isLocked.compareAndSet(false, true)
    }

    /**
     * Libère le verrou. Appelé par les activités dans `onPause`.
     */
    fun release() {
        isLocked.set(false)
    }

    /**
     * Vérifie si le verrou est actuellement détenu.
     * Le Worker vérifiera cela avant de commencer son travail.
     */
    fun isLocked(): Boolean {
        return isLocked.get()
    }
}