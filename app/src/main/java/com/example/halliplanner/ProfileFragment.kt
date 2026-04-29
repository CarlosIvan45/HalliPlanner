package com.example.halliplanner

import android.content.Intent
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var imgProfilePhoto: ImageView
    private lateinit var txtProfileInitial: TextView
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileRole: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var txtProfileDepartment: TextView
    private lateinit var txtProfilePhone: TextView
    private lateinit var txtProfileLocation: TextView
    private lateinit var txtProfileCompany: TextView
    private lateinit var txtProfileBio: TextView
    private lateinit var switchDarkMode: MaterialSwitch
    private lateinit var btnChangePhoto: MaterialButton
    private lateinit var btnEditProfile: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var currentName = ""
    private var currentRole = ""
    private var currentDepartment = ""
    private var currentPhone = ""
    private var currentLocation = ""
    private var currentCompany = ""
    private var currentBio = ""
    private var currentPhotoUri = ""
    private val photoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { saveProfilePhoto(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        imgProfilePhoto = view.findViewById(R.id.imgProfilePhoto)
        txtProfileInitial = view.findViewById(R.id.txtProfileInitial)
        txtProfileName = view.findViewById(R.id.txtProfileName)
        txtProfileRole = view.findViewById(R.id.txtProfileRole)
        txtProfileEmail = view.findViewById(R.id.txtProfileEmail)
        txtProfileDepartment = view.findViewById(R.id.txtProfileDepartment)
        txtProfilePhone = view.findViewById(R.id.txtProfilePhone)
        txtProfileLocation = view.findViewById(R.id.txtProfileLocation)
        txtProfileCompany = view.findViewById(R.id.txtProfileCompany)
        txtProfileBio = view.findViewById(R.id.txtProfileBio)
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        btnChangePhoto = view.findViewById(R.id.btnChangePhoto)
        btnEditProfile = view.findViewById(R.id.btnEditProfile)
        btnLogout = view.findViewById(R.id.btnLogout)

        loadProfile()
        switchDarkMode.isChecked = AppSettings.isDarkMode(requireContext())

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setDarkMode(requireContext(), isChecked)
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnChangePhoto.setOnClickListener {
            photoPicker.launch(arrayOf("image/*"))
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
                val phone = doc.getString("phone").orEmpty()
                val location = doc.getString("location").orEmpty()
                val company = doc.getString("company").orEmpty()
                val bio = doc.getString("bio").orEmpty()
                val photoUri = doc.getString("photoUri").orEmpty()

                currentName = name
                currentRole = role
                currentDepartment = department
                currentPhone = phone
                currentLocation = location
                currentCompany = company
                currentBio = bio
                currentPhotoUri = photoUri
                txtProfileName.text = name
                txtProfileRole.text = role
                txtProfileDepartment.text = department
                txtProfilePhone.text = phone.ifBlank { "Sin telefono" }
                txtProfileLocation.text = location.ifBlank { "Sin ubicacion" }
                txtProfileCompany.text = company.ifBlank { "Sin empresa" }
                txtProfileBio.text = bio.ifBlank { "Sin bio registrada" }
                txtProfileInitial.text = name.firstOrNull()?.uppercase() ?: "H"
                renderProfilePhoto(photoUri)
            }
            .addOnFailureListener {
                val fallback = user.email ?: "Usuario"
                currentName = fallback
                currentRole = "Usuario"
                currentDepartment = "Operaciones"
                txtProfileName.text = fallback
                txtProfileInitial.text = fallback.firstOrNull()?.uppercase() ?: "H"
                renderProfilePhoto("")
            }
    }

    private fun showEditProfileDialog() {
        val user = auth.currentUser ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.inputProfileName)
        val roleInput = dialogView.findViewById<EditText>(R.id.inputProfileRole)
        val departmentInput = dialogView.findViewById<EditText>(R.id.inputProfileDepartment)
        val phoneInput = dialogView.findViewById<EditText>(R.id.inputProfilePhone)
        val locationInput = dialogView.findViewById<EditText>(R.id.inputProfileLocation)
        val companyInput = dialogView.findViewById<EditText>(R.id.inputProfileCompany)
        val bioInput = dialogView.findViewById<EditText>(R.id.inputProfileBio)

        nameInput.setText(currentName)
        roleInput.setText(currentRole)
        departmentInput.setText(currentDepartment)
        phoneInput.setText(currentPhone)
        locationInput.setText(currentLocation)
        companyInput.setText(currentCompany)
        bioInput.setText(currentBio)

        AlertDialog.Builder(requireContext())
            .setIcon(R.drawable.ic_nav_profile)
            .setTitle("Personalizar perfil")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val name = nameInput.text.toString().trim().ifBlank { user.email ?: "Usuario" }
                val role = roleInput.text.toString().trim().ifBlank { "Usuario" }
                val department = departmentInput.text.toString().trim().ifBlank { "Operaciones" }
                val phone = phoneInput.text.toString().trim()
                val location = locationInput.text.toString().trim()
                val company = companyInput.text.toString().trim()
                val bio = bioInput.text.toString().trim()

                val data = hashMapOf(
                    "name" to name,
                    "email" to (user.email ?: ""),
                    "role" to role,
                    "department" to department,
                    "phone" to phone,
                    "location" to location,
                    "company" to company,
                    "bio" to bio,
                    "photoUri" to currentPhotoUri
                )

                db.collection("users").document(user.uid)
                    .set(data, SetOptions.merge())
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
            .also { DialogStyle.apply(it) }
    }

    private fun saveProfilePhoto(uri: Uri) {
        val user = auth.currentUser ?: return
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        currentPhotoUri = uri.toString()
        renderProfilePhoto(currentPhotoUri)

        db.collection("users").document(user.uid)
            .set(mapOf("photoUri" to currentPhotoUri), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(context, "Foto de perfil actualizada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "No se pudo guardar la foto: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun renderProfilePhoto(photoUri: String) {
        if (photoUri.isBlank()) {
            imgProfilePhoto.visibility = View.GONE
            txtProfileInitial.visibility = View.VISIBLE
            return
        }

        imgProfilePhoto.setImageURI(Uri.parse(photoUri))
        imgProfilePhoto.visibility = View.VISIBLE
        txtProfileInitial.visibility = View.GONE
    }
}
