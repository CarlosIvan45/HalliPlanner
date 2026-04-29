package com.example.halliplanner

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Locale

object AuditFormatter {
    private val formatter = SimpleDateFormat("d/M/yyyy HH:mm", Locale("es", "MX"))

    fun fromDocument(doc: DocumentSnapshot): String {
        val updatedBy = doc.getString("updatedByEmail").orEmpty()
            .ifBlank { doc.getString("createdByEmail").orEmpty() }
            .ifBlank { "Usuario no registrado" }
        val updatedAt = doc.getTimestamp("updatedAt") ?: doc.getTimestamp("createdAt")
        val date = updatedAt?.format().orEmpty()
        return if (date.isBlank()) {
            "Ultimo cambio: $updatedBy"
        } else {
            "Ultimo cambio: $updatedBy | $date"
        }
    }

    private fun Timestamp.format(): String {
        return formatter.format(toDate())
    }
}

