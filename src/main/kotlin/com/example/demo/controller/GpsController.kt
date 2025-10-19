package com.example.demo.controller

import com.example.demo.service.GpsService
import org.springframework.web.bind.annotation.*

/**
 * Contrôleur REST pour interagir avec le service GPS.
 *
 * Expose des endpoints pour récupérer les données des dispositifs et envoyer des commandes.
 * Utilise le GpsService pour la logique métier.
 *
 * Endpoints :
 * - GET /gps/data/{deviceId} : Récupère les données par type.
 * - POST /gps/command/{deviceId}/{command} : Envoie une commande au dispositif.
 */
@RestController
@RequestMapping("/gps")
class GpsController(private val gpsService: GpsService) {

    /**
     * Récupère les données d'un dispositif par ID.
     *
     * Filtre par types (UD, UD2, AL, PP) et retourne une liste de maps pour une lecture facile.
     *
     * @param deviceId ID du dispositif.
     * @return Liste de maps avec les détails des données.
     */
//    @GetMapping("/data/{deviceId}")
//    fun getDeviceData(@PathVariable deviceId: String): List<Map<String, String>> {
//        return listOf("UD", "UD2", "AL", "PP").flatMap { type ->
//            gpsService.deviceDataRepository.findByDeviceIdAndType(deviceId, type).map {
//                mapOf(
//                    "type" to it.type,
//                    "content" to it.content,
//                    "latitude" to (it.latitude ?: "N/A"),
//                    "longitude" to (it.longitude ?: "N/A"),
//                    "batteryStatus" to (it.batteryStatus?.toString() ?: "N/A"),
//                    "timestamp" to it.timestamp.toString()
//                )
//            }
//        }
//    }

    /**
     * Envoie une commande au dispositif.
     *
     * Supporte des commandes prédéfinies avec leur contenu par défaut.
     * Utilise le GpsService pour l'envoi.
     *
     * @param deviceId ID du dispositif.
     * @param command Commande (ex. APN, CR).
     * @return La commande envoyée ou un message d'erreur.
     */
//    @PostMapping("/command/{deviceId}/{command}")
//    fun sendCommand(@PathVariable deviceId: String, @PathVariable command: String): String {
//        return when (command) {
//            "APN" -> gpsService.sendCommand(deviceId, "APN", ",cmnet,,,20634")
//            "UPLOAD" -> gpsService.sendCommand(deviceId, "UPLOAD", ",600")
//            "CR" -> gpsService.sendCommand(deviceId, "CR", "")
//            "SOS1" -> gpsService.sendCommand(deviceId, "SOS1", ",00000000000")
//            "IP" -> gpsService.sendCommand(deviceId, "IP", ",113.81.229.9,5900")
//            else -> "Commande non supportée"
//        }
//    }

    @GetMapping("/data/{deviceId}")
    fun getDeviceData(@PathVariable deviceId: String): String {
        // Logique à implémenter pour récupérer les données (ex. depuis la base)
        return "Data for device $deviceId"
    }

    @PostMapping("/command/{deviceId}/{command}/{content}")
    fun sendCommand(@PathVariable deviceId: String, @PathVariable command: String, @PathVariable content: String): String {
        return gpsService.sendCommand(deviceId, command, content)
    }
}