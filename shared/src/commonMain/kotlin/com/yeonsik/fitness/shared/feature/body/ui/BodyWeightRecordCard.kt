package com.yeonsik.fitness.shared.feature.body.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens

@Composable
fun BodyWeightRecordCard(weightLabel: String, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.clickable(role = Role.Button, onClickLabel = "체중 기록 수정", onClick = onEdit),
        shape = RoundedCornerShape(FitnessComposeTokens.CARD_RADIUS_DP.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(FitnessComposeTokens.CARD_GAP_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(weightLabel, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp)
            }
            Text("수정", fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
