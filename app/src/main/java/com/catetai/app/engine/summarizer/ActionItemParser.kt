package com.catetai.app.engine.summarizer

/**
 * Utilitas untuk mengonversi string JSON daftar tugas (action items)
 * menjadi daftar objek domain [ActionItem].
 *
 * Menggunakan pendekatan ganda:
 * 1. Menggunakan [org.json.JSONArray] bawaan Android runtime saat berjalan di perangkat.
 * 2. Fallback regex parser saat dijalankan di JVM unit test tanpa dependensi Android SDK.
 */
object ActionItemParser {

    /**
     * Mem-parsing string JSON array menjadi [List] dari [ActionItem].
     *
     * Contoh format masukan:
     * `[{"task":"Kirim laporan","pic":"Budi","deadline":"Jumat"}]`
     */
    fun parse(jsonStr: String): List<ActionItem> {
        if (jsonStr.isBlank() || jsonStr.trim() == "[]") return emptyList()

        // 1. Coba menggunakan org.json bawaan Android
        try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<ActionItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val task = obj.optString("task")
                val pic = obj.optString("pic", ActionItem.DEFAULT_PIC)
                val deadline = if (obj.isNull("deadline")) null else obj.optString("deadline")
                if (task.isNotBlank()) {
                    list.add(ActionItem(task = task, pic = pic, deadline = deadline))
                }
            }
            if (list.isNotEmpty()) return list
        } catch (_: Throwable) {
            // Abaikan dan lanjut ke fallback parser
        }

        // 2. Fallback parser berbasis pencocokan pola regex (aman untuk JVM test)
        val list = mutableListOf<ActionItem>()
        val itemPattern = """\{"task":"(.*?)","pic":"(.*?)","deadline":(.*?)\}""".toRegex()
        itemPattern.findAll(jsonStr).forEach { match ->
            val task = unescapeJson(match.groupValues[1])
            val pic = unescapeJson(match.groupValues[2])
            val rawDeadline = match.groupValues[3].trim()
            val deadline = if (rawDeadline == "null") null else unescapeJson(rawDeadline.trim('"'))
            if (task.isNotBlank()) {
                list.add(ActionItem(task = task, pic = pic, deadline = deadline))
            }
        }

        return list
    }

    private fun unescapeJson(input: String): String {
        return input.replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
