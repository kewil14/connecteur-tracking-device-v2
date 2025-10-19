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

/**
 * Service principal pour gérer les messages GPS.
 *
 * Ce service est annoté comme un endpoint Spring Integration pour traiter les messages entrants du canal TCP.
 * Il parse le message, stocke les données, et génère des réponses conformes au protocole.
 * Il gère également l'envoi de commandes sortantes.
 *
 * Gestion des commandes :
 * - LK : Réponse immédiate pour maintenir la connexion.
 * - AL : Confirmation d'alarme.
 * - UD/UD2/PP : Stockage des positions sans réponse.
 * - CONFIG : Réponse OK.
 *
 * Injection :
 * - deviceDataRepository : Pour persister les données dans la base de données.
 * - tcpSender : Handler injecté pour envoyer des réponses et commandes via TCP.
 */
@Service
@MessageEndpoint
class GpsService(
    public val deviceDataRepository: DeviceDataRepository,
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
            content = gpsMessage.content
        )
        deviceDataRepository.save(deviceData)
        logger.info("Saved DeviceData with id=${deviceData.id}")

        return when (val command = gpsMessage.content.split(",")[0]) {
            "LK" -> handleLinkKeep(gpsMessage)
            "AL" -> handleAlarm(gpsMessage)
            "UD", "UD2", "PP" -> {
                handlePositionData(gpsMessage)
                null // Pas de réponse pour les données de position
            }
            "CONFIG" -> handleConfig(gpsMessage)
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
        logger.info("Generated response: $response")
        return MessageBuilder.withPayload(response).build()
    }

    /**
     * Gère les messages AL (alarme).
     *
     * Répond avec [manufacture*deviceId*0002*AL] pour confirmer la réception de l'alarme.
     *
     * @param gpsMessage Message parsé contenant les détails de l'alarme.
     * @return Message de réponse formaté.
     */
    private fun handleAlarm(gpsMessage: GpsMessage): Message<String> {
        val response = "[${gpsMessage.manufacture}*${gpsMessage.deviceId}*0002*AL]"
        logger.info("Generated response: $response")
        return MessageBuilder.withPayload(response).build()
    }

    /**
     * Gère les messages de position (UD, UD2, PP).
     *
     * Parse les parties du message pour extraire latitude, longitude et batterie (si présents),
     * puis stocke les données. Aucune réponse n'est requise par le protocole.
     *
     * @param gpsMessage Message parsé contenant les données de position.
     * @return Null (pas de réponse).
     */
    private fun handlePositionData(gpsMessage: GpsMessage): Message<String>? {
        val parts = gpsMessage.content.split(",")
        if (parts.size >= 5) {
            deviceDataRepository.save(
                DeviceData(
                    deviceId = gpsMessage.deviceId,
                    type = parts[0],
                    content = gpsMessage.content,
                    latitude = parts[3],
                    longitude = parts[5],
                    batteryStatus = parts[12].toIntOrNull()
                )
            )
            logger.info("Saved position data for deviceId=${gpsMessage.deviceId}")
        }
        return null
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
        logger.info("Generated response: $response")
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
        val fullContent = command + content
        val length = fullContent.length
        val fullCommand = "[3G*$deviceId*${length.toString().padStart(4, '0')}*$fullContent]"
        logger.info("Sending command: $fullCommand")
        tcpSender.handleMessage(MessageBuilder.withPayload(fullCommand).build())
        return fullCommand
    }
}