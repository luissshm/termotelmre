package com.sebyone.termotelmre

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Handle results if needed */ }

            LaunchedEffect(Unit) {
                val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    )
                } else {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                }
                permissionsLauncher.launch(permissions)
            }

            var inputDin by remember { mutableStateOf("") }
            var inputValue by remember { mutableStateOf("") }
            var inputTypeset by remember { mutableStateOf("") }
            var inputUri by remember { mutableStateOf("") }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device DIN: ${viewModel.localDin}", fontSize = 18.sp)
                    Button(onClick = { viewModel.randomizeDin() }) {
                        Text("RANDOMIZE DIN")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Device URI: ${viewModel.localUri}", fontSize = 18.sp)
                    Button(onClick = { viewModel.randomizeUri() }) {
                        Text("RANDOMIZE URI")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.startSequence() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("START AGENT")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.triggerDiscovery(viewModel.driverBle) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DISCOVER BLE")
                    }
                    Button(
                        onClick = { viewModel.triggerDiscovery(viewModel.driverInet4) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("DISCOVER INET4")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Common Parameters (DIN / URI):", fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputDin,
                        onValueChange = { inputDin = it },
                        label = { Text("DIN") },
                        placeholder = { Text("123") },
                        modifier = Modifier.weight(0.4f)
                    )
                    OutlinedTextField(
                        value = inputUri,
                        onValueChange = { inputUri = it },
                        label = { Text("URI") },
                        placeholder = { Text("aabbcc11") },
                        modifier = Modifier.weight(0.6f)
                    )
                }

                Button(
                    onClick = {
                        val din = inputDin.toLongOrNull() ?: 0L
                        viewModel.mapDevice(din, inputUri, viewModel.driverBle)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text("MAP DEVICE (BLE)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Send DDO Parameters (DIN / Value / Type):", fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputDin,
                        onValueChange = { inputDin = it },
                        label = { Text("DIN") },
                        placeholder = { Text("123") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        label = { Text("Value") },
                        placeholder = { Text("0") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = inputTypeset,
                        onValueChange = { inputTypeset = it },
                        label = { Text("Typeset") },
                        placeholder = { Text("1") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        val din = inputDin.toLongOrNull() ?: 0L
                        val value = inputValue.toIntOrNull()?.toByte() ?: 0
                        val typeset = inputTypeset.toIntOrNull() ?: 0
                        viewModel.sendTestDDO(din, value, typeset)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text("SEND TEST DDO")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Terminal-like output
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(8.dp),
                    reverseLayout = true // Newest logs at the bottom (visually top because reversed)
                ) {
                    items(viewModel.consoleLogs) { log ->
                        Text(
                            text = log,
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
