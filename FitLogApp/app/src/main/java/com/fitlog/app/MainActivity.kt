package com.fitlog.app

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // When person + a known exercise are set, fetch what that person did last time.
    LaunchedEffect(vm.exercise, vm.person) {
        vm.lastSession = null
        if (vm.person.isNotBlank() && vm.exerciseCode() in vm.exercises) {
            delay(400)
            vm.loadLast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo()
                        Spacer(Modifier.width(10.dp))
                        Text("FitLog", fontWeight = FontWeight.SemiBold)
                    }
                },
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
            if (vm.today.isNotEmpty()) TodayList(vm.today, onEdit = { editingId = it.id })
            Spacer(Modifier.height(8.dp))
        }
    }

    vm.today.firstOrNull { it.id == editingId }?.let { s ->
        EditSetDialog(
            s = s,
            onSave = { w, r -> vm.editSet(s, w, r) },
            onDelete = { vm.deleteSet(s) },
            onDismiss = { editingId = null }
        )
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

/** The launcher icon in miniature: the same white dumbbell on the same lime. */
@Composable
fun AppLogo() {
    Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Lime),
        contentAlignment = Alignment.Center
    ) {
        // The icon artwork sits inside a 108-unit canvas with padding, so draw it larger than the box.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "FitLog logo",
            modifier = Modifier.requiredSize(46.dp)
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
    // Codes stay internal; the menu shows and matches on pretty names ("Leg Press").
    val typed = vm.exercise.trim()
    val typedCode = normExercise(typed)
    val matches = vm.exercises
        .map { it to prettyExercise(it) }
        .filter { (code, pretty) ->
            !pretty.equals(typed, ignoreCase = true) &&
                (pretty.contains(typed, ignoreCase = true) || code.contains(typedCode))
        }
        .sortedBy { it.second.lowercase() }
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
            matches.forEach { (_, pretty) ->
                DropdownMenuItem(text = { Text(pretty) }, onClick = { vm.exercise = pretty; expanded = false })
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

/** One set per save: weight × reps. The sheet script numbers the set automatically. */
@Composable
fun SetsCard(vm: LogViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Header("Weight", Modifier.weight(1f))
                Spacer(Modifier.width(28.dp))
                Header("Reps", Modifier.weight(1f))
            }
            SetRow(entry = vm.current, onChange = { vm.current = it })
        }
    }
}

/**
 * Every set saved from this phone today, newest first, so you never have to wonder whether one
 * went through. Green tick = the script confirmed the row is in the sheet; amber = on the phone,
 * not yet sent. The newest set is highlighted.
 */
@Composable
fun TodayList(sets: List<SavedSet>, onEdit: (SavedSet) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Text("Today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "${sets.size} set${if (sets.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        sets.forEachIndexed { i, s -> TodayRow(s, newest = i == 0, onClick = { onEdit(s) }) }
    }
}

@Composable
private fun TodayRow(s: SavedSet, newest: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        !s.inSheet -> colors.tertiaryContainer
        newest -> colors.primaryContainer
        else -> colors.surfaceContainerLow
    }
    val content = when {
        !s.inSheet -> colors.onTertiaryContainer
        newest -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    Surface(
        onClick = onClick,
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = if (newest) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (s.inSheet) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
                contentDescription = if (s.inSheet) "Saved to sheet" else "Waiting to sync",
                tint = if (s.inSheet && !newest) colors.primary else content,
                modifier = Modifier.size(if (newest) 28.dp else 20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    (s.setNo?.let { "Set $it · " } ?: "") + s.exercise,
                    style = if (newest) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (newest) FontWeight.SemiBold else FontWeight.Medium
                )
                Text(
                    "${s.person} · ${s.weight} × ${s.reps}" +
                        if (s.inSheet) (s.row?.let { " · row $it" } ?: "") else " · waiting to sync",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit set",
                tint = content.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Fix the weight/reps of a set, or delete it (with a confirmation step). */
@Composable
fun EditSetDialog(
    s: SavedSet,
    onSave: suspend (String, String) -> Boolean,
    onDelete: suspend () -> Boolean,
    onDismiss: () -> Unit
) {
    var entry by remember(s.id) { mutableStateOf(SetEntry(s.weight, s.reps.toString())) }
    var confirmDelete by remember(s.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val danger = MaterialTheme.colorScheme.error

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text((s.setNo?.let { "Set $it · " } ?: "") + s.exercise) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    s.person + if (s.inSheet) " · in the sheet" else " · waiting to sync",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (confirmDelete) {
                    Text(
                        if (s.inSheet) "Delete this set from the sheet? Later sets of ${s.person}'s ${s.exercise} today will be renumbered."
                        else "Delete this set? It hasn't been sent to the sheet yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Header("Weight", Modifier.weight(1f))
                        Spacer(Modifier.width(28.dp))
                        Header("Reps", Modifier.weight(1f))
                    }
                    SetRow(entry = entry, onChange = { entry = it })
                }
                if (busy) CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.CenterHorizontally), strokeWidth = 2.dp)
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(
                    enabled = !busy,
                    onClick = { scope.launch { busy = true; val ok = onDelete(); busy = false; if (ok) onDismiss() } }
                ) { Text("Delete", color = danger) }
            } else {
                TextButton(
                    enabled = !busy,
                    onClick = { scope.launch { busy = true; val ok = onSave(entry.weight, entry.reps); busy = false; if (ok) onDismiss() } }
                ) { Text("Save") }
            }
        },
        dismissButton = {
            Row {
                if (!confirmDelete) {
                    TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete", color = danger) }
                }
                TextButton(enabled = !busy, onClick = { if (confirmDelete) confirmDelete = false else onDismiss() }) {
                    Text(if (confirmDelete) "Back" else "Cancel")
                }
            }
        }
    )
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
fun SetRow(entry: SetEntry, onChange: (SetEntry) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
            modifier = Modifier.width(28.dp)
        )
        NumberBox(
            value = entry.reps,
            onValueChange = { v -> onChange(entry.copy(reps = v.filter { it.isDigit() })) },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f)
        )
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
