package moysklad.client.feign

import feign.Param
import feign.RequestLine
import moysklad.model.ProductResponse

// Define the API client interface
interface ProductApiClient {
    @RequestLine("GET /products.json?category=&category_id=&limit={limit}&offset={offset}&search=")
    fun getProducts(
        @Param("limit") limit: Int,
        @Param("offset") offset: Int,
    ): ProductResponse
}
