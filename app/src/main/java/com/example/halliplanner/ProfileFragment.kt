package com.example.halliplanner

import android.content.Intent
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var txtProfileInitial: TextView
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileRole: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var txtProfileDepartment: TextView
    private lateinit var btnEditProfile: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var currentName = ""
    private var currentRole = ""
    private var currentDepartment = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        txtProfileInitial = view.findViewById(R.id.txtProfileInitial)
        txtProfileName = view.findViewById(R.id.txtProfileName)
        txtProfileRole = view.findViewById(R.id.txtProfileRole)
        txtProfileEmail = view.findViewById(R.id.txtProfileEmail)
        txtProfileDepartment = view.findViewById(R.id.txtProfileDepartment)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        btnLogout = view.findViewById(R.id.btnLogout)

        loadProfile()

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(requireContext(), AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun loadProfile() {
        val user = auth.currentUser ?: return
        txtProfileEmail.text = user.email ?: "Sin correo"

        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name").orEmpty().ifBlank { user.email ?: "Usuario" }
                val role = doc.getString("role").orEmpty().ifBlank { "Usuario" }
                val department = doc.getString("department").orEmpty().ifBlank { "Operaciones" }

                currentName = name
                currentRole = role
                currentDepartment = department
                txtProfileName.text = name
                txtProfileRole.text = role
                txtProfileDepartment.text = department
                txtProfileInitial.text = name.firstOrNull()?.uppercase() ?: "H"
            }
            .addOnFailureListener {
                val fallback = user.email ?: "Usuario"
                currentName = fallback
                currentRole = "Usuario"
                currentDepartment = "Operaciones"
                txtProfileName.text = fallback
                txtProfileInitial.text = fallback.firstOrNull()?.uppercase() ?: "H"
            }
    }

    private fun showEditProfileDialog() {
        val user = auth.currentUser ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.inputProfileName)
        val roleInput = dialogView.findViewById<EditText>(R.id.inputProfileRole)
        val departmentInput = dialogView.findViewById<EditText>(R.id.inputProfileDepartment)

        nameInput.setText(currentName)
        roleInput.setText(currentRole)
        departmentInput.setText(currentDepartment)

        AlertDialog.Builder(requireContext())
            .setTitle("Personalizar perfil")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { user.email ?: "Usuario" }
                val role = roleInput.text.toString().trim().ifBlank { "Usuario" }
                val department = departmentInput.text.toString().trim().ifBlank { "Operaciones" }

                val data = hashMapOf(
                    "name" to name,
                    "email" to (user.email ?: ""),
                    "role" to role,
                    "department" to department
                )

                db.collection("users").document(user.uid)
                    .set(data)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                        loadProfile()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "No se pudo actualizar: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
