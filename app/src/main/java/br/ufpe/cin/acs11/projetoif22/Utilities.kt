package br.ufpe.cin.acs11.projetoif22

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Utilities {
    companion object {
        fun formatDate(date: Date): String {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return dateFormat.format(date)
        }
    }
}