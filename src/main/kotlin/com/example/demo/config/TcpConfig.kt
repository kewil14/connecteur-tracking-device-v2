package com.example.demo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter
import org.springframework.integration.ip.tcp.TcpSendingMessageHandler
import org.springframework.integration.ip.tcp.connection.TcpNetServerConnectionFactory
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer
import org.springframework.messaging.MessageChannel

/**
 * Configuration du serveur TCP pour l'intégration Spring.
 *
 * Cette classe configure le serveur TCP pour écouter les connexions entrantes des dispositifs GPS.
 * Elle utilise les propriétés de configuration pour définir le port TCP (par défaut 9001) et l'hôte (par défaut localhost).
 * Le serveur est configuré pour maintenir les connexions ouvertes (keep-alive) et éviter les retards avec TCP No Delay.
 * Les réponses sont routées via un canal de sortie vers TcpSendingMessageHandler.
 *
 * Beans définis :
 * - tcpInChannel : Canal pour recevoir les messages entrants.
 * - tcpOutChannel : Canal pour envoyer les messages sortants.
 * - serverAdapter : Adaptateur pour recevoir les connexions TCP.
 * - tcpServerConnectionFactory : Factory pour créer le serveur TCP avec options de socket.
 * - tcpOutbound : Handler pour envoyer des commandes sortantes.
 */
@Configuration
class TcpConfig {

    @Value("\${tcp.server.port:9001}")
    private var tcpPort: Int = 9001

    @Value("\${tcp.server.host:localhost}")
    private var tcpHost: String = "localhost"

    /**
     * Crée le canal d'entrée pour les messages TCP.
     *
     * @return MessageChannel pour recevoir les payloads des connexions.
     */
    @Bean
    fun tcpInChannel(): MessageChannel = DirectChannel()

    /**
     * Crée le canal de sortie pour les messages TCP.
     *
     * @return MessageChannel pour envoyer les réponses aux clients.
     */
    @Bean
    fun tcpOutChannel(): MessageChannel = DirectChannel()

    /**
     * Crée l'adaptateur pour recevoir les connexions TCP et les router vers le canal d'entrée.
     *
     * @return TcpReceivingChannelAdapter configuré avec la factory de connexion.
     */
    @Bean
    fun serverAdapter(): TcpReceivingChannelAdapter {
        val factory = tcpServerConnectionFactory()
        val adapter = TcpReceivingChannelAdapter()
        adapter.setConnectionFactory(factory)
        adapter.setOutputChannel(tcpInChannel())
        return adapter
    }

    /**
     * Crée la factory de connexion TCP pour le serveur.
     *
     * Options :
     * - Hôte : Injecté depuis les propriétés (${tcp.server.host}, défaut localhost).
     * - Port : Injecté depuis les propriétés (${tcp.server.port}).
     * - TCP No Delay : Activé pour éviter les retards dans l'envoi de petits paquets.
     * - Keep Alive : Activé pour maintenir les connexions ouvertes, évitant les déconnexions.
     * - Sérialiseurs : Configure ByteArrayCrLfSerializer pour délimiter les messages par CRLF.
     *
     * @return TcpNetServerConnectionFactory configurée.
     */
    @Bean
    fun tcpServerConnectionFactory(): TcpNetServerConnectionFactory {
        val factory = TcpNetServerConnectionFactory(tcpPort)
        factory.setHost(tcpHost)
        factory.setSoTcpNoDelay(true)
        factory.setSoKeepAlive(true)
        factory.setSoLinger(10) // Temps de linger pour fermer proprement les sockets

        val serializer = ByteArrayCrLfSerializer()
        factory.setDeserializer(serializer) // Délimite les messages entrants par CRLF
        factory.setSerializer(serializer)    // Sérialise les réponses avec CRLF

        return factory
    }

    /**
     * Crée un flux pour router les messages sortants du canal tcpOutChannel vers tcpOutbound.
     *
     * @return IntegrationFlow configuré pour envoyer les réponses.
     */
    @Bean
    fun outboundFlow(): IntegrationFlow {
        return IntegrationFlow.from(tcpOutChannel())
            .handle(tcpOutbound())
            .get()
    }

    /**
     * Crée le handler pour envoyer des messages sortants via TCP.
     *
     * Utilisé pour envoyer des réponses et commandes aux dispositifs connectés.
     * Configure la connexion via la factory.
     *
     * @return TcpSendingMessageHandler configuré.
     */
    @Bean
    fun tcpOutbound(): TcpSendingMessageHandler {
        val factory = tcpServerConnectionFactory()
        val handler = TcpSendingMessageHandler()
        handler.setConnectionFactory(factory)
        return handler
    }
}