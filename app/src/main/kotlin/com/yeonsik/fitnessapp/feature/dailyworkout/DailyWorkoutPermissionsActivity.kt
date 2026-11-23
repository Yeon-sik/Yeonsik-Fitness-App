package com.yeonsik.fitnessapp.feature.dailyworkout

import android.os.Bundle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme

class DailyWorkoutPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FitnessComposeTheme(isSystemInDarkTheme()) {
                Scaffold { insets ->
                    Column(Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("워치 기록 읽기", style = MaterialTheme.typography.headlineSmall)
                        Text("선택한 날짜의 운동, 심박, 거리, 활동 칼로리를 읽어 하루 운동 기록에 연결합니다.")
                        Text("밴드 가져오기에서 선택한 운동과 심박은 현재 계정의 기기 저장소에 보관됩니다. Fitness 운동에 연결한 뒤에도 원래 세트와 수동 기록은 유지됩니다.")
                        Text("하루 운동 기록에서 JSON 저장을 선택하면 그날의 운동과 심박 요약을 파일로 내보냅니다.")
                        Text("읽기 권한은 Health Connect 설정에서 변경할 수 있습니다.")
                        TextButton(onClick = ::finish) { Text("닫기") }
                    }
                }
            }
        }
    }
}
