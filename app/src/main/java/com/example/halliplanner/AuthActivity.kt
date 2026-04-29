package com.example.halliplanner

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var txtAuthTitle: TextView
    private lateinit var txtAuthSubtitle: TextView
    private lateinit var inputName: EditText
    private lateinit var inputEmail: EditText
    private lateinit var inputPassword: EditText
    private lateinit var btnPrimaryAuth: MaterialButton
    private lateinit var btnSecondaryAuth: MaterialButton
    private lateinit var txtForgotPassword: TextView

    private var mode = AuthMode.LOGIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            openPlanner()
            return
        }

        setContentView(R.layout.activity_auth)

        txtAuthTitle = findViewById(R.id.txtAuthTitle)
        txtAuthSubtitle = findViewById(R.id.txtAuthSubtitle)
        inputName = findViewById(R.id.inputName)
        inputEmail = findViewById(R.id.inputEmail)
        inputPassword = findViewById(R.id.inputPassword)
        btnPrimaryAuth = findViewById(R.id.btnPrimaryAuth)
        btnSecondaryAuth = findViewById(R.id.btnSecondaryAuth)
        txtForgotPassword = findViewById(R.id.txtForgotPassword)

        renderMode()

        btnPrimaryAuth.setOnClickListener {
            when (mode) {
                AuthMode.LOGIN -> login()
                AuthMode.REGISTER -> register()
                AuthMode.FORGOT -> sendPasswordReset()
            }
        }

        btnSecondaryAuth.setOnClickListener {
            mode = when (mode) {
                AuthMode.LOGIN -> AuthMode.REGISTER
                AuthMode.REGISTER -> AuthMode.LOGIN
                AuthMode.FORGOT -> AuthMode.LOGIN
            }
            renderMode()
        }

        txtForgotPassword.setOnClickListener {
            mode = AuthMode.FORGOT
            renderMode()
        }
    }

    private fun login() {
        val email = inputEmail.text.toString().trim()
        val password = inputPassword.text.toString()

        if (!validateEmail(email) || password.isBlank()) {
            Toast.makeText(this, "Ingresa correo y contrasena", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                setLoading(false)
                openPlanner()
            }
            .addOnFailureListener {
                setLoading(false)
                showAuthError("No se pudo iniciar sesion", it)
            }
    }

    private fun register() {
        val name = inputName.text.toString().trim()
        val email = inputEmail.text.toString().trim()
        val password = inputPassword.text.toString()

        if (name.isBlank() || !validateEmail(email) || password.length < 6) {
            Toast.makeText(this, "Agrega nombre, correo valido y minimo 6 caracteres", Toast.LENGTH_LONG).show()
            return
        }

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid.orEmpty()
                val user = hashMapOf(
                    "name" to name,
                    "email" to email,
                    "role" to "Usuario",
                    "department" to "Operaciones"
                )

                db.collection("users").document(uid)
                    .set(user)
                    .addOnSuccessListener {
                        setLoading(false)
                        openPlanner()
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        Toast.makeText(
                            this,
                            "Cuenta creada, pero no se pudo guardar el perfil: ${it.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                        openPlanner()
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                showAuthError("No se pudo crear la cuenta", it)
            }
    }

    private fun sendPasswordReset() {
        val email = inputEmail.text.toString().trim()

        if (!validateEmail(email)) {
            Toast.makeText(this, "Ingresa tu correo", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Te enviamos un correo para recuperar tu contrasena", Toast.LENGTH_LONG).show()
                mode = AuthMode.LOGIN
                renderMode()
            }
            .addOnFailureListener {
                setLoading(false)
                showAuthError("No se pudo enviar el correo", it)
            }
    }

    private fun renderMode() {
        when (mode) {
            AuthMode.LOGIN -> {
                txtAuthTitle.text = "Iniciar sesion"
                txtAuthSubtitle.text = "Inicia sesion para organizar tareas, operaciones y reuniones."
                inputName.visibility = View.GONE
                inputPassword.visibility = View.VISIBLE
                btnPrimaryAuth.text = "Entrar"
                btnSecondaryAuth.text = "Crear cuenta"
                txtForgotPassword.visibility = View.VISIBLE
            }
            AuthMode.REGISTER -> {
                txtAuthTitle.text = "Crear cuenta"
                txtAuthSubtitle.text = "Registra tu acceso para trabajar con la base de datos."
                inputName.visibility = View.VISIBLE
                inputPassword.visibility = View.VISIBLE
                btnPrimaryAuth.text = "Registrar"
                btnSecondaryAuth.text = "Ya tengo cuenta"
                txtForgotPassword.visibility = View.GONE
            }
            AuthMode.FORGOT -> {
                txtAuthTitle.text = "Recuperar contrasena"
                txtAuthSubtitle.text = "Escribe tu correo y te enviaremos instrucciones."
                inputName.visibility = View.GONE
                inputPassword.visibility = View.GONE
                btnPrimaryAuth.text = "Enviar correo"
                btnSecondaryAuth.text = "Volver al login"
                txtForgotPassword.visibility = View.GONE
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnPrimaryAuth.isEnabled = !isLoading
        btnSecondaryAuth.isEnabled = !isLoading
        txtForgotPassword.isEnabled = !isLoading
    }

    private fun validateEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun openPlanner() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showAuthError(message: String, error: Exception) {
        Log.e(TAG, message, error)
        val detail = when (error) {
            is FirebaseAuthException -> authMessageForCode(error.errorCode, error.localizedMessage)
            else -> error.localizedMessage ?: error.javaClass.simpleName
        }
        Toast.makeText(this, "$message: $detail", Toast.LENGTH_LONG).show()
    }

    private fun authMessageForCode(code: String, fallback: String?): String {
        return when (code) {
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Activa Email/Password en Firebase Authentication. Codigo: $code"
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "Ese correo ya esta registrado. Codigo: $code"
            "ERROR_INVALID_EMAIL" ->
                "El correo no tiene un formato valido. Codigo: $code"
            "ERROR_WEAK_PASSWORD" ->
                "La contrasena debe tener al menos 6 caracteres. Codigo: $code"
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "No hay conexion con Firebase. Codigo: $code"
            "ERROR_INTERNAL_ERROR" ->
                "Firebase Auth no esta listo o falta activar el proveedor Email/Password. Codigo: $code"
            else ->
                "${fallback ?: "Error de autenticacion"} Codigo: $code"
        }
    }

    private enum class AuthMode {
        LOGIN,
        REGISTER,
        FORGOT
    }

    companion object {
        private const val TAG = "AuthActivity"
    }
}
