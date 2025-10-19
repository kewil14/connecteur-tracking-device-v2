package com.example.demo.service

import com.example.demo.entity.DeviceData
import com.example.demo.model.GpsMessage
import com.example.demo.repository.DeviceDataRepository
import org.slf4j.LoggerFactory
import org.springframework.integration.annotation.MessageEndpoint
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.ip.tcp.TcpSendingMessageHandler
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * Service principal pour gérer les messages GPS.
 *
 * Ce service est annoté comme un endpoint Spring Integration pour traiter les messages entrants du canal TCP.
 * Il parse le message, stocke les données, et génère des réponses conformes au protocole Beesure GPS SeTracker.
 * Il gère également l'envoi de commandes sortantes.
 *
 * Gestion des commandes :
 * - LK : Réponse immédiate pour maintenir la connexion.
 * - AL : Confirmation d'alarme avec analyse des bits (ex. chute, SOS).
 * - UD/UD2/PP : Stockage des positions avec extraction de latitude, longitude, batterie.
 * - CONFIG : Réponse OK.
 * - Commandes serveur (APN, UPLOAD, etc.) : Réponses appropriées.
 * - img : Gestion des snapshots (stockage des données brutes).
 *
 * Injection :
 * - deviceDataRepository : Pour persister les données dans la base de données.
 * - tcpSender : Handler injecté pour envoyer des réponses et commandes via TCP.
 */
