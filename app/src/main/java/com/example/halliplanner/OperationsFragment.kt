package com.example.halliplanner

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class OperationsFragment : Fragment(R.layout.fragment_operations) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var operations: ArrayList<Operation>
    private lateinit var operationIds: ArrayList<String>
    private lateinit var engineers: ArrayList<EngineersFragment.Engineer>
    private lateinit var adapter: OperationAdapter
    private lateinit var operationList: ListView
    private lateinit var txtOperationEmpty: TextView
    private lateinit var txtOperationSummary: TextView
    private lateinit var txtOperationOpenCount: TextView
    private lateinit var txtOperationEngineerCount: TextView
    private lateinit var btnAddOperation: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        operations = ArrayList()
        operationIds = ArrayList()
        engineers = ArrayList()
        adapter = OperationAdapter(operations)

        operationList = view.findViewById(R.id.operationList)
        txtOperationEmpty = view.findViewById(R.id.txtOperationEmpty)
        txtOperationSummary = view.findViewById(R.id.txtOperationSummary)
        txtOperationOpenCount = view.findViewById(R.id.txtOperationOpenCount)
        txtOperationEngineerCount = view.findViewById(R.id.txtOperationEngineerCount)
        btnAddOperation = view.findViewById(R.id.btnAddOperation)

        operationList.adapter = adapter
        operationList.emptyView = txtOperationEmpty

        btnAddOperation.setOnClickListener { showOperationDialog() }

        operationList.setOnItemClickListener { _, _, position, _ ->
            showOperationDialog(operations[position])
        }

        operationList.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteOperation(operations[position])
            true
        }

        loadEngineers()
        loadOperations()
    }

    private fun loadEngineers() {
        db.collection("engineers")
            .get()
            .addOnSuccessListener { docs ->
                engineers.clear()
                for (doc in docs) {
                    engineers.add(
                        EngineersFragment.Engineer(
                            id = doc.id,
                            name = doc.getString("name").orEmpty(),
                            type = doc.getString("type").orEmpty(),
                            specialty = doc.getString("specialty").orEmpty(),
                            email = doc.getString("email").orEmpty(),
                            phone = doc.getString("phone").orEmpty()
                        )
                    )
                }
            }
            .addOnFailureListener { showError("No se pudieron cargar ingenieros", it) }
    }

    private fun loadOperations() {
        db.collection("operations")
            .get()
            .addOnSuccessListener { docs ->
                operations.clear()
                operationIds.clear()

                var active = 0
                var assigned = 0

                for (doc in docs) {
                    val engineerNames = doc.get("engineerNames") as? List<*> ?: emptyList<Any>()
                    val engineerIds = doc.get("engineerIds") as? List<*> ?: emptyList<Any>()
                    val operation = Operation(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        type = doc.getString("type").orEmpty(),
                        location = doc.getString("location").orEmpty(),
                        date = doc.getString("date").orEmpty(),
                        status = doc.getString("status").orEmpty(),
                        description = doc.getString("description").orEmpty(),
                        revenue = doc.getDouble("revenue") ?: 0.0,
                        engineerIds = engineerIds.map { it.toString() },
                        engineerNames = engineerNames.map { it.toString() }
                    )
                    operations.add(operation)
                    operationIds.add(doc.id)

                    if (operation.status != "Completada") active++
                    assigned += operation.engineerNames.size
                }

                txtOperationSummary.text = "${operations.size} operaciones registradas."
                txtOperationOpenCount.text = "Activas: $active"
                txtOperationEngineerCount.text = "Asignados: $assigned"
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { showError("No se pudieron cargar operaciones", it) }
    }

    private fun showOperationDialog(operation: Operation? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_operation, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.inputOperationTitle)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.inputOperationType)
        val locationInput = dialogView.findViewById<EditText>(R.id.inputOperationLocation)
        val revenueInput = dialogView.findViewById<EditText>(R.id.inputOperationRevenue)
        val dateInput = dialogView.findViewById<EditText>(R.id.inputOperationDate)
        val statusSpinner = dialogView.findViewById<Spinner>(R.id.inputOperationStatus)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.inputOperationDescription)
        val btnSelectEngineers = dialogView.findViewById<MaterialButton>(R.id.btnSelectEngineers)
        val txtSelectedEngineers = dialogView.findViewById<TextView>(R.id.txtSelectedEngineers)
        val selectedEngineerIndexes = BooleanArray(engineers.size)

        val operationTypes = arrayOf("Pozo", "Entrenamiento", "Reunion interna", "Mantenimiento", "Soporte tecnico")
        val operationStatuses = arrayOf("Planeada", "En progreso", "Completada", "Pausada")
        typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            operationTypes
        )
        statusSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            operationStatuses
        )

        operation?.let {
            titleInput.setText(it.title)
            locationInput.setText(it.location)
            revenueInput.setText(if (it.revenue > 0.0) it.revenue.toString() else "")
            dateInput.setText(it.date)
            descriptionInput.setText(it.description)
            typeSpinner.setSelection(operationTypes.indexOf(it.type).takeIf { index -> index >= 0 } ?: 0)
            statusSpinner.setSelection(operationStatuses.indexOf(it.status).takeIf { index -> index >= 0 } ?: 0)
            engineers.forEachIndexed { index, engineer ->
                selectedEngineerIndexes[index] =
                    it.engineerIds.contains(engineer.id) || it.engineerNames.contains(engineer.name)
            }
            txtSelectedEngineers.text = engineers
                .filterIndexed { index, _ -> selectedEngineerIndexes[index] }
                .joinToString(", ") { engineer -> engineer.name }
                .ifBlank { "Sin ingenieros asignados" }
        }

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                revenueInput.visibility = if (operationTypes[position] == "Pozo") View.VISIBLE else View.GONE
                if (operationTypes[position] != "Pozo") {
                    revenueInput.setText("")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        dateInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    dateInput.setText("$day/${month + 1}/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnSelectEngineers.setOnClickListener {
            if (engineers.isEmpty()) {
                Toast.makeText(context, "Primero registra ingenieros", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val names = engineers.map { "${it.name} (${it.type})" }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("Asignar ingenieros")
                .setMultiChoiceItems(names, selectedEngineerIndexes) { _, which, isChecked ->
                    selectedEngineerIndexes[which] = isChecked
                }
                .setPositiveButton("Aplicar") { _, _ ->
                    val selectedNames = engineers
                        .filterIndexed { index, _ -> selectedEngineerIndexes[index] }
                        .map { it.name }
                    txtSelectedEngineers.text = selectedNames.joinToString(", ").ifBlank {
                        "Sin ingenieros asignados"
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (operation == null) "Crear operacion" else "Editar operacion")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isBlank()) {
                    Toast.makeText(context, "Agrega el nombre de la operacion", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val selectedEngineers = engineers.filterIndexed { index, _ -> selectedEngineerIndexes[index] }
                val selectedType = typeSpinner.selectedItem.toString()
                val revenue = if (selectedType == "Pozo") {
                    revenueInput.text.toString().trim().toDoubleOrNull() ?: 0.0
                } else {
                    0.0
                }
                val data = hashMapOf(
                    "title" to title,
                    "type" to selectedType,
                    "revenue" to revenue,
                    "location" to locationInput.text.toString().trim(),
                    "date" to dateInput.text.toString().trim(),
                    "status" to statusSpinner.selectedItem.toString(),
                    "description" to descriptionInput.text.toString().trim(),
                    "engineerIds" to selectedEngineers.map { it.id },
                    "engineerNames" to selectedEngineers.map { it.name },
                    "updatedBy" to auth.currentUser?.uid.orEmpty(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (operation == null) {
                    data["createdBy"] = auth.currentUser?.uid.orEmpty()
                    data["createdAt"] = FieldValue.serverTimestamp()
                    db.collection("operations")
                        .add(data)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Operacion creada", Toast.LENGTH_SHORT).show()
                            loadOperations()
                        }
                        .addOnFailureListener { showError("No se pudo guardar", it) }
                } else {
                    db.collection("operations").document(operation.id)
                        .set(data)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Operacion actualizada", Toast.LENGTH_SHORT).show()
                            loadOperations()
                        }
                        .addOnFailureListener { showError("No se pudo actualizar", it) }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteOperation(operation: Operation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar operacion")
            .setMessage("Quieres eliminar ${operation.title.ifBlank { "esta operacion" }}?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("operations").document(operation.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Operacion eliminada", Toast.LENGTH_SHORT).show()
                        loadOperations()
                    }
                    .addOnFailureListener { showError("No se pudo eliminar", it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showError(message: String, error: Exception) {
        Log.e(TAG, message, error)
        Toast.makeText(context, "$message: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
    }

    private inner class OperationAdapter(private val items: List<Operation>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Operation = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_operation, parent, false)
            val operation = getItem(position)

            row.findViewById<TextView>(R.id.txtOperationTitle).text =
                operation.title.ifBlank { "Operacion sin nombre" }
            row.findViewById<TextView>(R.id.txtOperationType).text =
                operation.type.ifBlank { "General" }
            row.findViewById<TextView>(R.id.txtOperationMeta).text =
                "${operation.status.ifBlank { "Planeada" }} | ${operation.date.ifBlank { "Sin fecha" }} | ${operation.location.ifBlank { "Sin ubicacion" }}"
            row.findViewById<TextView>(R.id.txtOperationDescription).text =
                operation.description.ifBlank { "Sin descripcion registrada" }
            val revenueText = row.findViewById<TextView>(R.id.txtOperationRevenue)
            if (operation.type == "Pozo") {
                revenueText.visibility = View.VISIBLE
                revenueText.text = "Revenue: $${String.format("%,.2f", operation.revenue)}"
            } else {
                revenueText.visibility = View.GONE
            }
            row.findViewById<TextView>(R.id.txtOperationEngineers).text =
                "Ingenieros: ${operation.engineerNames.joinToString(", ").ifBlank { "Sin asignar" }}"
            row.findViewById<MaterialButton>(R.id.btnDeleteOperation).setOnClickListener {
                confirmDeleteOperation(operation)
            }

            return row
        }
    }

    private data class Operation(
        val id: String,
        val title: String,
        val type: String,
        val location: String,
        val date: String,
        val status: String,
        val description: String,
        val revenue: Double,
        val engineerIds: List<String>,
        val engineerNames: List<String>
    )

    companion object {
        private const val TAG = "OperationsFragment"
    }
}
