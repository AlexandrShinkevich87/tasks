package moysklad.model

data class StockByProductCodeReport(
    private val code: String,
    private val stock: String,
) {
    fun toCsvString(): String =
        firstNonNull(this.code, BLANK) + DELIMITER +
            firstNonNull(this.stock, BLANK)

    companion object {
        private const val BLANK = ""
        private const val DELIMITER = ","
        const val HEADER: String = "Код,Остаток"
    }

    private fun <T> firstNonNull(vararg values: T?): T? = values.firstOrNull { it != null }
}
