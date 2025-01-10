package moysklad

import com.fasterxml.jackson.databind.ObjectMapper
import moysklad.client.feign.ProductApiClient
import moysklad.configuration.FeignConfiguration
import moysklad.configuration.JsonConfiguration
import moysklad.service.StockByProductCodeReportService

fun main() {
    val objectMapper: ObjectMapper = JsonConfiguration.objectMapper

    val feignConfig = FeignConfiguration(objectMapper)
    // Create Feign client
    val productApiClient =
        feignConfig.createClient(
            ProductApiClient::class.java,
            "https://b2b.moysklad.ru/desktop-api/",
        )

    StockByProductCodeReportService(productApiClient).generateReportToFile()
}
