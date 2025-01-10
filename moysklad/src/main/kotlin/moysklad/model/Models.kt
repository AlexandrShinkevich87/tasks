package moysklad.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProductResponse(
    val products: List<Product>,
    val size: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Product(
    val id: String,
    val code: String,
    val name: String,
    val price: Int,
    val currency: String,
    val stock: Int,
)
