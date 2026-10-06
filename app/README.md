# 지도 데이터 계층 및 Localization 기본 구조

현재 저장소에는 기존 앱/빌드 설정이 없고 `PROJECT_STRUCTURE.md`, `floor2_map.json`만 있었다.
구조 문서의 Kotlin 코드 명명 권장안을 따라 Android API에 의존하지 않는 Kotlin/JVM 코어를
`app/src/main/kotlin/indoor/`에 추가했다. 실제 센서 연결 시 Android 앱에서 이 코어를 사용하면 된다.
최상위 `localization/`은 구조 문서에서 설정·보정·특징 파일 저장 위치이므로 이번 단계에서
임의의 설정이나 특징 데이터를 만들지 않았다.

## 원본과 실제 스키마

두 원본 파일을 먼저 읽고 분석했으며 수정·이동·삭제하지 않았다.
문서 예시의 `floorN.map.json` 형태로 변환하지 않고 현재 `floor2_map.json`을 직접 읽는다.

| 영역 | 실제 내용 및 코드 해석 |
| --- | --- |
| `schema_version` | `1.0.0`; 지원하지 않는 버전은 명시적 오류 |
| `metadata` | `map_id=floor2`, `floor=2`, `unit=meter` |
| `coordinate_system` | 실측 B0 `(0,0)`가 원점. 자북 기준 시계방향 도 단위 `[0,360)` |
| 축 방향 | +X=300°, -X=120°, +Y=210°, -Y=30° |
| `measurement.nodes` | B0~B5, `id`, `raw_position.x/y`; 실측 좌표 보존 |
| `measurement.corridors` | C0~C2, `node_ids`, 폭, `direction`, `heading` |
| `navigation.nodes` | B0~B5, `type`, `description`, `position.x/y`, `images`; 선택적으로 POI·방번호·분기 상태 |
| 노드 타입 | B0/B4=anchor, B1/B2/B3=intersection, B5=destination |
| `navigation.edges` | 5개. `id`, `from`, `to`, `corridor_id`, `distance`, `bidirectional`, `walkable`, `heading.forward/reverse` |
| 이동/거리 | 현재 모든 간선 양방향·보행 가능. 거리는 navigation 좌표 간 유클리드 거리와 일치 |
| 이미지 | 현재 모든 `images`는 빈 배열. 임베디드 `image_metadata_schema`는 각 항목의 `id`, `path` 필수 지정 |
| 이미지 선택 필드 | `file`, `direction`, `heading`, `caption`, `mime_type`, `captured_at`, `position_source`, `extra_metadata`; 추가 필드 허용 |

| ID | 실측 (x, y) | 길안내 (x, y) |
| --- | --- | --- |
| B0 | (0, 0) | (0, -0.975) |
| B1 | (2.5, 0) | (3.775, -0.975) |
| B2 | (23.7, 0) | (23.7, -0.975) |
| B3 | (44, 0) | (42.6, -0.975) |
| B4 | (44, -2.25) | (42.6, -2.25) |
| B5 | (2.5, 4.2) | (3.775, 4.2) |

간선: B0–B1=3.775m, B1–B2=19.925m, B2–B3=18.9m,
B1–B5=5.175m, B3–B4=1.275m.

## 새 파일과 역할

