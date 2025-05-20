package com.example.userlocatorapp

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBackToMenu: () -> Unit,
    onLogout: () -> Unit
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val storage = FirebaseStorage.getInstance().reference

    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val imageRef = storage.child("fotos/${currentUser?.uid}.jpg")
            imageRef.putFile(uri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: Exception("Error al subir imagen")
                    imageRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    db.collection("usuarios").document(currentUser?.uid ?: return@addOnSuccessListener)
                        .update("foto", downloadUri.toString())
                    successMessage = "Foto subida exitosamente"
                }
                .addOnFailureListener {
                    errorMessage = "Error subiendo la foto: ${it.message}"
                }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val uid = currentUser?.uid ?: return@let
            val imageRef = storage.child("fotos/$uid.jpg")

            val baos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val data = baos.toByteArray()

            imageRef.putBytes(data)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: Exception("Error al subir imagen")
                    imageRef.downloadUrl
                }
                .addOnSuccessListener { downloadUri ->
                    db.collection("usuarios").document(uid)
                        .update("foto", downloadUri.toString())
                    successMessage = "Foto tomada y subida exitosamente"
                }
                .addOnFailureListener {
                    errorMessage = "Error subiendo la foto tomada: ${it.message}"
                }
        }
    }

    LaunchedEffect(Unit) {
        currentUser?.uid?.let { uid ->
            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener {
                    name = it.getString("nombre") ?: ""
                    id = it.getString("id") ?: ""
                    phone = it.getString("telefono") ?: ""
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBackToMenu) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        onLogout()
                    }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = id,
                onValueChange = { id = it },
                label = { Text("Número de Identificación") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Teléfono") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Nueva contraseña") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            Button(
                onClick = {
                    currentUser?.uid?.let { uid ->
                        db.collection("usuarios").document(uid).update(
                            mapOf("nombre" to name, "id" to id, "telefono" to phone)
                        )
                    }
                    if (password.isNotEmpty()) {
                        currentUser?.updatePassword(password)
                            ?.addOnSuccessListener {
                                successMessage = "Perfil actualizado"
                            }
                            ?.addOnFailureListener {
                                errorMessage = "Error: ${it.message}"
                            }
                    } else {
                        successMessage = "Perfil actualizado"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Guardar cambios")
            }

            Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Subir Foto de Perfil")
            }

            CameraPermissionButton(cameraLauncher)

            if (successMessage.isNotEmpty()) {
                Text(
                    successMessage,
                    color = Color.Green,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (errorMessage.isNotEmpty()) {
                Text(
                    errorMessage,
                    color = Color.Red,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionButton(
    cameraLauncher: ActivityResultLauncher<Void?>
) {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    Button(
        onClick = {
            if (cameraPermissionState.status is PermissionStatus.Granted) {
                cameraLauncher.launch(null)
            } else {
                cameraPermissionState.launchPermissionRequest()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Text("Tomar Foto con Cámara")
    }
}
