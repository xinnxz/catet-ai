package com.catetai.app.engine.summarizer

/**
 * Model data untuk satu butir tindakan atau tugas tindak lanjut rapat (Action Item).
 *
 * @param task Deskripsi tugas yang harus dikerjakan.
 * @param pic Person in Charge / Penanggung jawab tugas. Default: "Belum teridentifikasi"
 *            jika nama tidak disebutkan secara eksplisit dalam transkrip.
 * @param deadline Batas waktu / tenggat penyelesaian tugas (misal: "Jumat", "Besok sore", "30 November"),
 *                 atau null jika tidak ada tenggat yang disebutkan.
 */
data class ActionItem(
    val task: String,
    val pic: String = DEFAULT_PIC,
    val deadline: String? = null
) {
    companion object {
        const val DEFAULT_PIC = "Belum teridentifikasi"
    }
}
