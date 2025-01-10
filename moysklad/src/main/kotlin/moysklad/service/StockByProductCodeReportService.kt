package moysklad.service

import moysklad.client.feign.ProductApiClient
import moysklad.model.StockByProductCodeReport
import moysklad.model.StockByProductCodeReport.Companion.HEADER
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockByProductCodeReportService(
    private val productApiClient: ProductApiClient,
) {
    private fun tryGenerateReport(): ByteArray {
        try {
            ByteArrayOutputStream().use { outputStream ->
                BufferedWriter(
                    OutputStreamWriter(
                        outputStream,
                        StandardCharsets.UTF_8,
                    ),
                ).use { bufferedWriter ->
//                    bufferedWriter.write(SPECIAL_FIRST_SYMBOL_FOR_CORRECT_OPENING_REPORT_IN_EXCEL)
//                    bufferedWriter.write(SPECIAL_METADATA_FOR_SEPARATOR)
//                    bufferedWriter.newLine()

                    writeReportLine(
                        bufferedWriter,
                        HEADER,
                    )

                    val limit = 50 // Adjust this limit as needed
                    var offset = 0
                    var totalSize: Int

                    do {
                        val response = productApiClient.getProducts(limit, offset)
                        response.products.forEach {
                            writeReportLine(
                                bufferedWriter,
                                StockByProductCodeReport(
                                    code = it.code,
                                    stock = it.stock.toString(),
                                ).toCsvString(),
                            )
                        }

                        totalSize = response.size
                        offset += limit
                    } while (offset < totalSize)

                    bufferedWriter.flush()
                    return outputStream.toByteArray()
                }
            }
        } catch (e: IOException) {
            throw IllegalArgumentException("Exception during creation report", e)
        }
    }

    private fun writeReportLine(
        bufferedWriter: BufferedWriter,
        line: String,
    ) {
        try {
            bufferedWriter.write(line)
            bufferedWriter.newLine()
        } catch (e: IOException) {
            throw IllegalArgumentException("Exception during write line in report", e)
        }
    }

    open fun generateReportToFile() {
        val fileName = format(Date(), "yyyy-MM-dd'T'HH'h'mm'm'ss's'") + ".csv"
        val path = Paths.get("./$fileName")
        try {
            Files.write(path, tryGenerateReport())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun format(
        date: Date,
        pattern: String,
    ): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return dateFormat.format(date)
    }
}
