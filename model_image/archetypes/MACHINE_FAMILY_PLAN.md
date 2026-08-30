# Canonical Machine Family Plan

- 상태: **MACHINE_FAMILY_PLAN_APPROVED_SOURCE_PENDING**
- 목적: 운동별 신규 자산이 아니라 reusable canonical machine family를 계획한다.
- 실제 image generation API/adapter와 archetype camera/A/B는 아직 변경하지 않는다.

- MISSING_ASSET 운동: **29**
- machine family: **18**
- asset plan: **30** (기존 canonical 재사용 2, source 필요 28)
- unresolved machine-family TODO: **0**
- 힙 쓰러스트는 `machine_hip_thrust` 독립 reusable family이며 `glute_kickback_machine`과 통합하지 않는다.

| family | canonical asset | source action | recommended viewId | promotion | shared exercises |
|---|---|---|---|---|---|
| chest press machine family | chest_press_machine_flat_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 체스트 프레스 (chest_machine_chest_press) |
| chest press machine family | chest_press_machine_incline_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 인클라인 체스트 프레스 (chest_machine_incline_chest_press) |
| chest press machine family | chest_press_machine_decline_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 디클라인 체스트 프레스 (chest_machine_decline_chest_press) |
| chest press machine family | iso_lateral_chest_press_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 아이소 레터럴 체스트 프레스 (chest_machine_iso_lateral_chest_press) |
| machine curl family | machine_curl_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 바이셉스 컬 (arms_machine_biceps_curl) |
| machine curl family | preacher_curl_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 프리처 컬 (arms_machine_preacher_curl) |
| machine row family | seated_row_machine_front_side_three_quarter_v1 | REUSE_EXISTING_CANONICAL | front_side_three_quarter | ALREADY_PROMOTED | 로우 로우 머신 (back_machine_low_row) |
| machine row family | high_row_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 하이 로우 머신 (back_machine_high_row) |
| machine row family | t_bar_row_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 티바 로우 (back_machine_t_bar_row) |
| machine row family | chest_supported_row_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 체스트 서포티드 로우 (back_machine_chest_supported_row) |
| machine row family | iso_lateral_row_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 아이소 레터럴 로우 (back_machine_iso_lateral_row) |
| machine squat family | belt_squat_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 벨트 스쿼트 (legs_machine_belt_squat) |
| machine squat family | v_squat_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 브이 스쿼트 머신 (legs_machine_v_squat) |
| machine squat family | pendulum_squat_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 펜듈럼 스쿼트 (legs_machine_pendulum_squat) |
| machine squat family | hack_squat_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 핵 스쿼트 머신 (legs_machine_hack_squat) |
| machine shoulder press family | machine_shoulder_press_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 숄더 프레스 (shoulders_machine_shoulder_press) |
| machine triceps extension family | machine_triceps_extension_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 트라이셉스 익스텐션 (arms_machine_triceps_extension) |
| machine pullover | machine_pullover_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 풀오버 (back_machine_pullover) |
| ab crunch machine | ab_crunch_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 복근 크런치 머신 (abs_machine_ab_crunch) |
| back extension machine | back_extension_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 백 익스텐션 머신 (back_machine_back_extension) |
| reverse hyper machine | reverse_hyper_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 리버스 하이퍼익스텐션 머신 (back_machine_reverse_hyperextension) |
| glute kickback machine | glute_kickback_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 글루트 킥백 머신 (legs_machine_glute_kickback) |
| torso rotation machine | torso_rotation_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 토르소 로테이션 머신 (abs_machine_torso_rotation) |
| shrug machine | shrug_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 슈러그 (back_machine_shrug) |
| 기타 / machine leg curl family | standing_leg_curl_machine_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 스탠딩 레그 컬 (legs_machine_standing_leg_curl) |
| 기타 / reverse pec-deck family | pec_deck_front_v1 | REUSE_EXISTING_CANONICAL | front | ALREADY_PROMOTED | 머신 리어 델트 플라이 (shoulders_machine_rear_delt_fly) |
| 기타 / reverse pec-deck family | reverse_pec_deck_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | rear_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | - |
| 기타 / machine dip press family | machine_dip_press_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 딥 프레스 (arms_machine_dip_press) |
| 기타 / machine lateral raise family | machine_lateral_raise_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | front_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 머신 레터럴 레이즈 (shoulders_machine_lateral_raise) |
| machine hip thrust family | machine_hip_thrust_v1 | SOURCE_REQUIRED_NO_IMAGE_YET | side_three_quarter | PLANNED_AFTER_SOURCE_REVIEW | 힙 쓰러스트 머신 (legs_machine_hip_thrust) |

