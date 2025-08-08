/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.heyman.android.ai.kikko

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import be.heyman.android.ai.kikko.clash.helpers.ClashLlmHelper
import be.heyman.android.ai.kikko.clash.services.ClashArenaService
import be.heyman.android.ai.kikko.common.writeLaunchInfo
import be.heyman.android.ai.kikko.forge.ForgeLlmHelper
import be.heyman.android.ai.kikko.forge.ForgeRepository
import be.heyman.android.ai.kikko.persistence.AnalysisResultDao
import be.heyman.android.ai.kikko.persistence.CardDao
import be.heyman.android.ai.kikko.persistence.PollenGrainDao
import be.heyman.android.ai.kikko.prompt.PromptManager

class KikkoApplication : Application(), Configuration.Provider {

  val cardDao: CardDao by lazy { CardDao(this) }
  val pollenGrainDao: PollenGrainDao by lazy { PollenGrainDao(this) }
  val analysisResultDao: AnalysisResultDao by lazy { AnalysisResultDao(this) }
  val forgeRepository: ForgeRepository by lazy { ForgeRepository(pollenGrainDao, cardDao, analysisResultDao) }
  val clashLlmHelper: ClashLlmHelper by lazy { ClashLlmHelper(this) }
  val forgeLlmHelper: ForgeLlmHelper by lazy { ForgeLlmHelper(this) }
  val clashArenaService: ClashArenaService by lazy { ClashArenaService(this) }


  override fun onCreate() {
    super.onCreate()

    writeLaunchInfo(context = this)

    // BOURDON'S CRITICAL FIX: This line is mandatory and was missing.
    // It loads all prompts into memory when the app starts.
    PromptManager.initialize(this)

    cardDao.hashCode()
    pollenGrainDao.hashCode()
    analysisResultDao.hashCode()
    forgeRepository.hashCode()
    clashLlmHelper.hashCode()
    forgeLlmHelper.hashCode()
    clashArenaService.hashCode()
  }

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setMinimumLoggingLevel(Log.DEBUG)
        .build()
}