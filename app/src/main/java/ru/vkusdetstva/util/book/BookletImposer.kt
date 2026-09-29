package ru.vkusdetstva.util.book

/**
 * Тетрадная (брошюрная) раскладка для печати «пополам».
 * Все индексы страниц 0-ориентированные.
 */
object BookletImposer {

    data class Sheet(val frontLeft: Int, val frontRight: Int, val backLeft: Int, val backRight: Int)

    /** Добивает общее число страниц до кратного четырём. */
    fun padToQuadruple(totalPages: Int): Int = ((totalPages + 3) / 4) * 4

    /**
     * Порядок страниц по листам: лист k — лицевая [N-4k | 4k+1], оборот [4k+2 | N-4k-1] (1-ориентированно).
     * Для N=8: лист 1 = [8|1]/[2|7], лист 2 = [6|3]/[4|5].
     */
    fun bookletOrder(totalPages: Int): List<Sheet> {
        require(totalPages > 0 && totalPages % 4 == 0) { "Число страниц должно быть кратно 4: $totalPages" }
        val sheets = mutableListOf<Sheet>()
        var front = 0
        var back = totalPages - 1
        while (front < back) {
            sheets += Sheet(back, front, front + 1, back - 1)
            front += 2
            back -= 2
        }
        return sheets
    }
}