| 파일 (프로젝트 기준) | 역할 |
| --- | --- |
| `app/src/main/kotlin/indoor/map/MapModels.kt` | FloorMap, Node, Edge, Coordinate 및 실제 JSON의 메타데이터·실측·복도·이미지 모델 |
| `app/src/main/kotlin/indoor/map/MapJsonLoader.kt` | Gson 기반 명시적 파싱, 타입·필수 값·유한 수치·ID 중복·참조 검증, MapFormatException |
| `app/src/main/kotlin/indoor/map/IndoorMapRepository.kt` | 저장소 인터페이스/JSON 구현, node ID/incident edge 인덱스, 향후 NearestNodeSearch 인터페이스 |
| `app/src/main/kotlin/indoor/localization/PositionEstimate.kt` | 위치 공통 결과와 PDR/VISUAL/FUSED 출처, 좌표 기준 종류 |
| `app/src/main/kotlin/indoor/localization/pdr/PdrLocalizer.kt` | 센서 입력과 기준점 초기화·보정을 위한 인터페이스 |
| `app/src/main/kotlin/indoor/localization/visual/VisualLocalizer.kt` | 플랫폼별 프레임 입력 및 불확실한 매칭의 null 결과 계약 |
| `app/src/main/kotlin/indoor/localization/fusion/PositionFusionEngine.kt` | 공통 위치 결과 입력, 융합 결과 출력·리셋 계약 |
| `app/src/main/kotlin/indoor/localization/matching/MapMatcher.kt` | 지도와 추정 위치 입력, 매칭 위치 또는 null 출력 계약 |
| `app/src/main/kotlin/indoor/localization/coordinates/HeadingToMapConverter.kt` | JSON 축 기반 heading/거리 투영, 정규화, MapDisplacement(dx, dy) |
| `app/src/test/kotlin/indoor/CoreTests.kt` | 실제 JSON 검증 및 메모리상의 테스트 fixture/mock; 22개 테스트 그룹 |
| `app/src/test/kotlin/indoor/HeadingToMapConverterTests.kt` | 실제 지도 축·중간 방향·정규화·거리 보존·잘못된 입력 검증; 12개 테스트 그룹 |
| `scripts/build-test.ps1` | Kotlin/JVM 코어·테스트 컴파일 및 실패 시 비정상 종료 |
| `.gitignore` | 생성된 `app/build/` 제외 |
| `app/README.md` | 분석 결과, API 계약, 실행 방법, 제한 사항 및 후속 작업 |

최초 데이터 계층 작업에서는 기존 파일을 수정하지 않았다. 후속 방향 변환 작업에서는
변환 모듈과 테스트 파일을 생성하고, `CoreTests.kt`의 테스트 연결과 이 README를 수정했다.
`floor2_map.json` 및 기존 localization 인터페이스는 그대로 유지했다.
빌드 산출물은 `app/build/indoor-core.jar`, `indoor-tests.jar` 및 컴파일 인자 파일이다.

## 사용 방법

```kotlin
val repository: IndoorMapRepository = JsonIndoorMapRepository.load(Path.of("floor2_map.json"))
val floor = repository.floorMap.metadata.floor
val nodes = repository.getNodes()
val node = repository.getNode("B1")
val edges = repository.getEdges()
val incident = repository.getConnectedEdges("B1")
val navigationPosition = repository.getPosition("B1")       // (3.775, -0.975)
val measuredPosition = repository.getMeasuredPosition("B1") // (2.5, 0)
val connected = repository.areConnected("B1", "B0")
val traversable = repository.canTraverse("B1", "B0")
```

호출자가 경로를 주입한다. `load(Reader)`도 제공하므로 Android asset, 메모리 fixture 등에서 사용 가능하다.
Path 버전은 내부 Reader를 닫으며 Reader 버전의 수명은 호출자가 관리한다.
없는 node ID 조회는 null, 간선 조회는 빈 목록, 연결 조회는 false다.
`getConnectedEdges`/`areConnected`는 방향과 통행 가능 여부에 무관한 구조적 연결이고,
`canTraverse`는 단일 간선의 통행 가능 여부와 방향을 검사한다. 여러 간선을 거치는 경로 탐색은 하지 않는다.
최근접 검색은 별도 `NearestNodeSearch` 확장 인터페이스만 제공하며 아직 구현하지 않았다.

PositionEstimate는 x/y, floor, heading, confidence, timestamp, source와
positionReference, 선택적 nodeId를 갖는다. heading은 실제 JSON의 자북 기준을 따르고
모르면 null이다. confidence는 [0,1], timestamp는 Unix milliseconds다.
플랫폼 센서의 부팅 후 경과 시간을 그대로 전달하지 말고 추후 어댑터에서 공통 시간 기준으로 변환해야 한다.
실측/길안내 anchor 구분을 호출자가 명시하므로 임의 좌표 보정을 하지 않는다.
인터페이스의 null 결과는 위치 추정 불가를 뜻하며 임의의 (0,0) 결과로 대체하지 않는다.

