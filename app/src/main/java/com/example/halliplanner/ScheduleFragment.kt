package com.example.halliplanner

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private lateinit var db: FirebaseFirestore
    private lateinit var meetingListView: ListView
    private lateinit var btnAddMeeting: MaterialButton

    private lateinit var meetingList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>

    private var selectedDate = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        meetingListView = view.findViewById(R.id.meetingList)
        btnAddMeeting = view.findViewById(R.id.btnAddMeeting)

        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)

        meetingList = ArrayList()

        adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            meetingList
        )

        meetingListView.adapter = adapter

        val calendar = Calendar.getInstance()
        selectedDate = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH)+1}/${calendar.get(Calendar.YEAR)}"

        loadMeetings(selectedDate)

        calendarView.setOnDateChangeListener { _, year, month, day ->

            selectedDate = "$day/${month+1}/$year"
            loadMeetings(selectedDate)

        }

        btnAddMeeting.setOnClickListener {
            showAddMeetingDialog()
        }
    }

    private fun showAddMeetingDialog() {

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_meeting, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.inputMeetingTitle)
        val locationInput = dialogView.findViewById<EditText>(R.id.inputLocation)
        val timeInput = dialogView.findViewById<EditText>(R.id.inputTime)

        val calendar = Calendar.getInstance()

        timeInput.setOnClickListener {

            val timePicker = TimePickerDialog(
                requireContext(),
                { _, hour, minute ->

                    val time = "$hour:$minute"
                    timeInput.setText(time)

                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            )

            timePicker.show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Nueva reunión")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->

                val title = titleInput.text.toString()
                val location = locationInput.text.toString()
                val time = timeInput.text.toString()

                addMeeting(title, location, time)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addMeeting(title: String, location: String, time: String) {

        val meeting = hashMapOf(

            "title" to title,
            "location" to location,
            "time" to time,
            "date" to selectedDate
        )

        db.collection("meetings")
            .add(meeting)
            .addOnSuccessListener {

                Toast.makeText(context,"Evento guardado",Toast.LENGTH_SHORT).show()
                loadMeetings(selectedDate)

            }
    }

    private fun loadMeetings(date: String) {

        db.collection("meetings")
            .whereEqualTo("date",date)
            .get()
            .addOnSuccessListener { documents ->

                meetingList.clear()

                for(doc in documents){

                    val title = doc.getString("title")
                    val time = doc.getString("time")
                    val location = doc.getString("location")

                    meetingList.add("$time - $title\n$location")
                }

                adapter.notifyDataSetChanged()
            }
    }
}