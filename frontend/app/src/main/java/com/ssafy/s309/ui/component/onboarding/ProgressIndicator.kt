package com.ssafy.s309.ui.component.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ssafy.s309.ui.theme.Border
import com.ssafy.s309.ui.theme.Primary

@Composable
fun ProgressIndicator(
    currentStep: Int,
    totalSteps: Int = 7,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(totalSteps) { index ->
            Spacer(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (index < currentStep) Primary else Border,
                            shape = RoundedCornerShape(3.dp),
                        ),
            )
        }
    }
}