예정 흐름: JSON → Repository → FloorMap;
PdrLocalizer/VisualLocalizer → PositionFusionEngine → MapMatcher → 현재 PositionEstimate.
현재 이 파이프라인을 자동 실행하는 orchestrator나 알고리즘 구현은 없다.

## 검증 및 빌드

필요 환경: JDK 17, PATH의 Kotlin compiler 2.x (`kotlinc`), Gson 2.10.1 jar, PowerShell.
검증 환경은 JDK 17.0.12 / kotlinc 2.4.0이었다. 기존 Gradle 캐시의 Gson을 사용했고 설치·다운로드하지 않았다.

```powershell
.\scripts\build-test.ps1
# 다른 환경에서는 의존성 위치 명시:
.\scripts\build-test.ps1 -GsonJar 'C:\dependencies\gson-2.10.1.jar'
```

결과: 코어/테스트 컴파일 성공, **34개 테스트 그룹 모두 통과** (기존 22개 + 방향 변환 12개).
좌표 보존, 실제 간선 거리, 방향/통행 제한, 누락·타입 오류·중복 ID·잘못된 참조·잘못된 JSON,
이미지 확장 필드, 공통 위치 모델과 독립 mock 연결을 확인했다.
테스트용 이미지 경로는 파일을 읽지 않고 메모리에서 파서만 검증한다.
Gson은 런타임 의존성이며 코어 jar에 포함되지 않는다. Android APK 빌드는 앱 설정이 없어 수행하지 않았다.
임베디드 image metadata schema는 보존하지만 범용 JSON Schema 검증기를 구현하지는 않았다.

## 발견 사항과 향후 필요한 정보

1. 문서 예시의 +Y=0°와 실제 자북 축 정의가 다르다. 실제 JSON이 우선이며,
   HeadingToMapConverter는 각 축 방향을 사용한다. 단순히 heading을 좌표 각도로 쓰면 안 된다.
2. B0의 원점은 실측 (0,0)이고 길안내 B0는 (0,-0.975)다. 다른 노드도 보정량이 다르므로
   실측 좌표를 중심선 좌표로 일괄 평행이동하지 않는다. 이미지 `position_source`를 명확히 기록해야 한다.
3. 이미지가 등록되지 않았다. 스키마상 heading/position_source는 선택 사항이므로
   실제 위치 보정에 사용할 이미지에는 촬영 방향과 좌표 종류를 확정해야 한다. 현재 코드가 이를 추측하지 않는다.
4. 이미지 path 설명은 JSON 기준 상대 경로·절대 경로·URL을 허용하지만 구조 문서는 프로젝트 기준 상대 경로를 권장한다.
   현재 문자열을 그대로 보존하고 파일 접근은 하지 않는다. 이미지 로더 추가 전에 기준 디렉터리를 확정해야 한다.
5. 현재 파일은 지도와 경로 및 빈 이미지 참조를 함께 담는다. 이번에는 유지했다.
   향후 route/visual 파일 분리는 별도 호환성 작업이며 node ID를 유지해야 한다.
6. B2의 추가 분기는 실제로 있지만 비활성 상태이며 간선이 없다. 목적지 그래프에 임의 추가하지 않았다.
7. `walkable`은 있으나 휠체어 접근성·제한 통행·층간 연결 등의 상세 정책 필드는 없다.
   복도 폭은 있지만 완전한 벽/통행 폴리곤은 없어 정교한 Map Matching의 제약 데이터는 추후 필요하다.
8. PDR 보폭/센서 보정값, 시간 정렬 정책, 영상 인식 임계값·feature 모델 버전·융합 신뢰도 정책은
   실제 실험 후 별도 localization 설정에 정의해야 한다. 지도에 임의 수치를 넣지 않았다.

## 이번에 구현하지 않은 기능

