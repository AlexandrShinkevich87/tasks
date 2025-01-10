package moysklad.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import feign.Feign
import feign.jackson.JacksonDecoder
import feign.jackson.JacksonEncoder

class FeignConfiguration(
    private val objectMapper: ObjectMapper,
) {
    fun <T> createClient(
        apiClass: Class<T>,
        baseUrl: String,
    ): T =
        Feign
            .builder()
            .encoder(JacksonEncoder(objectMapper))
            .decoder(JacksonDecoder(objectMapper))
            .target(apiClass, baseUrl)
}