## Exercise mapping

| exercise | familyId | canonical asset |
|---|---|---|
| 백 익스텐션 머신 (back_machine_back_extension) | back_extension | back_extension_machine_v1 |
| 머신 바이셉스 컬 (arms_machine_biceps_curl) | biceps_curl | machine_curl_v1 |
| 머신 프리처 컬 (arms_machine_preacher_curl) | biceps_curl | preacher_curl_machine_v1 |
| 머신 디클라인 체스트 프레스 (chest_machine_decline_chest_press) | chest_press | chest_press_machine_decline_v1 |
| 머신 인클라인 체스트 프레스 (chest_machine_incline_chest_press) | chest_press | chest_press_machine_incline_v1 |
| 머신 체스트 프레스 (chest_machine_chest_press) | chest_press | chest_press_machine_flat_v1 |
| 아이소 레터럴 체스트 프레스 (chest_machine_iso_lateral_chest_press) | chest_press | iso_lateral_chest_press_v1 |
| 복근 크런치 머신 (abs_machine_ab_crunch) | crunch | ab_crunch_machine_v1 |
| 글루트 킥백 머신 (legs_machine_glute_kickback) | glute_kickback | glute_kickback_machine_v1 |
| 힙 쓰러스트 머신 (legs_machine_hip_thrust) | hip_thrust | machine_hip_thrust_v1 |
| 머신 레터럴 레이즈 (shoulders_machine_lateral_raise) | lateral_raise | machine_lateral_raise_v1 |
| 스탠딩 레그 컬 (legs_machine_standing_leg_curl) | leg_curl | standing_leg_curl_machine_v1 |
| 머신 딥 프레스 (arms_machine_dip_press) | machine_dip_press | machine_dip_press_v1 |
| 벨트 스쿼트 (legs_machine_belt_squat) | machine_squat | belt_squat_machine_v1 |
| 브이 스쿼트 머신 (legs_machine_v_squat) | machine_squat | v_squat_machine_v1 |
| 펜듈럼 스쿼트 (legs_machine_pendulum_squat) | machine_squat | pendulum_squat_machine_v1 |
| 핵 스쿼트 머신 (legs_machine_hack_squat) | machine_squat | hack_squat_machine_v1 |
| 머신 트라이셉스 익스텐션 (arms_machine_triceps_extension) | machine_triceps_extension | machine_triceps_extension_v1 |
| 머신 숄더 프레스 (shoulders_machine_shoulder_press) | overhead_press | machine_shoulder_press_v1 |
| 머신 풀오버 (back_machine_pullover) | pullover | machine_pullover_v1 |
| 머신 리어 델트 플라이 (shoulders_machine_rear_delt_fly) | rear_delt_fly | pec_deck_front_v1 |
| 리버스 하이퍼익스텐션 머신 (back_machine_reverse_hyperextension) | reverse_hyperextension | reverse_hyper_machine_v1 |
| 머신 체스트 서포티드 로우 (back_machine_chest_supported_row) | row | chest_supported_row_machine_v1 |
| 로우 로우 머신 (back_machine_low_row) | row | seated_row_machine_front_side_three_quarter_v1 |
| 하이 로우 머신 (back_machine_high_row) | row | high_row_machine_v1 |
| 머신 티바 로우 (back_machine_t_bar_row) | row | t_bar_row_machine_v1 |
| 아이소 레터럴 로우 (back_machine_iso_lateral_row) | row | iso_lateral_row_machine_v1 |
| 머신 슈러그 (back_machine_shrug) | shrug | shrug_machine_v1 |
| 토르소 로테이션 머신 (abs_machine_torso_rotation) | torso_rotation | torso_rotation_machine_v1 |

## Promotion boundary

- `SOURCE_REQUIRED_NO_IMAGE_YET` 자산은 source PNG, anchors, SHA-256, 검수 후에만 `final/`과 `equipment-catalog.json`에 등록한다.
- `pec_deck_front_v1`은 reverse orientation 합성 테스트에 먼저 사용하며, 부자연스러울 때만 `reverse_pec_deck_v1`을 source 생성 대상으로 전환한다.
- 승인된 viewId는 계획값으로 제시했으며, 실제 camera/A/B 결정과 혼동하지 않는다.
