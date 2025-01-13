package moysklad

import com.fasterxml.jackson.databind.ObjectMapper
import moysklad.client.feign.ProductApiClient
import moysklad.configuration.FeignConfiguration
import moysklad.configuration.JsonConfiguration
import moysklad.service.StockByProductCodeReportService
import org.slf4j.LoggerFactory
import java.util.Scanner

@Suppress("ktlint:standard:chain-method-continuation", "ktlint:standard:function-signature")
fun main() {
    val logger = LoggerFactory.getLogger("Main")
    logger.info("Program started")

    runCatching {
        val objectMapper: ObjectMapper = JsonConfiguration.objectMapper

        val feignConfig = FeignConfiguration(objectMapper)
        // Create Feign client
        val productApiClient =
            feignConfig.createClient(
                ProductApiClient::class.java,
                "https://b2b.moysklad.ru/desktop-api/",
            )

//        logger.info("Write sync start")
//        StockByProductCodeReportService(productApiClient).generateReportToFile()
//        logger.info("Write sync finish")

        logger.info("Write async start")
        StockByProductCodeReportService(productApiClient).generateReportToFileAsync()
        logger.info("Write async finish")
    }
        .onFailure { e ->
            logger.error("An error occurred during execution: {}", e.message, e)
        }

    logger.info("Press any key to exit the program...")
    Scanner(System.`in`).nextLine() // Wait for the user to press any key

    logger.info("Program terminated")
}
