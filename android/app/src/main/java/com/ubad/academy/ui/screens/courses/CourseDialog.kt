package com.ubad.academy.ui.screens.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ubad.academy.R
import com.ubad.academy.core.jsString
import com.ubad.academy.data.repository.CourseRepository
import com.ubad.academy.domain.model.Course

/** `openCourseModal(c, cb)` */
@Composable
fun CourseDialog(existing: Course?, onSave: (CourseRepository.CourseInput) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var code by rememberSaveable { mutableStateOf(existing?.code.orEmpty()) }
    var credits by rememberSaveable { mutableStateOf((existing?.credits ?: 3.0).jsString()) }
    var instructor by rememberSaveable { mutableStateOf(existing?.instructor.orEmpty()) }
    var semester by rememberSaveable { mutableStateOf(existing?.semester.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing != null) R.string.courses_edit else R.string.courses_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.courses_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(code, { code = it.take(24) }, label = { Text(stringResource(R.string.courses_code)) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        credits, { v -> credits = v.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text(stringResource(R.string.courses_credits)) }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OutlinedTextField(instructor, { instructor = it.take(80) }, label = { Text(stringResource(R.string.courses_instructor)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(semester, { semester = it.take(40) }, label = { Text(stringResource(R.string.courses_semester)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                // clampNum(value, 0, 99, 3)
                val cr = credits.trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() }?.coerceIn(0.0, 99.0) ?: 3.0
                onSave(CourseRepository.CourseInput(name, code, instructor, cr, semester))
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
