package com.example.halliplanner

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var meetingListView: ListView
    private lateinit var btnAddMeeting: MaterialButton
    private lateinit var btnPickDate: MaterialButton
    private lateinit var btnWeekView: MaterialButton
    private lateinit var btnMonthView: MaterialButton
    private lateinit var txtSelectedDate: TextView
    private lateinit var txtAgendaDay: TextView
    private lateinit var txtAgendaSummary: TextView
    private lateinit var txtAgendaRangeSummary: TextView
    private lateinit var txtMeetingEmpty: TextView
    private lateinit var meetingList: ArrayList<PlannerMeeting>
    private lateinit var meetingIds: ArrayList<String>
    private lateinit var adapter: MeetingAdapter
    private var selectedDate = ""

    companion object {
        private const val TAG = "ScheduleFragment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        meetingListView = view.findViewById(R.id.meetingList)
        btnAddMeeting = view.findViewById(R.id.btnAddMeeting)
        btnPickDate = view.findViewById(R.id.btnPickDate)
        btnWeekView = view.findViewById(R.id.btnWeekView)
        btnMonthView = view.findViewById(R.id.btnMonthView)
        txtSelectedDate = view.findViewById(R.id.txtSelectedDate)
        txtAgendaDay = view.findViewById(R.id.txtAgendaDay)
        txtAgendaSummary = view.findViewById(R.id.txtAgendaSummary)
        txtAgendaRangeSummary = view.findViewById(R.id.txtAgendaRangeSummary)
        txtMeetingEmpty = view.findViewById(R.id.txtMeetingEmpty)

        meetingList = ArrayList()
        meetingIds = ArrayList()
        adapter = MeetingAdapter(meetingList)
        meetingListView.adapter = adapter
        meetingListView.emptyView = txtMeetingEmpty
        ListScrollHelper.enableNestedScrolling(meetingListView)

        val calendar = Calendar.getInstance()
        selectedDate = formatDate(
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )
        updateSelectedDateLabel()
        loadMeetings(selectedDate)

        btnPickDate.setOnClickListener { showDatePicker() }
        btnWeekView.setOnClickListener { loadRangeMeetings(RangeMode.WEEK) }
        btnMonthView.setOnClickListener { loadRangeMeetings(RangeMode.MONTH) }

        btnAddMeeting.setOnClickListener {
            showMeetingDialog()
        }

        meetingListView.setOnItemClickListener { _, _, position, _ ->
            showMeetingDialog(meetingList[position], meetingIds[position])
        }

        meetingListView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteMeeting(meetingList[position], meetingIds[position])
            true
        }
    }

    private fun showMeetingDialog(meeting: PlannerMeeting? = null, meetingId: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_meeting, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.inputMeetingTitle)
        val operationInput = dialogView.findViewById<EditText>(R.id.inputMeetingOperation)
        val locationInput = dialogView.findViewById<EditText>(R.id.inputLocation)
        val attendeesInput = dialogView.findViewById<EditText>(R.id.inputAttendees)
        val timeInput = dialogView.findViewById<EditText>(R.id.inputTime)
        val calendar = Calendar.getInstance()

        meeting?.let {
            titleInput.setText(it.title)
            operationInput.setText(it.operation)
            locationInput.setText(it.location)
            attendeesInput.setText(it.attendees)
            timeInput.setText(it.time)
        }

        timeInput.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    timeInput.setText(String.format("%02d:%02d", hour, minute))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        AlertDialog.Builder(requireContext())
            .setIcon(R.drawable.ic_nav_agenda)
            .setTitle(if (meeting == null) "Planear reunion" else "Editar reunion")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val title = titleInput.text.toString().trim()
                val operation = operationInput.text.toString().trim()
                val location = locationInput.text.toString().trim()
                val attendees = attendeesInput.text.toString().trim()
                val time = timeInput.text.toString().trim()

                if (title.isBlank()) {
                    Toast.makeText(context, "Agrega un titulo", Toast.LENGTH_SHORT).show()
                } else {
                    saveMeeting(meetingId, title, operation, location, attendees, time)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .also { DialogStyle.apply(it) }
    }

    private fun saveMeeting(
        meetingId: String?,
        title: String,
        operation: String,
        location: String,
        attendees: String,
        time: String
    ) {
        val meeting = hashMapOf(
            "title" to title,
            "operation" to operation,
            "location" to location,
            "attendees" to attendees,
            "time" to time,
            "date" to selectedDate,
            "updatedBy" to auth.currentUser?.uid.orEmpty(),
            "updatedByEmail" to auth.currentUser?.email.orEmpty(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        validateMeetingAvailability(meetingId, attendees, time) {
            saveMeetingData(meetingId, meeting)
        }
    }

    private fun validateMeetingAvailability(
        meetingId: String?,
        attendees: String,
        time: String,
        onAvailable: () -> Unit
    ) {
        val selected = AvailabilityHelper.namesFromText(attendees)
        if (selected.isEmpty() || time.isBlank()) {
            onAvailable()
            return
        }

        db.collection("meetings")
            .whereEqualTo("date", selectedDate)
            .get()
            .addOnSuccessListener { meetings ->
                val meetingConflict = meetings.firstOrNull { doc ->
                    doc.id != meetingId &&
                        doc.getString("time").orEmpty() == time &&
                        AvailabilityHelper.namesFromText(doc.getString("attendees").orEmpty()).any { it in selected }
                }
                if (meetingConflict != null) {
                    val title = meetingConflict.getString("title").orEmpty().ifBlank { "otra reunion" }
                    Toast.makeText(context, "Disponibilidad bloqueada: hay asistentes en $title a la misma hora.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                db.collection("operations")
                    .get()
                    .addOnSuccessListener { operations ->
                        val operationConflict = operations.firstOrNull { doc ->
                            doc.getString("status") != "Completada" &&
                                (doc.get("engineerNames") as? List<*>).orEmpty()
                                    .map { AvailabilityHelper.normalizeName(it.toString()) }
                                    .any { it in selected } &&
                                AvailabilityHelper.dateTimeInsideRange(
                                    selectedDate,
                                    time,
                                    doc.getString("startDate").orEmpty().ifBlank { doc.getString("date").orEmpty() },
                                    doc.getString("startTime").orEmpty(),
                                    doc.getString("endDate").orEmpty().ifBlank { doc.getString("date").orEmpty() },
                                    doc.getString("endTime").orEmpty()
                                )
                        }
                        if (operationConflict == null) {
                            onAvailable()
                        } else {
                            val title = operationConflict.getString("title").orEmpty().ifBlank { "una operacion" }
                            Toast.makeText(context, "Disponibilidad bloqueada: hay asistentes asignados a $title en ese horario.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { showFirestoreError("No se pudo validar operaciones", it) }
            }
            .addOnFailureListener { showFirestoreError("No se pudo validar reuniones", it) }
    }

    private fun saveMeetingData(meetingId: String?, meeting: HashMap<String, Any>) {
        if (meetingId == null) {
            meeting["createdBy"] = auth.currentUser?.uid.orEmpty()
            meeting["createdByEmail"] = auth.currentUser?.email.orEmpty()
            meeting["createdAt"] = FieldValue.serverTimestamp()
            db.collection("meetings")
                .add(meeting)
                .addOnSuccessListener {
                    Toast.makeText(context, "Reunion guardada", Toast.LENGTH_SHORT).show()
                    loadMeetings(selectedDate)
                }
                .addOnFailureListener {
                    showFirestoreError("Error al guardar la reunion", it)
                }
        } else {
            db.collection("meetings").document(meetingId)
                .set(meeting)
                .addOnSuccessListener {
                    Toast.makeText(context, "Reunion actualizada", Toast.LENGTH_SHORT).show()
                    loadMeetings(selectedDate)
                }
                .addOnFailureListener {
                    showFirestoreError("Error al actualizar la reunion", it)
                }
        }
    }

    private fun loadMeetings(date: String) {
        db.collection("meetings")
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { documents ->
                meetingList.clear()
                meetingIds.clear()

                for (doc in documents) {
                    meetingList.add(
                        PlannerMeeting(
                            title = doc.getString("title").orEmpty(),
                            operation = doc.getString("operation").orEmpty(),
                            location = doc.getString("location").orEmpty(),
                            attendees = doc.getString("attendees").orEmpty(),
                            time = doc.getString("time").orEmpty(),
                            audit = AuditFormatter.fromDocument(doc)
                        )
                    )
                    meetingIds.add(doc.id)
                }

                adapter.notifyDataSetChanged()
                txtAgendaSummary.text = "${documents.size()} reuniones programadas"
            }
            .addOnFailureListener { error ->
                showFirestoreError("No se pudieron cargar las reuniones", error)
            }
    }

    private fun confirmDeleteMeeting(meeting: PlannerMeeting, meetingId: String) {
        AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_menu_delete)
            .setTitle("Eliminar reunion")
            .setMessage("Quieres eliminar ${meeting.title.ifBlank { "esta reunion" }}?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("meetings").document(meetingId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Reunion eliminada", Toast.LENGTH_SHORT).show()
                        loadMeetings(selectedDate)
                    }
                    .addOnFailureListener {
                        showFirestoreError("Error al eliminar la reunion", it)
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
            .also { DialogStyle.apply(it) }
    }

    private fun updateSelectedDateLabel() {
        txtSelectedDate.text = "Agenda para $selectedDate"
        val parsed = SimpleDateFormat("d/M/yyyy", Locale("es", "MX")).parse(selectedDate)
        txtAgendaDay.text = if (parsed == null) {
            selectedDate
        } else {
            SimpleDateFormat("EEEE d MMMM", Locale("es", "MX"))
                .format(parsed)
                .replaceFirstChar { it.titlecase(Locale("es", "MX")) }
        }
        btnPickDate.text = selectedDate
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedDate = formatDate(day, month + 1, year)
                updateSelectedDateLabel()
                loadMeetings(selectedDate)
                txtAgendaRangeSummary.text = "Selecciona semana o mes para ver el resumen."
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        return "$day/$month/$year"
    }

    private fun showFirestoreError(message: String, error: Exception) {
        Log.e(TAG, message, error)
        val detail = error.localizedMessage ?: error.javaClass.simpleName
        Toast.makeText(context, "$message: $detail", Toast.LENGTH_LONG).show()
    }

    private fun loadRangeMeetings(mode: RangeMode) {
        val selected = parseDate(selectedDate) ?: return
        val start = selected.clone() as Calendar
        val end = selected.clone() as Calendar

        if (mode == RangeMode.WEEK) {
            start.firstDayOfWeek = Calendar.MONDAY
            start.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            end.time = start.time
            end.add(Calendar.DAY_OF_MONTH, 6)
        } else {
            start.set(Calendar.DAY_OF_MONTH, 1)
            end.time = start.time
            end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        db.collection("meetings")
            .get()
            .addOnSuccessListener { docs ->
                val grouped = docs
                    .mapNotNull { doc ->
                        val date = doc.getString("date").orEmpty()
                        val calendar = parseDate(date)
                        if (calendar != null && !calendar.before(start) && !calendar.after(end)) {
                            date to doc
                        } else {
                            null
                        }
                    }
                    .groupBy { it.first }

                val title = if (mode == RangeMode.WEEK) "Semana" else "Mes"
                val total = grouped.values.sumOf { it.size }
                val detail = grouped.entries
                    .sortedBy { parseDate(it.key)?.timeInMillis ?: 0L }
                    .joinToString("\n") { (date, items) ->
                        "$date: ${items.size} reuniones"
                    }
                    .ifBlank { "Sin reuniones en este periodo." }

                txtAgendaRangeSummary.text = "$title seleccionado: $total reuniones\n$detail"
            }
            .addOnFailureListener { showFirestoreError("No se pudo cargar el resumen", it) }
    }

    private fun parseDate(value: String): Calendar? {
        return runCatching {
            val date = SimpleDateFormat("d/M/yyyy", Locale("es", "MX")).parse(value) ?: return null
            Calendar.getInstance().apply { time = date }
        }.getOrNull()
    }

    private inner class MeetingAdapter(private val items: List<PlannerMeeting>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): PlannerMeeting = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_meeting, parent, false)
            val meeting = getItem(position)

            row.findViewById<TextView>(R.id.txtMeetingTime).text =
                meeting.time.ifBlank { "--:--" }
            row.findViewById<TextView>(R.id.txtMeetingTitle).text =
                meeting.title.ifBlank { "Reunion sin titulo" }
            row.findViewById<TextView>(R.id.txtMeetingMeta).text =
                "${meeting.operation.ifBlank { "Tema general" }} | ${meeting.location.ifBlank { "Sin lugar" }}"
            row.findViewById<TextView>(R.id.txtMeetingPeople).text =
                "Asistentes: ${meeting.attendees.ifBlank { "Sin asignar" }}"
            row.findViewById<TextView>(R.id.txtMeetingAudit).text = meeting.audit
            row.findViewById<MaterialButton>(R.id.btnEditMeeting).setOnClickListener {
                val index = meetingList.indexOf(meeting)
                if (index >= 0) {
                    showMeetingDialog(meeting, meetingIds[index])
                }
            }
            row.findViewById<MaterialButton>(R.id.btnDeleteMeeting).setOnClickListener {
                val index = meetingList.indexOf(meeting)
                if (index >= 0) {
                    confirmDeleteMeeting(meeting, meetingIds[index])
                }
            }

            return row
        }
    }

    private data class PlannerMeeting(
        val title: String,
        val operation: String,
        val location: String,
        val attendees: String,
        val time: String,
        val audit: String
    )

    private enum class RangeMode {
        WEEK,
        MONTH
    }
}
