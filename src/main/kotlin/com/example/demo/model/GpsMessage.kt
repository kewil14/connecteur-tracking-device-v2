package com.example.demo.model

/**
 * Modèle représentant un message GPS au format du protocole.
 *
 * Structure : [manufacture*deviceId*LEN*content]
 * - manufacture : Préfixe (ex. 3G ou CS).
 * - deviceId : ID du dispositif (10 chiffres).
 * - length : Longueur du content en ASCII 4 chiffres (ex. 000D pour 13).
 * - content : Contenu du message (ex. LK,50,100,100).
 *
 * Méthode statique parse pour extraire les composants d'une chaîne brute.
 */
data class GpsMessage(
    val manufacture: String,
    val deviceId: String,
    val length: Int,
    val content: String
) {
    companion object {
        /**
         * Parse une chaîne de message brute en GpsMessage.
         *
         * Valide le format : Doit commencer par [ et finir par ], et avoir exactement 4 parties séparées par *.
         * Vérifie que la longueur déclarée correspond au contenu réel.
         *
         * @param message Chaîne brute (ex. [3G*8800000015*000D*LK,50,100,100]).
         * @return GpsMessage si valide, sinon null.
         */
        fun parse(message: String): GpsMessage? {
            if (!message.startsWith("[") || !message.endsWith("]")) return null
            val contentStr = message.substring(1, message.length - 1)
            val parts = contentStr.split("*")
            if (parts.size != 4) return null
            val manufacture = parts[0]
            val deviceId = parts[1]
            val lengthStr = parts[2]
            val length = lengthStr.toIntOrNull() ?: lengthStr.toIntOrNull(16) ?: return null
            val actualContent = parts[3]
            if (actualContent.length != length) return null
            return GpsMessage(manufacture, deviceId, length, actualContent)
        }
    }
}