package com.example.halliplanner

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class EngineersFragment : Fragment(R.layout.fragment_engineers) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var engineers: ArrayList<Engineer>
    private lateinit var engineerIds: ArrayList<String>
    private lateinit var adapter: EngineerAdapter
    private lateinit var engineerList: ListView
    private lateinit var txtEngineerEmpty: TextView
    private lateinit var txtEngineerSummary: TextView
    private lateinit var txtDesignCount: TextView
    private lateinit var txtFieldCount: TextView
    private lateinit var btnAddEngineer: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        engineers = ArrayList()
        engineerIds = ArrayList()
        adapter = EngineerAdapter(engineers)

        engineerList = view.findViewById(R.id.engineerList)
        txtEngineerEmpty = view.findViewById(R.id.txtEngineerEmpty)
        txtEngineerSummary = view.findViewById(R.id.txtEngineerSummary)
        txtDesignCount = view.findViewById(R.id.txtDesignCount)
        txtFieldCount = view.findViewById(R.id.txtFieldCount)
        btnAddEngineer = view.findViewById(R.id.btnAddEngineer)

        engineerList.adapter = adapter
        engineerList.emptyView = txtEngineerEmpty

        btnAddEngineer.setOnClickListener { showEngineerDialog() }

        engineerList.setOnItemClickListener { _, _, position, _ ->
            showEngineerDialog(engineers[position])
        }

        engineerList.setOnItemLongClickListener { _, _, position, _ ->
            confirmDeleteEngineer(engineers[position])
            true
        }

        loadEngineers()
    }

    private fun loadEngineers() {
        db.collection("engineers")
            .get()
            .addOnSuccessListener { docs ->
                engineers.clear()
                engineerIds.clear()

                var design = 0
                var field = 0

                for (doc in docs) {
                    val engineer = Engineer(
                        id = doc.id,
                        name = doc.getString("name").orEmpty(),
                        type = doc.getString("type").orEmpty(),
                        specialty = doc.getString("specialty").orEmpty(),
                        email = doc.getString("email").orEmpty(),
                        phone = doc.getString("phone").orEmpty()
                    )
                    engineers.add(engineer)
                    engineerIds.add(doc.id)

                    when (engineer.type) {
                        "Diseno" -> design++
                        "Campo" -> field++
                    }
                }

                txtEngineerSummary.text = "${engineers.size} ingenieros disponibles para asignacion."
                txtDesignCount.text = "Diseno: $design"
                txtFieldCount.text = "Campo: $field"
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { showError("No se pudieron cargar ingenieros", it) }
    }

    private fun showEngineerDialog(engineer: Engineer? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_engineer, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.inputEngineerName)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.inputEngineerType)
        val specialtyInput = dialogView.findViewById<EditText>(R.id.inputEngineerSpecialty)
        val emailInput = dialogView.findViewById<EditText>(R.id.inputEngineerEmail)
        val phoneInput = dialogView.findViewById<EditText>(R.id.inputEngineerPhone)

        val types = arrayOf("Diseno", "Campo")
        typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            types
        )

        engineer?.let {
            nameInput.setText(it.name)
            specialtyInput.setText(it.specialty)
            emailInput.setText(it.email)
            phoneInput.setText(it.phone)
            typeSpinner.setSelection(types.indexOf(it.type).takeIf { index -> index >= 0 } ?: 0)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (engineer == null) "Registrar ingeniero" else "Editar ingeniero")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(context, "Agrega el nombre", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val data = hashMapOf(
                    "name" to name,
                    "type" to typeSpinner.selectedItem.toString(),
                    "specialty" to specialtyInput.text.toString().trim(),
                    "email" to emailInput.text.toString().trim(),
                    "phone" to phoneInput.text.toString().trim(),
                    "updatedBy" to auth.currentUser?.uid.orEmpty(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val request = if (engineer == null) {
                    data["createdBy"] = auth.currentUser?.uid.orEmpty()
                    data["createdAt"] = FieldValue.serverTimestamp()
                    db.collection("engineers").add(data)
                } else {
                    db.collection("engineers").document(engineer.id).set(data)
                }

                request
                    .addOnSuccessListener {
                        Toast.makeText(context, "Ingeniero guardado", Toast.LENGTH_SHORT).show()
                        loadEngineers()
                    }
                    .addOnFailureListener { showError("No se pudo guardar", it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmDeleteEngineer(engineer: Engineer) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar ingeniero")
            .setMessage("Quieres eliminar a ${engineer.name.ifBlank { "este ingeniero" }}?")
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("engineers").document(engineer.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Ingeniero eliminado", Toast.LENGTH_SHORT).show()
                        loadEngineers()
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

    private inner class EngineerAdapter(private val items: List<Engineer>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Engineer = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_engineer, parent, false)
            val engineer = getItem(position)
            val name = engineer.name.ifBlank { "Ingeniero sin nombre" }

            row.findViewById<TextView>(R.id.txtEngineerInitial).text =
                name.firstOrNull()?.uppercase() ?: "I"
            row.findViewById<TextView>(R.id.txtEngineerName).text = name
            row.findViewById<TextView>(R.id.txtEngineerMeta).text =
                "${engineer.type.ifBlank { "Sin tipo" }} | ${engineer.specialty.ifBlank { "Especialidad general" }}"
            row.findViewById<TextView>(R.id.txtEngineerContact).text =
                listOf(engineer.email, engineer.phone).filter { it.isNotBlank() }.joinToString(" | ")
                    .ifBlank { "Sin contacto registrado" }
            row.findViewById<MaterialButton>(R.id.btnDeleteEngineer).setOnClickListener {
                confirmDeleteEngineer(engineer)
            }
            return row
        }
    }

    data class Engineer(
        val id: String,
        val name: String,
        val type: String,
        val specialty: String,
        val email: String,
        val phone: String
    )

    companion object {
        private const val TAG = "EngineersFragment"
    }
}
