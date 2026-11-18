package com.catetai.app.domain.usecase

import com.catetai.app.domain.model.ModelDownloadStatus
import com.catetai.app.domain.model.ModelInfo
import com.catetai.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case untuk memeriksa ketersediaan model AI dan mengamati status unduhan.
 */
@Singleton
class GetModelStatusUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {

    /**
     * Mengalirkan status terkini pengunduhan model AI.
     */
    val downloadStatus: StateFlow<ModelDownloadStatus>
        get() = modelRepository.downloadStatus

    /**
     * Memeriksa apakah model AI sudah tersedia di penyimpanan perangkat.
     */
    fun isModelAvailable(modelInfo: ModelInfo = ModelInfo.DEFAULT): Boolean =
        modelRepository.isModelAvailable(modelInfo)

    /**
     * Mengambil berkas model AI jika sudah diunduh.
     */
    fun getModelFile(modelInfo: ModelInfo = ModelInfo.DEFAULT): File? =
        modelRepository.getModelFile(modelInfo)
}
