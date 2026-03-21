package com.example.halliplanner

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.util.Calendar

class TasksFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var taskList: ArrayList<String>
    private lateinit var taskIds: ArrayList<String>

    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var taskListView: ListView
    private lateinit var btnAddTask: MaterialButton

    private lateinit var txtPending: TextView
    private lateinit var txtCompleted: TextView
    private lateinit var txtProgress: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_tasks, container, false)

        db = FirebaseFirestore.getInstance()

        taskListView = view.findViewById(R.id.taskList)
        btnAddTask = view.findViewById(R.id.btnAddTask)
        txtPending = view.findViewById(R.id.txtPending)
        txtCompleted = view.findViewById(R.id.txtCompleted)
        txtProgress = view.findViewById(R.id.txtProgress)

        taskList = ArrayList()
        taskIds = ArrayList()

        adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            taskList
        )

        taskListView.adapter = adapter

        loadTasks()

        btnAddTask.setOnClickListener {
            showAddTaskDialog()
        }

        // BORRAR TAREA AL MANTENER PRESIONADO
        taskListView.setOnItemLongClickListener { _, _, position, _ ->

            val taskId = taskIds[position]

            db.collection("tasks")
                .document(taskId)
                .delete()
                .addOnSuccessListener {

                    Toast.makeText(context, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                    loadTasks()

                }

            true
        }

        return view
    }

    private fun loadTasks() {

        db.collection("tasks")
            .get()
            .addOnSuccessListener { documents ->

                taskList.clear()
                taskIds.clear()

                for (document in documents) {

                    val title = document.getString("title")
                    val description = document.getString("description")

                    taskList.add("$title - $description")
                    taskIds.add(document.id)
                }

                adapter.notifyDataSetChanged()
                updateCounters(documents)
            }
    }

    private fun showAddTaskDialog() {

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.inputTitle)
        val descInput = dialogView.findViewById<EditText>(R.id.inputDescription)
        val assignedInput = dialogView.findViewById<EditText>(R.id.inputAssigned)
        val dateInput = dialogView.findViewById<EditText>(R.id.inputDate)

        val calendar = Calendar.getInstance()

        dateInput.setOnClickListener {

            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, day ->

                    val selectedDate = "$day/${month + 1}/$year"
                    dateInput.setText(selectedDate)

                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            datePicker.show()
        }

        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.inputPriority)
        val statusSpinner = dialogView.findViewById<Spinner>(R.id.inputStatus)

        val priorities = arrayOf("Alta", "Media", "Baja")
        val status = arrayOf("Pendiente", "En progreso", "Completada")

        prioritySpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorities)

        statusSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, status)

        AlertDialog.Builder(requireContext())
            .setTitle("Nueva tarea")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->

                val title = titleInput.text.toString()
                val description = descInput.text.toString()
                val assigned = assignedInput.text.toString()
                val date = dateInput.text.toString()
                val priority = prioritySpinner.selectedItem.toString()
                val taskStatus = statusSpinner.selectedItem.toString()

                addTask(title, description, assigned, date, priority, taskStatus)

            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addTask(
        title: String,
        description: String,
        assigned: String,
        date: String,
        priority: String,
        status: String
    ) {

        val task = hashMapOf(
            "title" to title,
            "description" to description,
            "assignedTo" to assigned,
            "date" to date,
            "priority" to priority,
            "status" to status
        )

        db.collection("tasks")
            .add(task)
            .addOnSuccessListener {

                Toast.makeText(context, "Tarea guardada", Toast.LENGTH_SHORT).show()
                loadTasks()

            }
            .addOnFailureListener {

                Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()

            }
    }

    private fun updateCounters(documents: QuerySnapshot) {

        var pending = 0
        var progress = 0
        var completed = 0

        for (doc in documents) {

            when(doc.getString("status")){

                "Pendiente" -> pending++
                "En progreso" -> progress++
                "Completada" -> completed++
            }
        }

        txtPending.text = pending.toString()
        txtProgress.text = progress.toString()
        txtCompleted.text = completed.toString()
    }
}