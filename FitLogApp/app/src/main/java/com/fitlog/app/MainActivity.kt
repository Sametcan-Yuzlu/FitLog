package com.fitlog.app

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(vm: LogViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(vm.scriptUrl.isBlank()) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // When person + a known exercise are set, fetch what that person did last time.
    LaunchedEffect(vm.exercise, vm.person) {
        vm.lastSession = null
        if (vm.person.isNotBlank() && normExercise(vm.exercise) in vm.exercises) {
            delay(400)
            vm.loadLast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FitLog", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        // Save stays pinned at the bottom and rides above the keyboard.
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = vm::save,
                    enabled = !vm.saving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Lime,
                        contentColor = LimeInk,
                        disabledContainerColor = Lime.copy(alpha = 0.6f),
                        disabledContentColor = LimeInk
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (vm.saving) CircularProgressIndicator(Modifier.size(22.dp), color = LimeInk, strokeWidth = 2.dp)
                    else Text("Save to sheet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (vm.pendingCount > 0) PendingBanner(vm)

            PersonAndDate(vm)
            ExerciseField(vm)

            vm.lastSession?.let { LastSessionHint(it) }

            SetsCard(vm)
            Spacer(Modifier.height(8.dp))
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

@Composable
fun PendingBanner(vm: LogViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${vm.pendingCount} workout(s) waiting to sync",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = vm::syncPending) {
                Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sync")
            }
        }
    }
}

/** Person field and a compact date button on one row, with partner chips underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonAndDate(vm: LogViewModel) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = vm.person,
                onValueChange = vm::changePerson,
                label = { Text("Person") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    val d = vm.date
                    DatePickerDialog(ctx, { _, y, m, day ->
                        vm.date = LocalDate.of(y, m + 1, day)
                    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.padding(top = 8.dp).height(56.dp)
            ) {
                Icon(Icons.Filled.DateRange, contentDescription = "Change date", Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (vm.date == LocalDate.now()) "Today" else "${vm.date.monthValue}/${vm.date.dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        if (vm.people.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.people.forEach { p ->
                    FilterChip(
                        selected = p.equals(vm.person.trim(), ignoreCase = true),
                        onClick = { vm.changePerson(p) },
                        label = { Text(p) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lime,
                            selectedLabelColor = LimeInk
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseField(vm: LogViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val typed = normExercise(vm.exercise)
    val matches = vm.exercises
        .filter { it.contains(typed) && it != typed }
        .take(8)
    val open = expanded && matches.isNotEmpty()

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = vm.exercise,
            onValueChange = { vm.exercise = it; expanded = true },
            label = { Text("Exercise") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
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
fun LastSessionHint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** All sets in one card: a header row, compact number boxes, and "Add set" at the bottom. */
@Composable
fun SetsCard(vm: LogViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Header("Set", Modifier.width(36.dp))
                Header("Weight", Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                Header("Reps", Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
            }
            vm.sets.forEachIndexed { i, s ->
                SetRow(
                    index = i,
                    entry = s,
                    onChange = { vm.sets[i] = it },
                    onRemove = { vm.removeSet(i) }
                )
            }
            TextButton(onClick = vm::addSet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add set")
            }
        }
    }
}

@Composable
private fun Header(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

@Composable
fun SetRow(index: Int, entry: SetEntry, onChange: (SetEntry) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(28.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        NumberBox(
            value = entry.weight,
            onValueChange = { v -> onChange(entry.copy(weight = v.filter { it.isDigit() || it == '.' || it == ',' })) },
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f)
        )
        Text(
            "×",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(20.dp)
        )
        NumberBox(
            value = entry.reps,
            onValueChange = { v -> onChange(entry.copy(reps = v.filter { it.isDigit() })) },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove set ${index + 1}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** A 48dp-tall, centred number field — much more compact than an OutlinedTextField. */
@Composable
fun NumberBox(value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        modifier = modifier
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(colors.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.primary else colors.outlineVariant, shape),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                if (value.isEmpty()) {
                    Text(
                        "–",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                Box(Modifier.fillMaxWidth()) { inner() }
            }
        }
    )
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secret, onValueChange = { secret = it },
                    label = { Text("Secret") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp),
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
