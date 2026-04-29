package com.example.halliplanner

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.QuerySnapshot
import java.util.Calendar

class TasksFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var taskList: ArrayList<PlannerTask>
    private lateinit var taskIds: ArrayList<String>
    private lateinit var adapter: TaskAdapter
    private lateinit var taskListView: ListView
    private lateinit var btnAddTask: MaterialButton
    private lateinit var txtPending: TextView
    private lateinit var txtCompleted: TextView
    private lateinit var txtProgress: TextView
    private lateinit var txtTaskEmpty: TextView

    companion object {
        private const val TAG = "TasksFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_tasks, container, false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        taskListView = view.findViewById(R.id.taskList)
        btnAddTask = view.findViewById(R.id.btnAddTask)
        txtPending = view.findViewById(R.id.txtPending)
        txtCompleted = view.findViewById(R.id.txtCompleted)
        txtProgress = view.findViewById(R.id.txtProgress)
        txtTaskEmpty = view.findViewById(R.id.txtTaskEmpty)

        taskList = ArrayList()
        taskIds = ArrayList()
        adapter = TaskAdapter(taskList)
        taskListView.adapter = adapter
        taskListView.emptyView = txtTaskEmpty

        loadTasks()

        btnAddTask.setOnClickListener {
            showTaskDialog()
        }

        taskListView.setOnItemClickListener { _, _, position, _ ->
            showTaskDialog(taskList[position], taskIds[position])
        }

        taskListView.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteTask(taskList[position], taskIds[position])
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
                    taskList.add(
                        PlannerTask(
                            title = document.getString("title").orEmpty(),
                            description = document.getString("description").orEmpty(),
                            assignedTo = document.getString("assignedTo").orEmpty(),
                            date = document.getString("date").orEmpty(),
                            priority = document.getString("priority").orEmpty(),
                            status = document.getString("status").orEmpty(),
                            operation = document.getString("operation").orEmpty()
                        )
                    )
                    taskIds.add(document.id)
                }

                adapter.notifyDataSetChanged()
                updateCounters(documents)
            }
            .addOnFailureListener { error ->
                showFirestoreError("No se pudieron cargar las tareas", error)
            }
    }

    private fun showTaskDialog(task: PlannerTask? = null, taskId: String? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_task, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.inputTitle)
        val operationInput = dialogView.findViewById<EditText>(R.id.inputOperation)
        val descInput = dialogView.findViewById<EditText>(R.id.inputDescription)
        val assignedInput = dialogView.findViewById<EditText>(R.id.inputAssigned)
        val dateInput = dialogView.findViewById<EditText>(R.id.inputDate)
        val prioritySpinner = dialogView.findViewById<Spinner>(R.id.inputPriority)
        val statusSpinner = dialogView.findViewById<Spinner>(R.id.inputStatus)
        val calendar = Calendar.getInstance()

        dateInput.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    dateInput.setText(formatDate(day, month + 1, year))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val priorities = arrayOf("Alta", "Media", "Baja")
        val status = arrayOf("Pendiente", "En progreso", "Completada")

        prioritySpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorities)

        statusSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, status)

        task?.let {
            titleInput.setText(it.title)
            operationInput.setText(it.operation)
            descInput.setText(it.description)
            assignedInput.setText(it.assignedTo)
            dateInput.setText(it.date)
            prioritySpinner.setSelection(priorities.indexOf(it.priority).takeIf { index -> index >= 0 } ?: 1)
            statusSpinner.setSelection(status.indexOf(it.status).takeIf { index -> index >= 0 } ?: 0)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (task == null) "Nueva actividad" else "Editar actividad")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descInput.text.toString().trim()
                val assigned = assignedInput.text.toString().trim()
                val date = dateInput.text.toString().trim()
                val operation = operationInput.text.toString().trim()
                val priority = prioritySpinner.selectedItem.toString()
                val taskStatus = statusSpinner.selectedItem.toString()

                if (title.isBlank()) {
                    Toast.makeText(context, "Agrega un titulo", Toast.LENGTH_SHORT).show()
                } else {
                    saveTask(taskId, title, description, assigned, date, priority, taskStatus, operation)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun saveTask(
        taskId: String?,
        title: String,
        description: String,
        assigned: String,
        date: String,
        priority: String,
        status: String,
        operation: String
    ) {
        val task = hashMapOf(
            "title" to title,
            "description" to description,
            "assignedTo" to assigned,
            "date" to date,
            "priority" to priority,
            "status" to status,
            "operation" to operation,
            "updatedBy" to auth.currentUser?.uid.orEmpty(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (taskId == null) {
            task["createdBy"] = auth.currentUser?.uid.orEmpty()
            task["createdByEmail"] = auth.currentUser?.email.orEmpty()
            task["createdAt"] = FieldValue.serverTimestamp()
            db.collection("tasks")
                .add(task)
                .addOnSuccessListener {
                    Toast.makeText(context, "Actividad guardada", Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
                .addOnFailureListener {
                    showFirestoreError("Error al guardar la actividad", it)
                }
        } else {
            db.collection("tasks").document(taskId)
                .set(task)
                .addOnSuccessListener {
                    Toast.makeText(context, "Actividad actualizada", Toast.LENGTH_SHORT).show()
                    loadTasks()
                }
                .addOnFailureListener {
                    showFirestoreError("Error al actualizar la actividad", it)
                }
        }
    }

    private fun confirmDeleteTask(task: PlannerTask, taskId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar actividad")
            .setMessage("Quieres eliminar ${task.title.ifBlank { "esta actividad" }}?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("tasks")
                    .document(taskId)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Actividad eliminada", Toast.LENGTH_SHORT).show()
                        loadTasks()
                    }
                    .addOnFailureListener {
                        showFirestoreError("Error al eliminar la actividad", it)
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun updateCounters(documents: QuerySnapshot) {
        var pending = 0
        var progress = 0
        var completed = 0

        for (doc in documents) {
            when (doc.getString("status")) {
                "Pendiente" -> pending++
                "En progreso" -> progress++
                "Completada" -> completed++
            }
        }

        txtPending.text = pending.toString()
        txtProgress.text = progress.toString()
        txtCompleted.text = completed.toString()
    }

    private fun formatDate(day: Int, month: Int, year: Int): String {
        return "$day/$month/$year"
    }

    private fun showFirestoreError(message: String, error: Exception) {
        Log.e(TAG, message, error)
        val detail = error.localizedMessage ?: error.javaClass.simpleName
        Toast.makeText(context, "$message: $detail", Toast.LENGTH_LONG).show()
    }

    private inner class TaskAdapter(private val items: List<PlannerTask>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): PlannerTask = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_task, parent, false)
            val task = getItem(position)

            row.findViewById<TextView>(R.id.txtTaskTitle).text = task.title.ifBlank { "Sin titulo" }
            row.findViewById<TextView>(R.id.txtTaskDescription).text =
                task.description.ifBlank { "Sin descripcion registrada" }
            row.findViewById<TextView>(R.id.txtTaskPriority).text = task.priority.ifBlank { "Media" }
            row.findViewById<TextView>(R.id.txtTaskMeta).text =
                "${task.status.ifBlank { "Pendiente" }} | ${task.date.ifBlank { "Sin fecha" }} | ${task.operation.ifBlank { "Operacion general" }}"
            row.findViewById<TextView>(R.id.txtTaskPeople).text =
                "Responsables: ${task.assignedTo.ifBlank { "Sin asignar" }}"
            row.findViewById<MaterialButton>(R.id.btnDeleteTask).setOnClickListener {
                val index = taskList.indexOf(task)
                if (index >= 0) {
                    confirmDeleteTask(task, taskIds[index])
                }
            }

            return row
        }
    }

    private data class PlannerTask(
        val title: String,
        val description: String,
        val assignedTo: String,
        val date: String,
        val priority: String,
        val status: String,
        val operation: String
    )
}
