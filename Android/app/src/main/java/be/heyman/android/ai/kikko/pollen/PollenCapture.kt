package be.heyman.android.ai.kikko.pollen

import android.graphics.Bitmap
import java.util.UUID

/**
 * Représente un grain de pollen individuel pendant la phase de capture en direct.
 * C'est un objet de l'interface utilisateur (UI model) qui contient le bitmap pour
 * l'affichage et les résultats d'analyse en temps réel.
 *
 * Cet objet est éphémère et sera transformé en un 'PollenGrain' persistant
 * (du package model) au moment de la sauvegarde.
 *
 * @param id Un identifiant unique pour suivre le grain dans l'UI.
 * @param bitmap L'image capturée, utilisée pour l'affichage et l'analyse.
 * @param status L'état actuel de l'analyse pour ce grain.
 * @param report Le rapport d'analyse structuré une fois l'analyse terminée.
 * @param jsonReport Le rapport d'analyse complet en format JSON.
 */
data class PollenCapture(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    var status: PollenAnalysisStatus = PollenAnalysisStatus.PROCESSING,
    var report: PollenAnalysis? = null,
    var jsonReport: String? = null
)