package com.joseibarra.trazago

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object ServicesRepository {

    val DEFAULT_SERVICES: List<Service> = listOf(
        Service(id = "svc_tourism_office", name = "Oficina de Turismo Álamos", phoneNumber = "647-428-0450",
            description = "Información turística, mapas y guías de la ciudad", category = ServiceCat.TOURISM,
            iconEmoji = "🏛️", priority = 1),
        Service(id = "svc_museo_costumbrista", name = "Museo Costumbrista", phoneNumber = "647-428-0053",
            description = "Historia y tradiciones de Álamos en un edificio del siglo XIX", category = ServiceCat.TOURISM,
            iconEmoji = "🏛️", priority = 2),
        Service(id = "svc_casa_cultura", name = "Casa de la Cultura", phoneNumber = "647-428-0780",
            description = "Exposiciones, talleres y eventos culturales durante todo el año", category = ServiceCat.TOURISM,
            iconEmoji = "🎭", priority = 3),
        Service(id = "svc_hotel_hacienda", name = "Hotel Hacienda de los Santos", phoneNumber = "647-428-0222",
            description = "Hotel boutique de lujo en el centro histórico, spa y restaurante", category = ServiceCat.LODGING,
            iconEmoji = "🏨", priority = 1),
        Service(id = "svc_el_cactus", name = "Restaurante El Cactus", phoneNumber = "647-428-0345",
            description = "Cocina tradicional sonorense, cortes de carne y mariscos frescos", category = ServiceCat.FOOD,
            iconEmoji = "🍽️", priority = 1),
        Service(id = "svc_terminal_bus", name = "Terminal de Autobuses", phoneNumber = "647-428-0145",
            description = "Conexiones diarias a Hermosillo, Navojoa y Ciudad Obregón", category = ServiceCat.TRANSPORT,
            iconEmoji = "🚌", priority = 1),
        Service(id = "svc_taxis", name = "Taxis Álamos", phoneNumber = "647-428-0900",
            description = "Servicio de taxi las 24 horas, traslados dentro y fuera de la ciudad", category = ServiceCat.TRANSPORT,
            iconEmoji = "🚕", priority = 2),
        Service(id = "svc_hospital", name = "Hospital General Álamos", phoneNumber = "647-428-0234",
            description = "Atención de urgencias, consulta externa y hospitalización", category = ServiceCat.HEALTH,
            iconEmoji = "🏥", priority = 1),
        Service(id = "svc_farmacia", name = "Farmacia 24 horas", phoneNumber = "647-428-0567",
            description = "Medicamentos de venta libre y receta, abierto toda la noche", category = ServiceCat.HEALTH,
            iconEmoji = "💊", priority = 2)
    )

    suspend fun load(): List<Service> {
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection(FirestoreCollections.SERVICES)
                .whereEqualTo("active", true)
                .get()
                .await()
            val remote = snap.documents.mapNotNull { doc ->
                try {
                    Service(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        phoneNumber = doc.getString("phoneNumber") ?: "",
                        description = doc.getString("description") ?: "",
                        category = try { ServiceCat.valueOf(doc.getString("category") ?: "") } catch (_: Exception) { ServiceCat.OTHER },
                        iconEmoji = doc.getString("iconEmoji") ?: "📞",
                        priority = doc.getLong("priority")?.toInt() ?: 100
                    )
                } catch (_: Exception) { null }
            }
            if (remote.isEmpty()) DEFAULT_SERVICES else remote
        } catch (_: Exception) {
            DEFAULT_SERVICES
        }
    }
}
