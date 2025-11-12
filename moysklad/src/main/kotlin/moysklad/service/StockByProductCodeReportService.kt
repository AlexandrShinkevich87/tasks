package moysklad.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import moysklad.client.feign.ProductApiClient
import moysklad.model.Product
import moysklad.model.StockByProductCodeReport
import moysklad.model.StockByProductCodeReport.Companion.HEADER
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.path.outputStream

@Suppress("ktlint:standard:chain-method-continuation", "ktlint:standard:function-signature")
class StockByProductCodeReportService(
    private val productApiClient: ProductApiClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val logger = LoggerFactory.getLogger("Report")

    private fun tryGenerateReport(): ByteArray {
        try {
            ByteArrayOutputStream().use { outputStream ->
                BufferedWriter(
                    OutputStreamWriter(
                        outputStream,
                        StandardCharsets.UTF_8,
                    ),
                )
                    .use { bufferedWriter ->
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
                                        price = it.price.toString(),
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

    open fun generateReportToFileAsync() {
        val fileName = format(Date(), "yyyy-MM-dd'T'HH'h'mm'm'ss's'") + ".csv"
        val path = Paths.get("./$fileName")

        if (Files.notExists(path)) {
            Files.createFile(path)
        }

        runBlocking {
            tryGenerateReportAsync(path)
        }
    }

    private suspend fun tryGenerateReportAsync(path: Path) =
        coroutineScope {
            val channel = Channel<List<Product>>(capacity = 100)

            val getProductJob =
                launch {
                    val limit = 50
                    var offset = 0
                    var totalSize: Int

                    var processedCount = 0 // Количество обработанных записей
                    do {
                        val response =
                            productApiClient.getProducts(limit, offset)

                        channel.send(response.products)

                        totalSize = response.size
                        offset += limit

                        processedCount += response.products.size
                        logger.info("Fetched ${response.products.size} records. Processed $processedCount of total $totalSize.")
                    } while (offset < totalSize)

                    logger.info("Fetching completed. Total records processed: $totalSize.")
                    channel.close() // Signal that all data has been sent
                }

            val writeProductsToFileJob =
                launch {
                    path.outputStream()
                        .use { outputStream ->
                            BufferedWriter(
                                OutputStreamWriter(
                                    outputStream,
                                    StandardCharsets.UTF_8,
                                ),
                            )
                                .use { bufferedWriter ->
                                    try {
                                        // Write header
                                        writeReportLine(bufferedWriter, HEADER)

                                        for (products in channel) {
                                            products
                                                .forEach {
                                                    writeReportLine(
                                                        bufferedWriter,
                                                        StockByProductCodeReport(
                                                            code = it.code,
                                                            stock = it.stock.toString(),
                                                            price = it.price.toString(),
                                                        )
                                                            .toCsvString(),
                                                    )
                                                }
                                            bufferedWriter.flush()
                                        }
                                    } catch (e: IOException) {
                                        throw IllegalArgumentException(
                                            "Exception during write line in report",
                                            e,
                                        )
                                    }
                                }
                        }
                }

            joinAll(getProductJob, writeProductsToFileJob)
        }
}