실제 IMU/카메라 어댑터, PDR, 이미지 특징 추출/검색, OpenCV·DINOv2·AnyLoc,
위치 융합·Map Matching 알고리즘, 최근접 노드 검색, A*, TTS, Android UI는 구현하지 않았다.

## 다음 작업 3개

1. 공통 timestamp 변환 및 기기 자세→실제 이동 heading 보정 계약을 확정하고 실측 기록으로 검증한다.
2. 이미지 촬영 위치·방향·경로 기준을 확정하고 node ID 기반 VisualRepository와 실제 기준 이미지 목록을 연결한다.
3. 실제 IMU 기록을 수집해 보폭/방향을 보정한 뒤 PdrLocalizer를 구현하고, 공통 PositionEstimate로 검증한다.

## Heading과 이동거리의 지도 좌표 변환

`indoor.localization.coordinates.HeadingToMapConverter`는 상태를 보관하지 않는 독립 모듈이다.
생성자에 Repository에서 로딩한 `MapMetadata`를 주입하며, 축 정의와 단위를 검증한다.
Android Sensor API, 걸음 검출, 보폭 추정, timestamp 처리, anchor 보정은 수행하지 않는다.
입력 heading은 자북 기준 시계방향 **이동 방향**이다. 휴대폰의 바라보는 방향과 보행 방향이 다르면
이 모듈 호출 전에 별도의 자세 보정이 필요하다.

입력 각도 h를 `[0,360)`으로 정규화한 값을 θ, 이동거리를 d,
JSON의 `positive_x_heading`을 α, `positive_y_heading`을 β라고 할 때:

```text
θ = h mod 360                  (음수 나머지는 360을 더해 정규화)
dx = d × cos((θ − α) × π / 180)
dy = d × cos((θ − β) × π / 180)
```

자북 기준 동/북 성분은 `(d sin θ, d cos θ)`이고, 각 지도 축의 단위벡터는
`(sin α, cos α)`, `(sin β, cos β)`다. 내적이 위 cos 식이 된다.
직교축에서만 이 투영이 지도 좌표 성분을 주므로 축 직교 및 음의 축이 양의 축과
180° 반대인지 확인한다. 축 검증 허용 오차 `1e-9°`는 수치 연산용이며 센서 보정값이 아니다.
현재 지도에서 α=300°, β=210°이며 이 값은 코드 상수가 아니다.

| heading | 거리 | dx | dy |
| --- | --- | --- | --- |
| 300° | 1m | 1 | 0 |
| 120° | 1m | -1 | 0 |
| 210° | 1m | 0 | 1 |
| 30° | 1m | 0 | -1 |
| 255° | 1m | 약 0.707107 | 약 0.707107 |

거리 0이면 정확한 `(0.0, 0.0)`을 반환한다. 그 외 결과에는 일반적인 부동소수점 오차가 남을 수 있다.
음수 거리, NaN/무한대 거리·heading, 지원하지 않는 지도 단위/heading 기준, 모순된 축은
`IllegalArgumentException`으로 거부한다. heading은 음수나 여러 바퀴 각도도 허용하고 정규화한다.
변위는 원점과 무관하며 실측→복도 중심선 좌표 보정은 수행하지 않는다.

향후 PDR 호출 예시 (입력 값과 current는 실제 상위 모듈에서 전달):

```kotlin
import indoor.localization.coordinates.HeadingToMapConverter

val converter = HeadingToMapConverter(repository.floorMap.metadata) // 지도 로딩 후 재사용
val delta = converter.toDisplacement(headingDegrees, distanceMeters)
val nextX = current.x + delta.dx
val nextY = current.y + delta.dy
val normalizedHeading = HeadingToMapConverter.normalizeHeading(headingDegrees)
```

향후 PdrLocalizer는 이 좌표와 정규화 heading, 실제 관측 timestamp,
자체 계산 confidence를 이용해 source=PDR인 PositionEstimate를 생성하면 된다.
기존 nodeId를 이동 후에도 현재 노드라고 유지하지 않도록 주의하고, 층/좌표 기준은 현재 상태를 따른다.
이번 단계에는 해당 추적 로직이나 가짜 센서 생산자를 추가하지 않았다.
