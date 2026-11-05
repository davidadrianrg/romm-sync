package com.davidadrianrg.atril.core.util

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PageHeader(
    title: String,
    navIcon: String? = null,
    action: String? = null,
    onAction: () -> Unit = { },
    back: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (navIcon != null || action != null) Arrangement.SpaceBetween else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )

            if (navIcon != null) {
                Icon(
                    Icons.Filled.Search, // Usaremos un icono genérico; cada pantalla pasará el suyo
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (action != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(start = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(action)
                }
            }

            if (back) {
                IconButton(
                    onClick = { /* back action */ },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.ArrowBack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
