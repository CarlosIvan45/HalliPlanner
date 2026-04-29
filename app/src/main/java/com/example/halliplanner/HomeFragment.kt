package com.example.halliplanner

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var db: FirebaseFirestore
    private lateinit var txtOperationsCount: TextView
    private lateinit var txtMeetingsCount: TextView
    private lateinit var txtTasksCount: TextView
    private lateinit var txtEngineersCount: TextView
    private lateinit var txtRevenueTotal: TextView
    private lateinit var txtToday: TextView
    private lateinit var txtHomeFocus: TextView
    private lateinit var txtHomeFocusDetail: TextView
    private lateinit var txtExecutiveList: TextView
    private lateinit var txtOperationsChartLabel: TextView
    private lateinit var txtTasksChartLabel: TextView
    private lateinit var txtEngineersChartLabel: TextView
    private lateinit var progressOperations: ProgressBar
    private lateinit var progressTasks: ProgressBar
    private lateinit var progressEngineers: ProgressBar

    private var operationsLine = "Operaciones: sin registros"
    private var meetingsLine = "Reuniones hoy: 0"
    private var tasksLine = "Actividades: sin registros"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        txtOperationsCount = view.findViewById(R.id.txtHomeOperationsCount)
        txtMeetingsCount = view.findViewById(R.id.txtHomeMeetingsCount)
        txtTasksCount = view.findViewById(R.id.txtHomeTasksCount)
        txtEngineersCount = view.findViewById(R.id.txtHomeEngineersCount)
        txtRevenueTotal = view.findViewById(R.id.txtHomeRevenueTotal)
        txtToday = view.findViewById(R.id.txtToday)
        txtHomeFocus = view.findViewById(R.id.txtHomeFocus)
        txtHomeFocusDetail = view.findViewById(R.id.txtHomeFocusDetail)
        txtExecutiveList = view.findViewById(R.id.txtHomeExecutiveList)
        txtOperationsChartLabel = view.findViewById(R.id.txtOperationsChartLabel)
        txtTasksChartLabel = view.findViewById(R.id.txtTasksChartLabel)
        txtEngineersChartLabel = view.findViewById(R.id.txtEngineersChartLabel)
        progressOperations = view.findViewById(R.id.progressOperations)
        progressTasks = view.findViewById(R.id.progressTasks)
        progressEngineers = view.findViewById(R.id.progressEngineers)

        val calendar = Calendar.getInstance()
        txtToday.text = SimpleDateFormat("EEEE d MMMM yyyy", Locale("es", "MX"))
            .format(calendar.time)
            .replaceFirstChar { it.titlecase(Locale("es", "MX")) }

        loadOperations()
        loadTodayMeetings()
        loadTasks()
        loadEngineers()
    }

    private fun loadOperations() {
        db.collection("operations")
            .get()
            .addOnSuccessListener { documents ->
                val total = documents.size()
                val active = documents.count { it.getString("status") != "Completada" }
                val completed = documents.count { it.getString("status") == "Completada" }
                val totalRevenue = documents
                    .filter { it.getString("type") == "Pozo" }
                    .sumOf { it.getDouble("revenue") ?: 0.0 }
                val percent = percent(active, total)

                txtOperationsCount.text = active.toString()
                txtRevenueTotal.text = "$${String.format("%,.2f", totalRevenue)}"
                txtHomeFocus.text = "$active operaciones activas"
                txtHomeFocusDetail.text = if (total == 0) {
                    "Crea operaciones y asigna ingenieros para iniciar la planeacion."
                } else {
                    "$completed completadas de $total operaciones registradas."
                }
                progressOperations.progress = percent
                txtOperationsChartLabel.text = "Operaciones activas: $active de $total"

                operationsLine = if (total == 0) {
                    "Operaciones: sin registros"
                } else {
                    val sample = documents.take(3).joinToString("\n") { doc ->
                        val title = doc.getString("title").orEmpty().ifBlank { "Operacion sin nombre" }
                        val type = doc.getString("type").orEmpty().ifBlank { "General" }
                        "[$type] $title"
                    }
                    "Operaciones destacadas:\n$sample"
                }
                renderExecutiveList()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "No se pudieron cargar las operaciones", error)
                txtOperationsCount.text = "0"
            }
    }

    private fun loadTodayMeetings() {
        val calendar = Calendar.getInstance()
        val today = formatDate(
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )

        db.collection("meetings")
            .whereEqualTo("date", today)
            .get()
            .addOnSuccessListener { documents ->
                txtMeetingsCount.text = documents.size().toString()
                meetingsLine = if (documents.isEmpty) {
                    "Reuniones hoy: 0"
                } else {
                    "Reuniones hoy: ${documents.size()}"
                }
                renderExecutiveList()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "No se pudieron cargar las reuniones de hoy", error)
                txtMeetingsCount.text = "0"
            }
    }

    private fun loadTasks() {
        db.collection("tasks")
            .get()
            .addOnSuccessListener { documents ->
                val total = documents.size()
                val pending = documents.count { it.getString("status") != "Completada" }
                val progress = documents.count { it.getString("status") == "En progreso" }

                txtTasksCount.text = pending.toString()
                progressTasks.progress = percent(progress, total)
                txtTasksChartLabel.text = "Actividades en progreso: $progress de $total"
                tasksLine = "Actividades pendientes: $pending"
                renderExecutiveList()
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "No se pudieron cargar las actividades", error)
                txtTasksCount.text = "0"
            }
    }

    private fun loadEngineers() {
        db.collection("engineers")
            .get()
            .addOnSuccessListener { documents ->
                val total = documents.size()
                val design = documents.count { it.getString("type") == "Diseno" }
                val field = documents.count { it.getString("type") == "Campo" }

                txtEngineersCount.text = total.toString()
                progressEngineers.progress = percent(field, total)
                txtEngineersChartLabel.text = "Campo $field | Diseno $design"
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "No se pudieron cargar los ingenieros", error)
                txtEngineersCount.text = "0"
            }
    }

    private fun renderExecutiveList() {
        txtExecutiveList.text = "$operationsLine\n\n$meetingsLine\n$tasksLine"
    }

    private fun percent(value: Int, total: Int): Int {
        return if (total <= 0) 0 else ((value.toFloat() / total.toFloat()) * 100).toInt()
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        return "$day/$month/$year"
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}
