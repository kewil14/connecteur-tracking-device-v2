package com.example.demo.repository

import com.example.demo.entity.DeviceData
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Répository JPA pour l'entité DeviceData.
 *
 * Fournit des méthodes CRUD automatiques et une requête personnalisée pour filtrer par deviceId et type.
 * Utilisé pour persister et récupérer les données des dispositifs.
 */
interface DeviceDataRepository : JpaRepository<DeviceData, Long> {
    /**
     * Trouve les données par ID du dispositif et type de message.
     *
     * @param deviceId ID du dispositif.
     * @param type Type de message (ex. LK, UD).
     * @return Liste des DeviceData correspondantes.
     */
    fun findByDeviceIdAndType(deviceId: String, type: String): List<DeviceData>
}