package com.yeonsik.fitnessapp.probe.wearable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Privacy explanation that Health Connect opens from its permission screen. Debug builds declare
 * it for `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (Android 13 and lower) and through
 * the `VIEW_PERMISSION_USAGE` alias (Android 14 and later). Release and QA builds do not have it.
 */
class WearableProbeRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Wearable PR0 Probe — 데이터 사용 안내", style = MaterialTheme.typography.titleLarge)
                        for (line in RATIONALE) {
                            Text("• $line", style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = ::finish) { Text("닫기") }
                    }
                }
            }
        }
    }

    private companion object {
        val RATIONALE = listOf(
            "이 화면은 개발용(debug) 빌드에만 있는 실기기 검증 도구입니다. 배포 빌드에는 포함되지 않습니다.",
            "심박수, 운동 세션, 걸음 수를 읽기 전용으로 조회합니다. Health Connect에 쓰거나 삭제하지 않습니다.",
            "조회 결과는 기기 밖으로 전송하지 않습니다. 심박 값과 원본 record id는 저장하지 않습니다.",
            "비교를 위해 기기 안에만 무작위 salt로 가린 record id와 changes token을 백업 제외 영역에 저장합니다.",
            "Fitness 운동 기록 DB를 열거나 바꾸지 않습니다.",
            "권한은 Health Connect 설정에서 언제든 철회할 수 있습니다.",
        )
    }
}
