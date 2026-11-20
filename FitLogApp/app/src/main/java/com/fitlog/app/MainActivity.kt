package com.fitlog.app

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FitLogTheme { LogScreen() } }
    }
}

@Composable
fun FitLogTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: LogViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(vm.scriptUrl.isBlank()) }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // When the typed name matches a known exercise, fetch what you did last time.
    LaunchedEffect(vm.exercise) {
        vm.lastSession = null
        if (vm.exercises.any { it.equals(vm.exercise.trim(), ignoreCase = true) }) {
            delay(400)
            vm.loadLast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitLog") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.pendingCount > 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${vm.pendingCount} workout(s) saved on phone, not yet in the sheet", Modifier.weight(1f))
                        TextButton(onClick = vm::syncPending) { Text("Sync") }
                    }
                }
            }

            OutlinedButton(onClick = {
                val d = runCatching { LocalDate.parse(vm.date) }.getOrDefault(LocalDate.now())
                DatePickerDialog(ctx, { _, y, m, day ->
                    vm.date = LocalDate.of(y, m + 1, day).toString()
                }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(vm.date)
            }

            ExerciseField(vm)

            vm.lastSession?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Unit:", style = MaterialTheme.typography.bodyLarge)
                listOf("kg", "lb").forEach { u ->
                    FilterChip(selected = vm.unit == u, onClick = { vm.changeUnit(u) }, label = { Text(u) })
                }
            }

            vm.sets.forEachIndexed { i, s ->
                SetRow(
                    index = i,
                    entry = s,
                    unit = vm.unit,
                    onChange = { vm.sets[i] = it },
                    onRemove = { vm.removeSet(i) }
                )
            }

            OutlinedButton(onClick = vm::addSet) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add set")
            }

            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = vm::save,
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (vm.saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text("Save to Google Sheets")
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            initialUrl = vm.scriptUrl,
            initialSecret = vm.secret,
            onSave = { url, secret -> vm.saveSettings(url, secret); showSettings = false },
            onDismiss = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseField(vm: LogViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val typed = vm.exercise.trim()
    val matches = vm.exercises
        .filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }
        .take(8)
    val open = expanded && matches.isNotEmpty()

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = vm.exercise,
            onValueChange = { vm.exercise = it; expanded = true },
            label = { Text("Exercise") },
            singleLine = true,
            trailingIcon = { if (vm.exercises.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            matches.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { vm.exercise = name; expanded = false })
            }
        }
    }
}

@Composable
fun SetRow(index: Int, entry: SetEntry, unit: String, onChange: (SetEntry) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${index + 1}", Modifier.width(20.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = entry.weight,
            onValueChange = { v -> onChange(entry.copy(weight = v.filter { it.isDigit() || it == '.' || it == ',' })) },
            label = { Text("Weight ($unit)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = entry.reps,
            onValueChange = { v -> onChange(entry.copy(reps = v.filter { it.isDigit() })) },
            label = { Text("Reps") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove set ${index + 1}")
        }
    }
}

@Composable
fun SettingsDialog(
    initialUrl: String,
    initialSecret: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    var secret by remember { mutableStateOf(initialSecret) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to Google Sheets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paste the Web app URL from your Apps Script deployment, and the SECRET you set in Code.gs.")
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Web app URL (…/exec)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it },
                    label = { Text("Secret") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(url.trim(), secret.trim()) },
                enabled = url.trim().startsWith("https://")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
