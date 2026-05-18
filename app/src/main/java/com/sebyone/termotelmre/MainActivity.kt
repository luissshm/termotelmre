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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
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
            TermotelApp(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermotelApp(viewModel: MainViewModel) {
    var showMenu by remember { mutableStateOf(false) }
    var showDiscoveryList by remember { mutableStateOf(false) }

    // Dialog states
    var showMapDialog by remember { mutableStateOf(false) }
    var showDdoDialog by remember { mutableStateOf(false) }
    var showDiscoveryInputDialog by remember { mutableStateOf(false) }
    var showGetAllNodesDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termotel MRE") },
                actions = {
                    IconButton(onClick = {
                        showDiscoveryList = !showDiscoveryList
                        if (showDiscoveryList) {
                            viewModel.refreshAcceptedDins()
                        }
                    }) {
                        Icon(
                            imageVector = if (showDiscoveryList) Icons.Default.Menu else Icons.AutoMirrored.Filled.List,
                            contentDescription = "Toggle View"
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Randomize DIN") },
                            onClick = {
                                viewModel.randomizeDin()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Randomize URI") },
                            onClick = {
                                viewModel.randomizeUri()
                                showMenu = false
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Map Device (BLE)") },
                            onClick = {
                                showMapDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send Test DDO") },
                            onClick = {
                                showDdoDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Discovery by SID") },
                            onClick = {
                                showDiscoveryInputDialog = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Get All Nodes") },
                            onClick = {
                                showGetAllNodesDialog = true
                                showMenu = false
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Status Section
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Device DIN: ${viewModel.localDin}", style = MaterialTheme.typography.bodyMedium)
                    Text("Device URI: ${viewModel.localUri}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Actions
            Button(
                onClick = { viewModel.startSequence() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("START AGENT")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.triggerJoin() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("JOIN")
                }
                Button(
                    onClick = { viewModel.triggerDiscovery(viewModel.driverInet4) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("DISCOVER INET4")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (showDiscoveryList) "Discovery List" else "Console Logs",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Console / List Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, shape = MaterialTheme.shapes.extraSmall)
                    .padding(8.dp)
            ) {
                if (showDiscoveryList) {
                    DiscoveryListView(viewModel)
                } else {
                    ConsoleView(viewModel)
                }
            }
        }
    }

    // Dialogs
    if (showMapDialog) {
        MapDeviceDialog(viewModel) { showMapDialog = false }
    }
    if (showDdoDialog) {
        SendDdoDialog(viewModel) { showDdoDialog = false }
    }
    if (showDiscoveryInputDialog) {
        DiscoveryInputDialog(viewModel) { showDiscoveryInputDialog = false }
    }
    if (showGetAllNodesDialog) {
        GetAllNodesDialog(viewModel) { showGetAllNodesDialog = false }
    }
}

@Composable
fun ConsoleView(viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true
    ) {
        items(viewModel.consoleLogs) { log ->
            Text(
                text = log,
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
fun DiscoveryListView(viewModel: MainViewModel) {
    var configSidInput by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = configSidInput,
            onValueChange = { configSidInput = it },
            label = { Text("Config SID", color = Color.LightGray) },
            placeholder = { Text("100") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.Green),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Green,
                unfocusedBorderColor = Color.Gray,
                focusedTextColor = Color.Green
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(viewModel.discoveredDins) { din ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIN: $din",
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )

                    Button(
                        onClick = {
                            val sid = configSidInput.toLongOrNull() ?: 0L
                            viewModel.sendNetworkConfiguration(din, sid)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("CONFIG", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MapDeviceDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var inputDin by remember { mutableStateOf("") }
    var inputUri by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map Device (BLE)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = inputDin, onValueChange = { inputDin = it }, label = { Text("DIN") })
                OutlinedTextField(value = inputUri, onValueChange = { inputUri = it }, label = { Text("URI") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val din = inputDin.toLongOrNull() ?: 0L
                viewModel.mapDevice(din, inputUri, viewModel.driverBle)
                onDismiss()
            }) { Text("Map") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SendDdoDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var inputDin by remember { mutableStateOf("") }
    var inputValue by remember { mutableStateOf("") }
    var inputTypeset by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Test DDO") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = inputDin, onValueChange = { inputDin = it }, label = { Text("DIN") })
                OutlinedTextField(value = inputValue, onValueChange = { inputValue = it }, label = { Text("Value") })
                OutlinedTextField(value = inputTypeset, onValueChange = { inputTypeset = it }, label = { Text("Typeset") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val din = inputDin.toLongOrNull() ?: 0L
                val value = inputValue.toIntOrNull()?.toLong() ?: 0
                val typeset = inputTypeset.toIntOrNull() ?: 0
                viewModel.sendTestDDO(din, value, typeset)
                onDismiss()
            }) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DiscoveryInputDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var discoveryInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discovery by SID") },
        text = {
            OutlinedTextField(
                value = discoveryInput,
                onValueChange = { discoveryInput = it },
                label = { Text("SID") },
                placeholder = { Text("e.g. 100") }
            )
        },
        confirmButton = {
            Button(onClick = {
                viewModel.triggerDiscoveryInput(discoveryInput)
                onDismiss()
            }) { Text("Discover") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun GetAllNodesDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var inputSid by remember { mutableStateOf(viewModel.deviceSid.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Get All Nodes") },
        text = {
            OutlinedTextField(
                value = inputSid,
                onValueChange = { inputSid = it },
                label = { Text("SID") },
                placeholder = { Text("SID") }
            )
        },
        confirmButton = {
            Button(onClick = {
                val din = inputSid.toLongOrNull() ?: 0L
                viewModel.getAllNodes(din)
                onDismiss()
            }) { Text("Get") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
