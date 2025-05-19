package com.example.userlocatorapp

import android.Manifest
import android.graphics.Bitmap
import android.location.Location
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.Glide
import com.google.accompanist.permissions.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val firestore = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val myUid = currentUser?.uid
    val scope = rememberCoroutineScope()

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var isConnected by remember { mutableStateOf(false) }
    var usersOnline by remember { mutableStateOf<Map<String, Pair<String, LatLng>>>(emptyMap()) }
    val locationList = remember { mutableStateListOf<LatLng>() }
    val userRoutes = remember { mutableStateMapOf<String, List<LatLng>>() }
    val userIcons = remember { mutableStateMapOf<String, BitmapDescriptor>() }

    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val fusedLocationProvider = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    var defaultUserIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var fallbackMeIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val latLng = LatLng(loc.latitude, loc.longitude)
                currentLocation = latLng
                locationList.add(latLng)

                val point = mapOf("lat" to loc.latitude, "lon" to loc.longitude)

                myUid?.let { uid ->
                    firestore.collection("usuarios").document(uid).update(
                        mapOf("lat" to loc.latitude, "lon" to loc.longitude)
                    )
                    firestore.collection("usuarios").document(uid)
                        .collection("rutas").add(point)
                }
            }
        }
    }

    DisposableEffect(isConnected) {
        if (isConnected && permissionState.status.isGranted) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateDistanceMeters(1f).build()
            try {
                fusedLocationProvider.requestLocationUpdates(
                    request,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            myUid?.let { uid ->
                firestore.collection("usuarios").document(uid).update("conectado", false)

                // Eliminar rutas del usuario en Firestore
                firestore.collection("usuarios").document(uid).collection("rutas")
                    .get()
                    .addOnSuccessListener { snapshots ->
                        snapshots.documents.forEach { it.reference.delete() }
                    }

                // Limpiar rutas en el frontend (propias y ajenas)
                locationList.clear()
                userRoutes.remove(uid)
            }
            fusedLocationProvider.removeLocationUpdates(locationCallback)
        }

        onDispose {
            fusedLocationProvider.removeLocationUpdates(locationCallback)
        }
    }

    LaunchedEffect(Unit) {
        if (!permissionState.status.isGranted) {
            permissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(Unit) {
        firestore.collection("usuarios")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val nuevosUsers = mutableMapOf<String, Pair<String, LatLng>>()

                    for (doc in it.documents) {
                        val uid = doc.id
                        val nombre = doc.getString("nombre") ?: continue
                        val lat = doc.getDouble("lat")
                        val lon = doc.getDouble("lon")
                        val fotoUrl = doc.getString("foto")

                        if (lat != null && lon != null && doc.getBoolean("conectado") == true) {
                            nuevosUsers[uid] = nombre to LatLng(lat, lon)
                        }

                        if (fotoUrl != null) {
                            scope.launch {
                                val icon = loadMarkerIconFromUrl(context, fotoUrl)
                                if (icon != null) {
                                    userIcons[uid] = icon
                                    usersOnline = usersOnline.toMap()
                                }
                            }
                        }

                        firestore.collection("usuarios").document(uid)
                            .collection("rutas")
                            .addSnapshotListener { rutaSnap, _ ->
                                rutaSnap?.let { rutaDocs ->
                                    val puntos = rutaDocs.mapNotNull { rutaDoc ->
                                        val latR = rutaDoc.getDouble("lat")
                                        val lonR = rutaDoc.getDouble("lon")
                                        if (latR != null && lonR != null) LatLng(latR, lonR) else null
                                    }
                                    userRoutes[uid] = puntos
                                }
                            }
                    }

                    usersOnline = nuevosUsers
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Estado: ${if (isConnected) "Conectado" else "No conectado"}")
            Switch(
                checked = isConnected,
                onCheckedChange = { checked ->
                    isConnected = checked
                    myUid?.let { uid ->
                        firestore.collection("usuarios").document(uid)
                            .update("conectado", checked)

                        if (!checked) {
                            firestore.collection("usuarios").document(uid).collection("rutas")
                                .get()
                                .addOnSuccessListener { snapshots ->
                                    snapshots.documents.forEach { it.reference.delete() }
                                }
                            locationList.clear()
                            userRoutes.remove(uid)
                        }
                    }
                }
            )
        }

        currentLocation?.let { loc ->
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(loc, 15f)
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                if (fallbackMeIcon == null || defaultUserIcon == null) {
                    val yoDrawable = ContextCompat.getDrawable(context, R.drawable.yo)?.toBitmap(100, 100)
                    val otrosDrawable = ContextCompat.getDrawable(context, R.drawable.demas)?.toBitmap(100, 100)

                    yoDrawable?.let {
                        fallbackMeIcon = BitmapDescriptorFactory.fromBitmap(it)
                    }
                    otrosDrawable?.let {
                        defaultUserIcon = BitmapDescriptorFactory.fromBitmap(it)
                    }
                }

                val myIcon = userIcons[myUid] ?: fallbackMeIcon
                myIcon?.let { icon ->
                    Marker(
                        state = MarkerState(position = loc),
                        title = "Tú",
                        icon = icon
                    )
                }

                usersOnline.forEach { (uid, data) ->
                    if (uid != myUid) {
                        val (name, latLng) = data
                        val icon = userIcons[uid] ?: defaultUserIcon
                        icon?.let {
                            Marker(
                                state = MarkerState(position = latLng),
                                title = name,
                                icon = it
                            )
                        }

                        userRoutes[uid]?.let { path ->
                            if (path.size > 1) {
                                Polyline(points = path, color = Color.Red)
                            }
                        }
                    }
                }

                if (locationList.size > 1) {
                    Polyline(points = locationList, color = Color.Blue)
                }
            }
        } ?: Text("Obteniendo ubicación...", modifier = Modifier.padding(16.dp), color = Color.Gray)
    }
}

suspend fun loadMarkerIconFromUrl(context: android.content.Context, url: String): BitmapDescriptor? {
    return withContext(Dispatchers.IO) {
        try {
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(url)
                .submit(100, 100)
                .get()
            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
