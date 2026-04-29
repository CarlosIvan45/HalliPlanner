package com.example.halliplanner

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var txtGreeting: TextView
    private lateinit var txtOperationsCount: TextView
    private lateinit var txtMeetingsCount: TextView
    private lateinit var txtTasksCount: TextView
    private lateinit var txtEngineersCount: TextView
    private lateinit var txtRevenueTotal: TextView
    private lateinit var txtToday: TextView
    private lateinit var txtHomeFocus: TextView
    private lateinit var txtHomeFocusDetail: TextView
    private lateinit var txtExecutiveList: TextView
    private lateinit var txtExecutiveRisks: TextView
    private lateinit var txtExecutiveOperations: TextView
    private lateinit var txtExecutiveMeetings: TextView
    private lateinit var txtExecutiveWorkload: TextView
    private lateinit var txtExecutiveTasks: TextView
    private lateinit var txtPieSummary: TextView
    private lateinit var txtHomeAlerts: TextView
    private lateinit var txtOperationsChartLabel: TextView
    private lateinit var txtTasksChartLabel: TextView
    private lateinit var txtEngineersChartLabel: TextView
    private lateinit var progressOperations: ProgressBar
    private lateinit var progressTasks: ProgressBar
    private lateinit var progressEngineers: ProgressBar
    private lateinit var pieDashboard: DashboardPieChartView

    private var operationsLine = "Operaciones: sin registros"
    private var meetingsLine = "Reuniones hoy: 0"
    private var tasksLine = "Actividades: sin registros"
    private var workloadLine = "Carga de ingenieros: sin asignaciones"
    private var overdueOperationsCount = 0
    private var overdueTasksCount = 0
    private var activeOperationsCount = 0
    private var openTasksCount = 0
    private var todayMeetingsCount = 0
    private val completedAlerts = linkedMapOf<String, String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        txtGreeting = view.findViewById(R.id.txtGreeting)
        txtOperationsCount = view.findViewById(R.id.txtHomeOperationsCount)
        txtMeetingsCount = view.findViewById(R.id.txtHomeMeetingsCount)
        txtTasksCount = view.findViewById(R.id.txtHomeTasksCount)
        txtEngineersCount = view.findViewById(R.id.txtHomeEngineersCount)
        txtRevenueTotal = view.findViewById(R.id.txtHomeRevenueTotal)
        txtToday = view.findViewById(R.id.txtToday)
        txtHomeFocus = view.findViewById(R.id.txtHomeFocus)
        txtHomeFocusDetail = view.findViewById(R.id.txtHomeFocusDetail)
        txtExecutiveList = view.findViewById(R.id.txtHomeExecutiveList)
        txtExecutiveRisks = view.findViewById(R.id.txtExecutiveRisks)
        txtExecutiveOperations = view.findViewById(R.id.txtExecutiveOperations)
        txtExecutiveMeetings = view.findViewById(R.id.txtExecutiveMeetings)
        txtExecutiveWorkload = view.findViewById(R.id.txtExecutiveWorkload)
        txtExecutiveTasks = view.findViewById(R.id.txtExecutiveTasks)
        txtPieSummary = view.findViewById(R.id.txtPieSummary)
        txtHomeAlerts = view.findViewById(R.id.txtHomeAlerts)
        txtOperationsChartLabel = view.findViewById(R.id.txtOperationsChartLabel)
        txtTasksChartLabel = view.findViewById(R.id.txtTasksChartLabel)
        txtEngineersChartLabel = view.findViewById(R.id.txtEngineersChartLabel)
        progressOperations = view.findViewById(R.id.progressOperations)
        progressTasks = view.findViewById(R.id.progressTasks)
        progressEngineers = view.findViewById(R.id.progressEngineers)
        pieDashboard = view.findViewById(R.id.pieDashboard)

        val calendar = Calendar.getInstance()
        txtToday.text = SimpleDateFormat("EEEE d MMMM yyyy", Locale("es", "MX"))
            .format(calendar.time)
            .replaceFirstChar { it.titlecase(Locale("es", "MX")) }

        loadGreeting()
        loadOperations()
        loadTodayMeetings()
        loadTasks()
        loadEngineers()
    }

    private fun loadGreeting() {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val fullName = doc.getString("name").orEmpty().ifBlank { user.email.orEmpty() }
                val firstName = fullName.trim().split(" ").firstOrNull().orEmpty()
                txtGreeting.text = if (firstName.isBlank()) {
                    "Bienvenido"
                } else {
                    "Bienvenido, $firstName"
                }
            }
            .addOnFailureListener {
                txtGreeting.text = "Bienvenido"
            }
    }

    private fun loadOperations() {
        db.collection("operations")
            .get()
            .addOnSuccessListener { documents ->
                val total = documents.size()
                val active = documents.count { it.getString("status") != "Completada" }
                val completed = documents.count { it.getString("status") == "Completada" }
                overdueOperationsCount = documents.count { doc ->
                    doc.getString("status") != "Completada" &&
                        isOverdue(
                            doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() },
                            doc.getString("endTime").orEmpty()
                        )
                }
                val totalRevenue = documents
                    .filter { it.getString("type") == "Pozo" }
                    .sumOf { it.getDouble("revenue") ?: 0.0 }
                val percent = percent(active, total)

                txtOperationsCount.text = active.toString()
                activeOperationsCount = active
                updatePieChart()
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
                val workload = mutableMapOf<String, Int>()
                documents
                    .filter { it.getString("status") != "Completada" }
                    .forEach { doc ->
                        (doc.get("engineerNames") as? List<*>).orEmpty()
                            .map { it.toString() }
                            .filter { it.isNotBlank() }
                            .forEach { name -> workload[name] = (workload[name] ?: 0) + 1 }
                    }
                workloadLine = if (workload.isEmpty()) {
                    "Carga de ingenieros: sin asignaciones activas"
                } else {
                    "Carga de ingenieros:\n" + workload.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .joinToString("\n") { "${it.key}: ${it.value} asignaciones" }
                }
                documents
                    .filter { it.getString("status") == "Completada" }
                    .forEach { doc ->
                        val title = doc.getString("title").orEmpty().ifBlank { "Operacion finalizada" }
                        val endDate = doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() }
                        completedAlerts["operation:${doc.id}"] =
                            "Operacion finalizada: $title${if (endDate.isNotBlank()) " | Termino: $endDate" else ""}"
                    }
                documents
                    .filter { it.getString("status") != "Completada" }
                    .forEach { doc ->
                        val endDate = doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() }
                        val endTime = doc.getString("endTime").orEmpty()
                        if (isDueSoon(endDate, endTime)) {
                            val title = doc.getString("title").orEmpty().ifBlank { "Operacion sin nombre" }
                            completedAlerts["due-operation:${doc.id}"] =
                                "Operacion por vencer: $title | Termino: ${endDate.ifBlank { "Sin fecha" }} ${endTime.ifBlank { "" }}"
                        }
                        if (isOverdue(endDate, endTime)) {
                            val title = doc.getString("title").orEmpty().ifBlank { "Operacion sin nombre" }
                            completedAlerts["overdue-operation:${doc.id}"] =
                                "Operacion atrasada: $title | Termino: ${endDate.ifBlank { "Sin fecha" }} ${endTime.ifBlank { "" }}"
                        }
                    }
                renderAlerts()
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
                todayMeetingsCount = documents.size()
                txtMeetingsCount.text = todayMeetingsCount.toString()
                updatePieChart()
                meetingsLine = if (documents.isEmpty) {
                    "Reuniones hoy: 0"
                } else {
                    val sample = documents.take(3).joinToString("\n") { doc ->
                        val time = doc.getString("time").orEmpty().ifBlank { "--:--" }
                        val title = doc.getString("title").orEmpty().ifBlank { "Reunion sin titulo" }
                        "$time | $title"
                    }
                    "Reuniones hoy: ${documents.size()}\n$sample"
                }
                documents.forEach { doc ->
                    val title = doc.getString("title").orEmpty().ifBlank { "Reunion sin titulo" }
                    val time = doc.getString("time").orEmpty().ifBlank { "--:--" }
                    completedAlerts["meeting-today:${doc.id}"] = "Reunion de hoy: $time | $title"
                }
                renderAlerts()
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
                overdueTasksCount = documents.count { doc ->
                    doc.getString("status") != "Completada" &&
                        isOverdue(
                            doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() },
                            doc.getString("endTime").orEmpty()
                        )
                }

                txtTasksCount.text = pending.toString()
                openTasksCount = pending
                updatePieChart()
                progressTasks.progress = percent(progress, total)
                txtTasksChartLabel.text = "Actividades en progreso: $progress de $total"
                tasksLine = if (total == 0) {
                    "Actividades: sin registros"
                } else {
                    val nextTasks = documents
                        .filter { it.getString("status") != "Completada" }
                        .take(3)
                        .joinToString("\n") { doc ->
                            val title = doc.getString("title").orEmpty().ifBlank { "Actividad sin titulo" }
                            val endDate = doc.getString("endDate").orEmpty()
                                .ifBlank { doc.getString("date").orEmpty().ifBlank { "Sin termino" } }
                            "$title | Termino: $endDate"
                        }
                    "Actividades pendientes: $pending${if (nextTasks.isNotBlank()) "\n$nextTasks" else ""}"
                }
                documents
                    .filter { it.getString("status") == "Completada" }
                    .forEach { doc ->
                        val title = doc.getString("title").orEmpty().ifBlank { "Actividad finalizada" }
                        val endDate = doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() }
                        completedAlerts["task:${doc.id}"] =
                            "Actividad finalizada: $title${if (endDate.isNotBlank()) " | Termino: $endDate" else ""}"
                    }
                documents
                    .filter { it.getString("status") != "Completada" }
                    .forEach { doc ->
                        val endDate = doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() }
                        val endTime = doc.getString("endTime").orEmpty()
                        if (isDueSoon(endDate, endTime)) {
                            val title = doc.getString("title").orEmpty().ifBlank { "Actividad sin titulo" }
                            completedAlerts["due-task:${doc.id}"] =
                                "Actividad por vencer: $title | Termino: ${endDate.ifBlank { "Sin fecha" }} ${endTime.ifBlank { "" }}"
                        }
                        if (isOverdue(endDate, endTime)) {
                            val title = doc.getString("title").orEmpty().ifBlank { "Actividad sin titulo" }
                            completedAlerts["overdue-task:${doc.id}"] =
                                "Actividad atrasada: $title | Termino: ${endDate.ifBlank { "Sin fecha" }} ${endTime.ifBlank { "" }}"
                        }
                    }
                renderAlerts()
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
        txtExecutiveRisks.text =
            "Riesgos\n$overdueOperationsCount operaciones atrasadas | $overdueTasksCount actividades atrasadas"
        txtExecutiveOperations.text = operationsLine
        txtExecutiveMeetings.text = meetingsLine
        txtExecutiveWorkload.text = workloadLine
        txtExecutiveTasks.text = tasksLine
        txtExecutiveList.text =
            "$operationsLine\n\nRiesgos: $overdueOperationsCount operaciones atrasadas | $overdueTasksCount actividades atrasadas\n\n$workloadLine\n\nAgenda de hoy:\n$meetingsLine\n\nSeguimiento:\n$tasksLine"
    }

    private fun renderAlerts() {
        val alertIds = completedAlerts.keys
        val unseen = AppSettings.unseenCompletionAlerts(requireContext(), alertIds)
        txtHomeAlerts.text = if (completedAlerts.isEmpty()) {
            "Sin alertas nuevas"
        } else {
            "${completedAlerts.size} finalizaciones detectadas"
        }

        if (unseen.isNotEmpty()) {
            val message = unseen.mapNotNull { completedAlerts[it] }.joinToString("\n\n")
            NotificationHelper.showCompletionAlert(
                requireContext(),
                "Alertas operativas",
                message
            )
            AlertDialog.Builder(requireContext())
                .setIcon(R.drawable.ic_nav_home)
                .setTitle("Alertas operativas")
                .setMessage(message)
                .setPositiveButton("Entendido") { _, _ ->
                    AppSettings.markCompletionAlertsSeen(requireContext(), unseen)
                }
                .show()
                .also { DialogStyle.apply(it) }
        }
    }

    private fun updatePieChart() {
        pieDashboard.setValues(activeOperationsCount, openTasksCount, todayMeetingsCount)
        val total = activeOperationsCount + openTasksCount + todayMeetingsCount
        val operationsPercent = percent(activeOperationsCount, total)
        val tasksPercent = percent(openTasksCount, total)
        val meetingsPercent = percent(todayMeetingsCount, total)
        txtPieSummary.text =
            "Rojo operaciones: $operationsPercent% ($activeOperationsCount)\nRosa actividades: $tasksPercent% ($openTasksCount)\nGris reuniones: $meetingsPercent% ($todayMeetingsCount)"
    }

    private fun percent(value: Int, total: Int): Int {
        return if (total <= 0) 0 else ((value.toFloat() / total.toFloat()) * 100).toInt()
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        return "$day/$month/$year"
    }

    private fun isDueSoon(date: String, time: String): Boolean {
        val dueAt = parseDateTime(date, time) ?: return false
        val now = System.currentTimeMillis()
        val next24Hours = now + 24L * 60L * 60L * 1000L
        return dueAt in now..next24Hours
    }

    private fun isOverdue(date: String, time: String): Boolean {
        val dueAt = parseDateTime(date, time) ?: return false
        return dueAt < System.currentTimeMillis()
    }

    private fun parseDateTime(date: String, time: String): Long? {
        if (date.isBlank()) return null
        val value = "$date ${time.ifBlank { "23:59" }}"
        return runCatching {
            SimpleDateFormat("d/M/yyyy HH:mm", Locale("es", "MX")).parse(value)?.time
        }.getOrNull()
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}
