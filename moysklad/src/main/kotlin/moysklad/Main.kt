package moysklad

import com.fasterxml.jackson.databind.ObjectMapper
import moysklad.client.feign.ProductApiClient
import moysklad.configuration.FeignConfiguration
import moysklad.configuration.JsonConfiguration
import moysklad.model.Product
import java.io.File

fun main() {
    val objectMapper: ObjectMapper = JsonConfiguration.objectMapper

    val feignConfig = FeignConfiguration(objectMapper)
    // Create Feign client
    val productApiClient =
        feignConfig.createClient(
            ProductApiClient::class.java,
            "https://b2b.moysklad.ru/desktop-api/6wgkgT3JeIM4",
        )

    val limit = 1000 // Adjust this limit as needed
    var offset = 0
    var totalSize: Int

    val allProducts = mutableListOf<Product>()

    do {
        val response = productApiClient.getProducts(limit, offset)
        allProducts.addAll(response.products)
        totalSize = response.size
        offset += limit
    } while (offset < totalSize)

    // Save results to a file
    val outputFile = File("products.json")
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile, allProducts)

    println("Fetched ${allProducts.size} products and saved to products.json")
}
