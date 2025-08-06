package be.heyman.android.ai.kikko.clash.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * L'adaptateur pour le ViewPager2 de l'arène de Clash.
 * Il est responsable de la création d'un ClashDuelFragment pour chaque duel.
 *
 * @param fa L'activité hôte du fragment.
 * @param duelCount Le nombre total de duels à afficher.
 */
class ClashPagerAdapter(
    fa: FragmentActivity,
    private val duelCount: Int
) : FragmentStateAdapter(fa) {

    /**
     * Retourne le nombre total de pages (duels).
     */
    override fun getItemCount(): Int = duelCount

    /**
     * Crée et retourne un nouveau fragment pour la position donnée.
     * Chaque fragment représente un seul duel.
     */
    override fun createFragment(position: Int): Fragment {
        // Passe la position (index du duel) au fragment pour qu'il sache quelles données afficher.
        return ClashDuelFragment.newInstance(position)
    }
}