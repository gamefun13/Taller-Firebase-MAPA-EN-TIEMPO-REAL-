package com.example.userlocatorapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLogin) "Iniciar sesión" else "Registrarse",
            style = MaterialTheme.typography.headlineMedium
        )

        if (!isLogin) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("Número de identificación") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo electrónico") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else {
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = ""
                    if (isLogin) {
                        login(email, password, context,
                            onSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onError = {
                                isLoading = false
                                errorMessage = it
                            }
                        )
                    } else {
                        register(email, password, name, idNumber, phone,
                            onSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onError = {
                                isLoading = false
                                errorMessage = it
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(text = if (isLogin) "Entrar" else "Registrarme")
            }
        }

        TextButton(onClick = { isLogin = !isLogin }) {
            Text(if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión")
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@SuppressLint("MissingPermission")
private fun login(email: String, password: String, context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    auth.signInWithEmailAndPassword(email, password)
        .addOnSuccessListener { authResult ->
            val userId = authResult.user?.uid ?: return@addOnSuccessListener
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    val lat = location?.latitude ?: 0.0
                    val lon = location?.longitude ?: 0.0
                    val updates = mapOf(
                        "lat" to lat,
                        "lon" to lon,
                        "conectado" to true
                    )
                    db.collection("usuarios").document(userId).update(updates)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { e -> onError("Error al actualizar ubicación: ${e.message}") }
                }
                .addOnFailureListener { e -> onError("Error al obtener ubicación: ${e.message}") }
        }
        .addOnFailureListener { e -> onError(e.localizedMessage ?: "Error al iniciar sesión") }
}

private fun register(
    email: String,
    password: String,
    name: String,
    id: String,
    phone: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener {
            val userId = it.user?.uid ?: return@addOnSuccessListener
            val db = FirebaseFirestore.getInstance()
            val data = mapOf(
                "nombre" to name,
                "id" to id,
                "telefono" to phone,
                "correo" to email,
                "lat" to 0.0,
                "lon" to 0.0,
                "conectado" to false
            )
            db.collection("usuarios").document(userId).set(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { e -> onError("Error al guardar datos: ${e.message}") }
        }
        .addOnFailureListener { e -> onError("Error de registro: ${e.message}") }
}