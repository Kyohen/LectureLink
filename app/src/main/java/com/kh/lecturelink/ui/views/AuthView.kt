package com.kh.lecturelink.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kh.lecturelink.WrappedEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthView(passwordSubmitted: (String, WrappedEvent) -> Unit, cancel: () -> Unit, event: WrappedEvent, authfailed: Boolean) {
    var txt by remember { mutableStateOf("") }
    
    OutlinedCard(
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
        modifier = Modifier
            .height(height = 180.dp)
            .fillMaxWidth(0.8F),
        border = BorderStroke(1.dp, Color.Black)
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (authfailed) {
                OutlinedTextField(
                    value = txt,
                    onValueChange = { txt = it },
                    label = { Text("Enter password:") },
                    maxLines = 1,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = Color.Red),
                    visualTransformation = PasswordVisualTransformation()
                )
            } else {
                TextField(
                    value = txt,
                    onValueChange = { txt = it },
                    label = { Text("Enter password:") },
                    maxLines = 1,
                    visualTransformation = PasswordVisualTransformation()
                )
            }
            Button({ passwordSubmitted(txt, event) }, modifier = Modifier.fillMaxWidth(0.8f)) {
                Text("Submit")
            }
            Button(cancel, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("cancel")
            }
        }
    }
}