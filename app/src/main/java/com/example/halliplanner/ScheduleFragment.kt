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
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
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
    private lateinit var meetingList: ArrayList<AgendaItem>
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
            val item = meetingList[position]
            if (item.kind == AgendaKind.MEETING) {
                showMeetingDialog(item.toPlannerMeeting(), meetingIds[position])
            }
        }

        meetingListView.setOnItemLongClickListener { _, _, position, _ ->
            val item = meetingList[position]
            if (item.kind == AgendaKind.MEETING) {
                confirmDeleteMeeting(item.toPlannerMeeting(), meetingIds[position])
            }
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
        val user = auth.currentUser ?: run {
            Toast.makeText(context, "Inicia sesion para guardar", Toast.LENGTH_SHORT).show()
            return
        }
        val meeting = hashMapOf(
            "title" to title,
            "operation" to operation,
            "location" to location,
            "attendees" to attendees,
            "time" to time,
            "date" to selectedDate,
            "updatedBy" to user.uid,
            "updatedByEmail" to user.email.orEmpty(),
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
        val uid = auth.currentUser?.uid ?: return

        db.collection("meetings")
            .whereEqualTo("createdBy", uid)
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
                    .whereEqualTo("createdBy", uid)
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
        val user = auth.currentUser ?: run {
            Toast.makeText(context, "Inicia sesion para guardar", Toast.LENGTH_SHORT).show()
            return
        }
        if (meetingId == null) {
            meeting["createdBy"] = user.uid
            meeting["createdByEmail"] = user.email.orEmpty()
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
                .set(meeting, SetOptions.merge())
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
        val uid = auth.currentUser?.uid ?: return
        val loadedMeetings = ArrayList<AgendaItem>()
        val loadedTasks = ArrayList<AgendaItem>()
        val loadedOperations = ArrayList<AgendaItem>()

        db.collection("meetings")
            .whereEqualTo("createdBy", uid)
            .whereEqualTo("date", date)
            .get()
            .addOnSuccessListener { documents ->
                loadedMeetings.addAll(documents.toAgendaMeetings())
                db.collection("tasks")
                    .whereEqualTo("createdBy", uid)
                    .get()
                    .addOnSuccessListener { tasks ->
                        loadedTasks.addAll(tasks.toAgendaTasks(date))
                        db.collection("operations")
                            .whereEqualTo("createdBy", uid)
                            .get()
                            .addOnSuccessListener { operations ->
                                loadedOperations.addAll(operations.toAgendaOperations(date))
                                renderAgendaItems(loadedMeetings, loadedTasks, loadedOperations)
                            }
                            .addOnFailureListener { error ->
                                showFirestoreError("No se pudieron cargar las operaciones", error)
                            }
                    }
                    .addOnFailureListener { error ->
                        showFirestoreError("No se pudieron cargar las actividades", error)
                    }
            }
            .addOnFailureListener { error ->
                showFirestoreError("No se pudieron cargar las reuniones", error)
            }
    }

    private fun renderAgendaItems(
        meetings: List<AgendaItem>,
        tasks: List<AgendaItem>,
        operations: List<AgendaItem>
    ) {
        val items = (meetings + tasks + operations).sortedWith(
            compareBy<AgendaItem> { it.time.ifBlank { "99:99" } }
                .thenBy { it.kind.ordinal }
                .thenBy { it.title }
        )

        meetingList.clear()
        meetingIds.clear()
        items.forEach { item ->
            meetingList.add(item)
            meetingIds.add(item.id)
        }

        adapter.notifyDataSetChanged()
        txtAgendaSummary.text =
            "${items.size} eventos: ${meetings.size} reuniones, ${tasks.size} actividades, ${operations.size} operaciones"
    }

    private fun confirmDeleteMeeting(meeting: PlannerMeeting, meetingId: String) {
        AlertDialog.Builder(requireContext())
            .setIcon(android.R.drawable.ic_menu_delete)
            .setTitle("Eliminar reunion")
            .setMessage("Quieres eliminar ${meeting.title.ifBlank { "esta reunion" }}?")
            .setPositiveButton("Eliminar") { _, _ ->
                auth.currentUser?.uid ?: run {
                    Toast.makeText(context, "Inicia sesion para eliminar", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
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

        val uid = auth.currentUser?.uid ?: return
        db.collection("meetings")
            .whereEqualTo("createdBy", uid)
            .get()
            .addOnSuccessListener { meetings ->
                db.collection("tasks")
                    .whereEqualTo("createdBy", uid)
                    .get()
                    .addOnSuccessListener { tasks ->
                        db.collection("operations")
                            .whereEqualTo("createdBy", uid)
                            .get()
                            .addOnSuccessListener { operations ->
                                renderRangeSummary(mode, start, end, meetings, tasks, operations)
                            }
                            .addOnFailureListener { showFirestoreError("No se pudo cargar el resumen de operaciones", it) }
                    }
                    .addOnFailureListener { showFirestoreError("No se pudo cargar el resumen de actividades", it) }
            }
            .addOnFailureListener { showFirestoreError("No se pudo cargar el resumen", it) }
    }

    private fun renderRangeSummary(
        mode: RangeMode,
        start: Calendar,
        end: Calendar,
        meetings: QuerySnapshot,
        tasks: QuerySnapshot,
        operations: QuerySnapshot
    ) {
        val grouped = linkedMapOf<String, Int>()

        fun addDate(date: String) {
            val calendar = parseDate(date)
            if (calendar != null && !calendar.before(start) && !calendar.after(end)) {
                grouped[date] = (grouped[date] ?: 0) + 1
            }
        }

        meetings.forEach { doc -> addDate(doc.getString("date").orEmpty()) }
        tasks.forEach { doc ->
            addTouchedDates(
                doc.getString("date").orEmpty(),
                doc.getString("startDate").orEmpty(),
                doc.getString("endDate").orEmpty(),
                ::addDate
            )
        }
        operations.forEach { doc ->
            addTouchedDates(
                doc.getString("date").orEmpty(),
                doc.getString("startDate").orEmpty(),
                doc.getString("endDate").orEmpty(),
                ::addDate
            )
        }

        val title = if (mode == RangeMode.WEEK) "Semana" else "Mes"
        val total = grouped.values.sum()
        val detail = grouped.entries
            .sortedBy { parseDate(it.key)?.timeInMillis ?: 0L }
            .joinToString("\n") { (date, count) -> "$date: $count eventos" }
            .ifBlank { "Sin eventos en este periodo." }

        txtAgendaRangeSummary.text = "$title seleccionado: $total eventos\n$detail"
    }

    private fun addTouchedDates(mainDate: String, startDate: String, endDate: String, addDate: (String) -> Unit) {
        val start = startDate.ifBlank { mainDate }
        val end = endDate.ifBlank { start }
        if (start.isBlank()) return

        val startCalendar = parseDate(start) ?: return
        val endCalendar = parseDate(end) ?: startCalendar
        val cursor = startCalendar.clone() as Calendar
        while (!cursor.after(endCalendar)) {
            addDate(formatDate(cursor.get(Calendar.DAY_OF_MONTH), cursor.get(Calendar.MONTH) + 1, cursor.get(Calendar.YEAR)))
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun parseDate(value: String): Calendar? {
        return runCatching {
            val date = SimpleDateFormat("d/M/yyyy", Locale("es", "MX")).parse(value) ?: return null
            Calendar.getInstance().apply { time = date }
        }.getOrNull()
    }

    private inner class MeetingAdapter(private val items: List<AgendaItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): AgendaItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_meeting, parent, false)
            val meeting = getItem(position)

            row.findViewById<TextView>(R.id.txtMeetingTime).text =
                meeting.time.ifBlank { "--:--" }
            row.findViewById<TextView>(R.id.txtMeetingTitle).text =
                meeting.title.ifBlank { "Reunion sin titulo" }
            row.findViewById<TextView>(R.id.txtMeetingMeta).text =
                "${meeting.kind.label} | ${meeting.operation.ifBlank { "Tema general" }} | ${meeting.location.ifBlank { "Sin lugar" }}"
            row.findViewById<TextView>(R.id.txtMeetingPeople).text =
                "Asistentes: ${meeting.attendees.ifBlank { "Sin asignar" }}"
            row.findViewById<TextView>(R.id.txtMeetingAudit).text = meeting.audit
            val editButton = row.findViewById<MaterialButton>(R.id.btnEditMeeting)
            val deleteButton = row.findViewById<MaterialButton>(R.id.btnDeleteMeeting)
            editButton.visibility = if (meeting.kind == AgendaKind.MEETING) View.VISIBLE else View.GONE
            deleteButton.visibility = if (meeting.kind == AgendaKind.MEETING) View.VISIBLE else View.GONE
            editButton.setOnClickListener {
                val index = meetingList.indexOf(meeting)
                if (index >= 0) {
                    showMeetingDialog(meeting.toPlannerMeeting(), meetingIds[index])
                }
            }
            deleteButton.setOnClickListener {
                val index = meetingList.indexOf(meeting)
                if (index >= 0) {
                    confirmDeleteMeeting(meeting.toPlannerMeeting(), meetingIds[index])
                }
            }

            return row
        }
    }

    private fun QuerySnapshot.toAgendaMeetings(): List<AgendaItem> {
        return map { doc ->
            AgendaItem(
                id = doc.id,
                kind = AgendaKind.MEETING,
                title = doc.getString("title").orEmpty(),
                operation = doc.getString("operation").orEmpty(),
                location = doc.getString("location").orEmpty(),
                attendees = doc.getString("attendees").orEmpty(),
                time = doc.getString("time").orEmpty(),
                audit = AuditFormatter.fromDocument(doc)
            )
        }
    }

    private fun QuerySnapshot.toAgendaTasks(date: String): List<AgendaItem> {
        return filter { doc ->
            itemTouchesDate(
                date,
                doc.getString("date").orEmpty(),
                doc.getString("startDate").orEmpty(),
                doc.getString("endDate").orEmpty()
            )
        }.map { doc ->
            AgendaItem(
                id = doc.id,
                kind = AgendaKind.TASK,
                title = doc.getString("title").orEmpty(),
                operation = doc.getString("operation").orEmpty(),
                location = doc.getString("status").orEmpty().ifBlank { "Pendiente" },
                attendees = doc.getString("assignedTo").orEmpty(),
                time = doc.getString("startTime").orEmpty(),
                audit = AuditFormatter.fromDocument(doc)
            )
        }
    }

    private fun QuerySnapshot.toAgendaOperations(date: String): List<AgendaItem> {
        return filter { doc ->
            itemTouchesDate(
                date,
                doc.getString("date").orEmpty(),
                doc.getString("startDate").orEmpty(),
                doc.getString("endDate").orEmpty()
            )
        }.map { doc ->
            AgendaItem(
                id = doc.id,
                kind = AgendaKind.OPERATION,
                title = doc.getString("title").orEmpty(),
                operation = doc.getString("type").orEmpty(),
                location = doc.getString("location").orEmpty(),
                attendees = (doc.get("engineerNames") as? List<*>).orEmpty().joinToString(", "),
                time = doc.getString("startTime").orEmpty(),
                audit = AuditFormatter.fromDocument(doc)
            )
        }
    }

    private fun itemTouchesDate(targetDate: String, mainDate: String, startDate: String, endDate: String): Boolean {
        if (targetDate == mainDate || targetDate == startDate || targetDate == endDate) return true
        val target = parseDate(targetDate) ?: return false
        val start = parseDate(startDate.ifBlank { mainDate }) ?: return false
        val end = parseDate(endDate.ifBlank { startDate.ifBlank { mainDate } }) ?: start
        return !target.before(start) && !target.after(end)
    }

    private data class AgendaItem(
        val id: String,
        val kind: AgendaKind,
        val title: String,
        val operation: String,
        val location: String,
        val attendees: String,
        val time: String,
        val audit: String
    ) {
        fun toPlannerMeeting(): PlannerMeeting {
            return PlannerMeeting(title, operation, location, attendees, time, audit)
        }
    }

    private enum class AgendaKind(val label: String) {
        MEETING("Reunion"),
        TASK("Actividad"),
        OPERATION("Operacion")
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
