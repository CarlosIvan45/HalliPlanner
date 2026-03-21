package com.example.halliplanner

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var db: FirebaseFirestore
    private lateinit var pendingTasksText: TextView

    private lateinit var txtMeetingsHome: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()

        pendingTasksText = view.findViewById(R.id.txtHomeTasks)

        txtMeetingsHome = view.findViewById(R.id.txtMeetingsHome)

        loadPendingTasks()
        loadTodayMeetings()
    }

    private fun loadPendingTasks() {

        db.collection("tasks")
            .whereEqualTo("status", "Pendiente")
            .get()
            .addOnSuccessListener { documents ->

                pendingTasksText.text =
                    "${documents.size()} tareas por completar hoy"
            }
    }

    private fun loadTodayMeetings(){

        val calendar = Calendar.getInstance()
        val today = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH)+1}/${calendar.get(Calendar.YEAR)}"

        db.collection("meetings")
            .whereEqualTo("date",today)
            .get()
            .addOnSuccessListener { documents ->

                txtMeetingsHome.text =
                    "${documents.size()} reuniones hoy"
            }
    }
}