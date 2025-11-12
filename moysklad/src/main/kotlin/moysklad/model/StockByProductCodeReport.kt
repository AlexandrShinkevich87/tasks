package moysklad.model

import kotlin.plus

data class StockByProductCodeReport(
    private val code: String,
    private val stock: String,
    private val price: String,
) {
    fun toCsvString(): String =
        firstNonNull(this.code, BLANK) + DELIMITER +
            firstNonNull(this.stock, BLANK) + DELIMITER +
            firstNonNull(this.price, BLANK)

    companion object {
        private const val BLANK = ""
        private const val DELIMITER = ","
        const val HEADER: String = "Код,Остаток,Цена"
    }

    private fun <T> firstNonNull(vararg values: T?): T? = values.firstOrNull { it != null }
}
