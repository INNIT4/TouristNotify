package com.joseibarra.trazago

data class Service(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val description: String = "",
    val category: ServiceCat = ServiceCat.OTHER,
    val iconEmoji: String = "📞",
    val priority: Int = 100
)

enum class ServiceCat(val labelRes: Int, val order: Int) {
    TOURISM(R.string.service_cat_tourism, 0),
    LODGING(R.string.service_cat_lodging, 1),
    FOOD(R.string.service_cat_food, 2),
    TRANSPORT(R.string.service_cat_transport, 3),
    HEALTH(R.string.service_cat_health, 4),
    UTILITIES(R.string.service_cat_utilities, 5),
    OTHER(R.string.service_cat_other, 99)
}
