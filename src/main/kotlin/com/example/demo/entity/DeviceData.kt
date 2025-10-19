package com.example.demo.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.cglib.core.Local
import java.time.LocalDateTime
import java.util.Date

/**
 * Entité JPA représentant les données reçues d'un dispositif GPS.
 *
 * Cette entité stocke les informations des messages entrants, telles que le type (ex. LK, UD),
 * le contenu brut, les coordonnées GPS (si applicables) et l'état de la batterie.
 * Elle est persistée dans la base de données H2 (ou autre via configuration).
 *
 * Champs :
 * - id : Identifiant auto-généré.
 * - deviceId : ID du dispositif (extrait de l'IMEI).
 * - type : Type de message (ex. LK, UD, AL).
 * - content : Contenu brut du message.
 * - timestamp : Date/heure de réception.
 * - latitude/longitude : Coordonnées GPS (null si non présent).
 * - batteryStatus : Niveau de batterie (null si non présent).
 */
@Entity
data class DeviceData(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val deviceId: String,
    val type: String,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val batteryStatus: Int? = null,
    val signalStrength: Int? = null,
    val receivedAt: LocalDateTime? = null,
    val imageData: String? = null
)