@Service
@MessageEndpoint
class GpsService(
    val deviceDataRepository: DeviceDataRepository,
    @Autowired private val tcpSender: TcpSendingMessageHandler
) {
    private val logger = LoggerFactory.getLogger(GpsService::class.java)

    /**
     * Méthode activée pour traiter les messages entrants du canal TCP.
     *
     * Parse le payload, stocke les données dans la base de données, et retourne une réponse si nécessaire.
     * La réponse est envoyée via le canal tcpOutChannel.
     *
     * @param message Message entrant avec payload String.
     * @return Message de réponse (ou null si aucune réponse requise).
     */
    @ServiceActivator(inputChannel = "tcpInChannel", outputChannel = "tcpOutChannel")
    fun handleMessage(message: Message<String>): Message<String>? {
        val payload = message.payload
        logger.info("Received message: $payload")

        val gpsMessage = GpsMessage.parse(payload) ?: run {
            logger.warn("Failed to parse message: $payload")
            return null // Pas de réponse pour message invalide
        }

        logger.info("Parsed message: manufacture=${gpsMessage.manufacture}, deviceId=${gpsMessage.deviceId}, length=${gpsMessage.length}, content=${gpsMessage.content}")

        val deviceData = DeviceData(
            deviceId = gpsMessage.deviceId,
            type = gpsMessage.content.split(",")[0],
            content = gpsMessage.content,
            receivedAt = LocalDateTime.now()
        )
        deviceDataRepository.save(deviceData)
        logger.info("Saved DeviceData with id=${deviceData.id}")

        return when (val command = gpsMessage.content.split(",")[0]) {
            "LK" -> handleLinkKeep(gpsMessage)
            "UD", "UD2", "PP" -> {
                handlePositionData(gpsMessage)
                null
            }
            "AL" -> handleAlarm(gpsMessage)
            "CONFIG" -> handleConfig(gpsMessage)
            "img" -> handleImage(gpsMessage)
            "APN", "UPLOAD", "PW", "CALL", "CENTER", "MONITOR", "SOS1", "SOS2", "SOS3", "SOS",
            "IP", "FACTORY", "LZ", "SOSSMS", "LOWBAT", "VERNO", "TS", "RESET", "CR", "POWEROFF",
            "REMOVE", "REMOVESMS", "WALKTIME", "SLEEPTIME", "SILENCETIME", "SILENCETIME2", "FIND",
            "FLOWER", "REMIND", "TK", "TKQ", "TKQ2", "MESSAGE", "PHB", "PHB2", "PHBX", "PHBX2",
            "DPHBX", "PPR", "profile", "WHITELIST1", "WHITELIST2", "hrtstart", "HEALTHAUTOSET",
            "bphrt", "oxygen", "TAKEPILLS", "rcapture", "FALLDOWN", "LSSET", "bodytemp", "bodytemp2",
            "btemp2", "WIFISEARCH", "WIFISET", "WIFIDEL", "WIFICUR", "WIFIINFOUP", "APPLOCK",
            "DEVREFUSEPHONESWITCH", "ACALL" -> handleServerCommand(gpsMessage, command)
            else -> {
                logger.warn("Unknown command: $command")
                null
            }
        }
    }

    /**
     * Gère les messages LK (heartbeat).
     *
     * Répond toujours avec [manufacture*deviceId*0002*LK], indépendamment des données supplémentaires.
     *
     * @param gpsMessage Message parsé contenant les détails du dispositif.
     * @return Message de réponse formaté.
     */
    private fun handleLinkKeep(gpsMessage: GpsMessage): Message<String> {
        val response = "[${gpsMessage.manufacture}*${gpsMessage.deviceId}*0002*LK]"
        logger.info("Generated response for LK: $response")
        return MessageBuilder.withPayload(response).build()
    }

    /**
     * Gère les messages de position (UD, UD2, PP).
     *
     * Parse les parties du message pour extraire latitude, longitude, batterie, et autres métadonnées,
     * puis stocke les données. Aucune réponse n'est requise par le protocole.
     *
     * @param gpsMessage Message parsé contenant les données de position.
     * @return Null (pas de réponse).
     */
    private fun handlePositionData(gpsMessage: GpsMessage): Message<String>? {
        val parts = gpsMessage.content.split(",")
        if (parts.size >= 5) {
            val deviceData = DeviceData(
                deviceId = gpsMessage.deviceId,
                type = parts[0],
                content = gpsMessage.content,
                latitude = parts[3].toDoubleOrNull(),
                longitude = parts[5].toDoubleOrNull(),
                batteryStatus = parts[12].toIntOrNull(),
                signalStrength = parts[11].toIntOrNull(),
                receivedAt = LocalDateTime.now()
            )
            deviceDataRepository.save(deviceData)
            logger.info("Saved position data for deviceId=${gpsMessage.deviceId}, lat=${parts[3]}, lon=${parts[5]}, battery=${parts[12]}")
        }
        return null
    }

    /**
     * Gère les messages AL (alarme).
     *
     * Analyse les bits d'alarme (ex. chute bit 21, SOS bit 16) et répond avec [manufacture*deviceId*0002*AL].
     *
     * @param gpsMessage Message parsé contenant les détails de l'alarme.
     * @return Message de réponse formaté.
     */
    private fun handleAlarm(gpsMessage: GpsMessage): Message<String> {
        val parts = gpsMessage.content.split(",")
        val status = parts.getOrNull(15)?.toIntOrNull(16)?.toString(2)?.padStart(32, '0') ?: "00000000"
        val fallDown = status[11] == '1' // Bit 21 (de droite à gauche, 0-based)
        val sos = status[16] == '1' // Bit 16
        if (fallDown) logger.info("Fall down alarm detected for deviceId=${gpsMessage.deviceId}")
        if (sos) logger.info("SOS alarm detected for deviceId=${gpsMessage.deviceId}")
        val response = "[${gpsMessage.manufacture}*${gpsMessage.deviceId}*0002*AL]"
        logger.info("Generated response for AL: $response")
        return MessageBuilder.withPayload(response).build()
    }

    /**
     * Gère les messages CONFIG.
     *
     * Répond avec [manufacture*deviceId*0006*CONFIG,1] pour confirmer que la configuration a été acceptée.
     *
     * @param gpsMessage Message parsé contenant la demande de configuration.
     * @return Message de réponse formaté.
     */
    private fun handleConfig(gpsMessage: GpsMessage): Message<String> {
        val response = "[${gpsMessage.manufacture}*${gpsMessage.deviceId}*0006*CONFIG,1]"
        logger.info("Generated response for CONFIG: $response")
        return MessageBuilder.withPayload(response).build()
    }

    /**
     * Gère les messages d'image (img) provenant d'un snapshot distant.
     *
     * Stocke les données brutes de l'image et le timestamp. La conversion AMR/JPEG n'est pas implémentée ici.
     *
     * @param gpsMessage Message parsé contenant les données d'image.
     * @return Null (pas de réponse requise).
     */
    private fun handleImage(gpsMessage: GpsMessage): Message<String>? {
        val parts = gpsMessage.content.split(",")
        if (parts.size >= 3 && parts[1] == "5") { // 5 indique un snapshot distant
            val timestamp = parts[2]
            val imageData = parts.drop(3).joinToString(",")
            val deviceData = DeviceData(
                deviceId = gpsMessage.deviceId,
                type = "img",
                content = gpsMessage.content,
                imageData = imageData,
                receivedAt = LocalDateTime.now()
            )
            deviceDataRepository.save(deviceData)
            logger.info("Received image for deviceId=${gpsMessage.deviceId}, timestamp=$timestamp, data length=${imageData.length}")
        }
        return null
    }

    /**
     * Gère les réponses aux commandes serveur initiées par le dispositif.
     *
     * Génère une réponse avec la longueur appropriée selon la commande.
     *
     * @param gpsMessage Message parsé.
     * @param command Commande à traiter.
     * @return Message de réponse formaté.
     */
    private fun handleServerCommand(gpsMessage: GpsMessage, command: String): Message<String>? {
        val length = when (command) {
            "APN", "CALL", "SOS1", "SOS2", "SOS3", "CENTER", "MONITOR" -> "0004"
            "UPLOAD", "PW", "FACTORY", "RESET", "CR", "POWEROFF", "REMOVE", "REMOVESMS", "FIND", "FLOWER",
            "profile", "SOSSMS", "LOWBAT", "VERNO", "TS", "LZ", "WALKTIME", "SLEEPTIME", "SILENCETIME",
            "SILENCETIME2", "REMIND", "TK", "TKQ", "TKQ2", "MESSAGE", "PHB", "PHB2", "PHBX", "PHBX2",
            "DPHBX", "PPR", "WHITELIST1", "WHITELIST2", "hrtstart", "HEALTHAUTOSET", "bphrt", "oxygen",
            "TAKEPILLS", "rcapture", "FALLDOWN", "LSSET", "bodytemp", "bodytemp2", "btemp2", "WIFISEARCH",
            "WIFISET", "WIFIDEL", "WIFICUR", "WIFIINFOUP", "APPLOCK", "DEVREFUSEPHONESWITCH", "ACALL" -> "0006"
            "IP" -> "0008"
            else -> "0002"
        }
        val response = "[${gpsMessage.manufacture}*${gpsMessage.deviceId}*$length*$command]"
        logger.info("Generated response for server command $command: $response")
        return MessageBuilder.withPayload(response).build()
    }

    /**
     * Envoie une commande sortante au dispositif.
     *
     * Formatte la commande selon le protocole : [3G*deviceId*LEN*command + content]
     * et utilise tcpSender pour l'envoi.
     *
     * @param deviceId ID du dispositif cible.
     * @param command Commande à envoyer (ex. APN, UPLOAD).
     * @param content Contenu supplémentaire (ex. ,cmnet,,,20634).
     * @return La commande complète envoyée sous forme de chaîne.
     */
    fun sendCommand(deviceId: String, command: String, content: String): String {
        val fullContent = "$command$content"
        val length = fullContent.length.toString().padStart(4, '0')
        val fullCommand = "[3G*$deviceId*$length*$fullContent]"
        logger.info("Sending command to deviceId=$deviceId: $fullCommand")
        tcpSender.handleMessage(MessageBuilder.withPayload(fullCommand).build())
        return fullCommand
    }
}