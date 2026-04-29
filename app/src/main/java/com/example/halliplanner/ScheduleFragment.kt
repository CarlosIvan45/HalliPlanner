package com.example.halliplanner

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CalendarView
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var meetingListView: ListView
    private lateinit var btnAddMeeting: MaterialButton
    private lateinit var txtSelectedDate: TextView
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
        txtSelectedDate = view.findViewById(R.id.txtSelectedDate)
        txtMeetingEmpty = view.findViewById(R.id.txtMeetingEmpty)

        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        meetingList = ArrayList()
        meetingIds = ArrayList()
        adapter = MeetingAdapter(meetingList)
        meetingListView.adapter = adapter
        meetingListView.emptyView = txtMeetingEmpty

        val calendar = Calendar.getInstance()
        selectedDate = formatDate(
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR)
        )
        updateSelectedDateLabel()
        loadMeetings(selectedDate)

        calendarView.setOnDateChangeListener { _, year, month, day ->
            selectedDate = formatDate(day, month + 1, year)
            updateSelectedDateLabel()
            loadMeetings(selectedDate)
        }

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
            "updatedAt" to FieldValue.serverTimestamp()
        )

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
                            time = doc.getString("time").orEmpty()
                        )
                    )
                    meetingIds.add(doc.id)
                }

                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                showFirestoreError("No se pudieron cargar las reuniones", error)
            }
    }

    private fun confirmDeleteMeeting(meeting: PlannerMeeting, meetingId: String) {
        AlertDialog.Builder(requireContext())
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
    }

    private fun updateSelectedDateLabel() {
        txtSelectedDate.text = "Agenda para $selectedDate"
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        return "$day/$month/$year"
    }

    private fun showFirestoreError(message: String, error: Exception) {
        Log.e(TAG, message, error)
        val detail = error.localizedMessage ?: error.javaClass.simpleName
        Toast.makeText(context, "$message: $detail", Toast.LENGTH_LONG).show()
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
        val time: String
    )
}
